package httpx

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"mime"
	"net/http"
	"net/url"
	"reflect"
	"strconv"
	"strings"
	"time"

	"github.com/go-chi/chi/v5"
	localeZh "github.com/go-playground/locales/zh"
	ut "github.com/go-playground/universal-translator"
	"github.com/go-playground/validator/v10"
	translationsZh "github.com/go-playground/validator/v10/translations/zh"
)

var (
	bodyValidator  = validator.New(validator.WithRequiredStructEnabled())
	bodyTranslator ut.Translator
)

func init() {
	locale := localeZh.New()
	universalTranslator := ut.New(locale, locale)
	var found bool
	bodyTranslator, found = universalTranslator.GetTranslator("zh")
	if !found {
		panic("Chinese validator translator is unavailable")
	}

	bodyValidator.RegisterTagNameFunc(func(field reflect.StructField) string {
		if label := field.Tag.Get("label"); label != "" {
			return label
		}
		name := strings.SplitN(field.Tag.Get("json"), ",", 2)[0]
		if name == "-" {
			return ""
		}
		if name != "" {
			return name
		}
		return field.Name
	})
	if err := translationsZh.RegisterDefaultTranslations(bodyValidator, bodyTranslator); err != nil {
		panic(err)
	}
}

func Body[T any](r *http.Request) (T, error) {
	var zero T

	contentType := r.Header.Get("Content-Type")
	mediaType, _, err := mime.ParseMediaType(contentType)
	if err != nil || mediaType != "application/json" {
		return zero, &HttpError{
			StatusCode: http.StatusUnsupportedMediaType,
			Message:    "请求格式无效",
		}
	}

	// 限制读取的最大字节数为1MB
	const maxBytesDefault = 1 << 20
	defer r.Body.Close()
	body, err := io.ReadAll(io.LimitReader(r.Body, maxBytesDefault+1))
	if err != nil {
		return zero, BadRequest("请求格式无效")
	}
	if len(body) > maxBytesDefault {
		return zero, &HttpError{
			StatusCode: http.StatusRequestEntityTooLarge,
			Message:    "请求体大小不能超过 1 MB",
		}
	}

	// 解码JSON
	var result T
	decoder := json.NewDecoder(bytes.NewReader(body))
	if err := decoder.Decode(&result); err != nil {
		return zero, BadRequest("请求格式无效")
	}
	// 请求体只能包含一个 JSON 值，允许后面有空白字符。
	if err := decoder.Decode(&struct{}{}); err != io.EOF {
		return zero, BadRequest("请求格式无效")
	}

	// 验证JSON
	if err := bodyValidator.Struct(result); err != nil {
		var validationErrors validator.ValidationErrors
		if !errors.As(err, &validationErrors) {
			return zero, InternalError(err, "服务器内部错误")
		}
		messages := make([]string, len(validationErrors))
		for i, ve := range validationErrors {
			messages[i] = ve.Translate(bodyTranslator)
		}
		return zero, BadRequest(strings.Join(messages, "; "))
	}

	return result, nil
}

func ParseQueryPositiveInt(query url.Values, key string, defaultValue int64, label string) (int64, error) {
	value := query.Get(key)
	if value == "" {
		return defaultValue, nil
	}
	parsed, err := strconv.ParseInt(value, 10, 64)
	if err != nil || parsed <= 0 {
		return 0, BadRequest(fmt.Sprintf("%s 必须为正整数", label))
	}
	return parsed, nil
}

func ParseQueryUnixTime(query url.Values, key string, label string) (time.Time, error) {
	value := query.Get(key)
	if value == "" {
		return time.Time{}, nil
	}
	epochSeconds, err := strconv.ParseInt(value, 10, 64)
	if err != nil {
		return time.Time{}, BadRequest(fmt.Sprintf("%s 必须为 Unix 时间戳", label))
	}
	return time.Unix(epochSeconds, 0), nil
}

func ParseParamPositiveInt(r *http.Request, key string, label string) (int64, error) {
	value, err := strconv.ParseInt(chi.URLParam(r, key), 10, 64)
	if err != nil || value <= 0 {
		return 0, BadRequest(fmt.Sprintf("%s 必须为正整数", label))
	}
	return value, nil
}
