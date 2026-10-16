package handler

import (
	"errors"
	"net/http"

	"auto-novel/internal/httpx"
	"auto-novel/internal/usecase"
)

func transportError(err error) error {
	var httpErr *httpx.HttpError
	if errors.As(err, &httpErr) {
		return err
	}
	var appErr *usecase.AppError
	if !errors.As(err, &appErr) {
		return err
	}
	var status int
	switch appErr.Kind {
	case usecase.KindInvalid:
		status = http.StatusBadRequest
	case usecase.KindNotFound:
		status = http.StatusNotFound
	case usecase.KindConflict:
		status = http.StatusConflict
	case usecase.KindPermissionDenied:
		status = http.StatusForbidden
	default:
		return err
	}
	return &httpx.HttpError{StatusCode: status, Message: appErr.Message, Cause: err}
}
