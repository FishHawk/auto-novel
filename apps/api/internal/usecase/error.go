package usecase

// ErrorKind groups business failures for protocol adapters.
type ErrorKind string

const (
	KindInvalid          ErrorKind = "invalid"
	KindNotFound         ErrorKind = "not_found"
	KindConflict         ErrorKind = "conflict"
	KindPermissionDenied ErrorKind = "permission_denied"
)

// AppError describes an expected application failure. Unknown infrastructure
// errors remain wrapped errors for logging at the transport boundary.
type AppError struct {
	Kind    ErrorKind
	Code    string
	Message string
	Cause   error
}

func Invalid(code, message string) *AppError {
	return &AppError{Kind: KindInvalid, Code: code, Message: message}
}

func NotFound(code, message string) *AppError {
	return &AppError{Kind: KindNotFound, Code: code, Message: message}
}

func Conflict(code, message string) *AppError {
	return &AppError{Kind: KindConflict, Code: code, Message: message}
}

func PermissionDenied(code, message string) *AppError {
	return &AppError{Kind: KindPermissionDenied, Code: code, Message: message}
}

func (e *AppError) Error() string { return e.Message }
func (e *AppError) Unwrap() error { return e.Cause }
