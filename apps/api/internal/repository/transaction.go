package repository

import (
	"context"
	"database/sql"
	"errors"

	"github.com/go-jet/jet/v2/qrm"
)

// TransactionRunner executes a usecase-defined unit of work atomically.
// Every repository call in the callback must receive the callback's context.
// Returning an error or panicking rolls back; success is returned only after
// commit succeeds. Nested transactions are not supported.
type TransactionRunner interface {
	WithinTransaction(ctx context.Context, fn func(context.Context) error) error
}

// transactionKey ties the context's transaction to its owning database. A
// repository backed by another database must not accidentally use this tx.
type transactionKey struct{ db *sql.DB }

type transactionRunner struct{ db *sql.DB }

func NewTransactionRunner(db *sql.DB) TransactionRunner {
	return &transactionRunner{db: db}
}

// WithinTransaction implements the usecase transaction boundary without
// exposing database/sql or Jet types to the application layer. The callback
// context is only valid during the callback and must not escape to goroutines.
func (m *transactionRunner) WithinTransaction(ctx context.Context, fn func(context.Context) error) error {
	if ctx.Value(transactionKey{m.db}) != nil {
		return errors.New("transaction: nested transactions are not supported")
	}
	tx, err := m.db.BeginTx(ctx, nil)
	if err != nil {
		return storageError(err, "transaction.Begin")
	}
	defer tx.Rollback()

	txCtx := context.WithValue(ctx, transactionKey{m.db}, tx)
	if err := fn(txCtx); err != nil {
		return err
	}
	return storageError(tx.Commit(), "transaction.Commit")
}

// executor lets every repository operation participate in the usecase's tx,
// while standalone reads and single-statement writes can still use the DB.
func executor(ctx context.Context, db *sql.DB) qrm.DB {
	if tx, ok := ctx.Value(transactionKey{db}).(*sql.Tx); ok {
		return tx
	}
	return db
}

// Multi-statement writes must never silently run without atomicity.
func requireTransaction(ctx context.Context, db *sql.DB) (*sql.Tx, error) {
	if err := ctx.Err(); err != nil {
		return nil, err
	}
	if tx, ok := ctx.Value(transactionKey{db}).(*sql.Tx); ok {
		return tx, nil
	}
	return nil, errors.New("repository write requires a usecase transaction")
}
