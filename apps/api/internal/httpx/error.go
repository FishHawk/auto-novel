package httpx

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"net/http"

	"github.com/go-chi/render"
)

type HttpError struct {
	StatusCode int
	Message    string
	Cause      error
}

func (e *HttpError) Error() string {
	if e.Cause != nil {
		return fmt.Sprintf("[%d] %s: %v", e.StatusCode, e.Message, e.Cause)
	}
	return fmt.Sprintf("[%d] %s", e.StatusCode, e.Message)
}

func (e *HttpError) Unwrap() error {
	return e.Cause
}

func NewHttpError(statusCode int, message string) *HttpError {
	return &HttpError{
		StatusCode: statusCode,
		Message:    message,
	}
}

func BadRequest(message string) *HttpError {
	return NewHttpError(http.StatusBadRequest, message)
}

func Unauthorized(message string) *HttpError {
	return NewHttpError(http.StatusUnauthorized, message)
}

func Forbidden(message string) *HttpError {
	return NewHttpError(http.StatusForbidden, message)
}

func NotFound(message string) *HttpError {
	return NewHttpError(http.StatusNotFound, message)
}

func Conflict(message string) *HttpError {
	return NewHttpError(http.StatusConflict, message)
}

func InternalServerError(message string) *HttpError {
	return NewHttpError(http.StatusInternalServerError, message)
}

func InternalError(cause error, message string) *HttpError {
	return &HttpError{
		StatusCode: http.StatusInternalServerError,
		Message:    message,
		Cause:      cause,
	}
}

func EH(f func(http.ResponseWriter, *http.Request) error) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		err := f(w, r)
		if err != nil {
			respondError(w, r, err)
			return
		}
	}
}

func respondError(w http.ResponseWriter, r *http.Request, err error) {
	var code int
	var message string

	// Drivers may return their own cancellation error rather than ctx.Err().
	// Prefer the request context, and also recognize wrapped context errors.
	if errors.Is(r.Context().Err(), context.Canceled) || errors.Is(err, context.Canceled) {
		// 499 is the conventional client-closed-request status for access logs.
		// Do not attempt to send an error body to a disconnected client.
		slog.DebugContext(r.Context(), "Request canceled", "status", 499)
		w.Header().Del("Content-Length")
		w.WriteHeader(499)
		return
	}
	var httpErr *HttpError
	if errors.Is(r.Context().Err(), context.DeadlineExceeded) || errors.Is(err, context.DeadlineExceeded) {
		code = http.StatusGatewayTimeout
		message = "请求超时"
		slog.WarnContext(r.Context(), "Request timed out", "status", code)
	} else if errors.As(err, &httpErr) {
		code = httpErr.StatusCode
		message = httpErr.Message
		if code >= http.StatusInternalServerError {
			slog.Error("Request failed", "status", code, "error", err)
		}
	} else {
		code = http.StatusInternalServerError
		message = "服务器内部错误"
		slog.Error("Unhandled request error", "status", code, "error", err)
	}

	header := w.Header()
	header.Del("Content-Length")
	header.Set("X-Content-Type-Options", "nosniff")
	render.Status(r, code)
	render.PlainText(w, r, message)
}
