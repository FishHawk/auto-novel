package infra

import (
	"context"
	"errors"
	"fmt"
	"net/http"
	"net/url"
	"os"
	"strings"
	"time"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/credentials"
	"github.com/aws/aws-sdk-go-v2/service/s3"
	"github.com/aws/smithy-go"
)

type R2Config struct {
	AccountID       string
	AccessKeyID     string
	SecretAccessKey string
	Bucket          string
	PublicBaseURL   string
}

func R2ConfigFromEnv() R2Config {
	return R2Config{
		AccountID:       os.Getenv("R2_ACCOUNT_ID"),
		AccessKeyID:     os.Getenv("R2_ACCESS_KEY_ID"),
		SecretAccessKey: os.Getenv("R2_SECRET_ACCESS_KEY"),
		Bucket:          os.Getenv("R2_BUCKET"),
		PublicBaseURL:   os.Getenv("R2_PUBLIC_BASE_URL"),
	}
}

type R2Storage struct {
	client    *s3.Client
	presigner *s3.PresignClient
	bucket    string
	publicURL *url.URL
}

var ErrObjectNotFound = errors.New("object not found")

type ObjectInfo struct {
	Size        int64
	ContentType string
}

type PresignedUpload struct {
	URL     string
	Headers map[string]string
}

func NewR2Storage(config R2Config) (*R2Storage, error) {
	if config.AccountID == "" || config.AccessKeyID == "" ||
		config.SecretAccessKey == "" || config.Bucket == "" {
		return nil, errors.New("r2: account ID, credentials and bucket are required")
	}
	if strings.ContainsAny(config.AccountID, "/\\.:?#@ \t\r\n") {
		return nil, errors.New("r2: invalid account ID")
	}
	publicURL, err := url.Parse(strings.TrimRight(config.PublicBaseURL, "/"))
	if err != nil || publicURL.Scheme != "https" || publicURL.Hostname() == "" ||
		publicURL.User != nil || publicURL.RawQuery != "" || publicURL.Fragment != "" {
		return nil, errors.New("r2: public base URL must be an absolute HTTPS URL")
	}

	client := s3.NewFromConfig(aws.Config{
		Region: "auto",
		Credentials: credentials.NewStaticCredentialsProvider(
			config.AccessKeyID,
			config.SecretAccessKey,
			"",
		),
		HTTPClient:                 &http.Client{Timeout: 30 * time.Second},
		RequestChecksumCalculation: aws.RequestChecksumCalculationWhenRequired,
		ResponseChecksumValidation: aws.ResponseChecksumValidationWhenRequired,
	}, func(options *s3.Options) {
		options.BaseEndpoint = aws.String(
			"https://" + config.AccountID + ".r2.cloudflarestorage.com",
		)
	})
	return &R2Storage{
		client:    client,
		presigner: s3.NewPresignClient(client),
		bucket:    config.Bucket,
		publicURL: publicURL,
	}, nil
}

func (s *R2Storage) PresignPut(
	ctx context.Context,
	key, contentType string,
	size int64,
	expires time.Duration,
) (*PresignedUpload, error) {
	if key == "" || contentType == "" || size <= 0 ||
		expires <= 0 || expires > 7*24*time.Hour {
		return nil, errors.New("r2.presign_put: invalid upload parameters")
	}
	request, err := s.presigner.PresignPutObject(ctx, &s3.PutObjectInput{
		Bucket:        aws.String(s.bucket),
		Key:           aws.String(key),
		ContentType:   aws.String(contentType),
		ContentLength: aws.Int64(size),
		IfNoneMatch:   aws.String("*"),
	}, func(options *s3.PresignOptions) {
		options.Expires = expires
	})
	if err != nil {
		return nil, fmt.Errorf("r2.presign_put: %w", err)
	}
	headers := map[string]string{"Content-Type": contentType}
	for name, values := range request.SignedHeader {
		if strings.EqualFold(name, "Host") || strings.EqualFold(name, "Content-Length") {
			continue
		}
		headers[name] = strings.Join(values, ",")
	}
	return &PresignedUpload{URL: request.URL, Headers: headers}, nil
}

func (s *R2Storage) Stat(ctx context.Context, key string) (ObjectInfo, error) {
	result, err := s.client.HeadObject(ctx, &s3.HeadObjectInput{
		Bucket: aws.String(s.bucket),
		Key:    aws.String(key),
	})
	if err != nil {
		var apiError smithy.APIError
		if errors.As(err, &apiError) {
			switch apiError.ErrorCode() {
			case "NotFound", "NoSuchKey", "404":
				err = ErrObjectNotFound
			}
		}
		return ObjectInfo{}, fmt.Errorf("r2.stat: %w", err)
	}
	return ObjectInfo{
		Size:        aws.ToInt64(result.ContentLength),
		ContentType: aws.ToString(result.ContentType),
	}, nil
}

func (s *R2Storage) Delete(ctx context.Context, key string) error {
	_, err := s.client.DeleteObject(ctx, &s3.DeleteObjectInput{
		Bucket: aws.String(s.bucket),
		Key:    aws.String(key),
	})
	if err != nil {
		return fmt.Errorf("r2.delete: %w", err)
	}
	return nil
}

func (s *R2Storage) PublicURL(key string) string {
	result := *s.publicURL
	result.Path = strings.TrimRight(result.Path, "/") + "/" + key
	result.RawPath = ""
	return result.String()
}
