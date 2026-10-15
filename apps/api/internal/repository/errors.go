package repository

import (
	"database/sql"
	"errors"
	"fmt"

	"github.com/go-jet/jet/v2/qrm"
	"github.com/lib/pq"
)

var (
	ErrNotFound = errors.New("record not found")
	ErrConflict = errors.New("record conflict")
)

func isUniqueViolation(err error) bool {
	var pqErr *pq.Error
	return errors.Is(err, ErrConflict) || errors.As(err, &pqErr) && pqErr.Code == "23505"
}

// requireAffectedRow converts an empty single-record update to a stable outcome.
func requireAffectedRow(result sql.Result, err error) error {
	if err != nil {
		return err
	}
	affected, err := result.RowsAffected()
	if err != nil {
		return err
	}
	if affected == 0 {
		return ErrNotFound
	}
	return nil
}

// storageError translates known storage outcomes without leaking driver errors.
// Unknown failures retain their cause for logging at the transport boundary.
func storageError(err error, operation string) error {
	if err == nil {
		return nil
	}
	switch {
	case errors.Is(err, qrm.ErrNoRows), errors.Is(err, sql.ErrNoRows):
		err = ErrNotFound
	case isUniqueViolation(err):
		err = ErrConflict
	}
	return fmt.Errorf("%s: %w", operation, err)
}
