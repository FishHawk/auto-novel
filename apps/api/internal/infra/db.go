package infra

import (
	"database/sql"
	"net"
	"net/url"
	"strconv"
	"time"

	_ "github.com/lib/pq"
)

func NewSQLDB(host string, port int, user, password, dbname string) (*sql.DB, error) {
	dsn := url.URL{
		Scheme: "postgres",
		Host:   net.JoinHostPort(host, strconv.Itoa(port)),
		User:   url.UserPassword(user, password),
		Path:   "/" + dbname,
	}
	query := dsn.Query()
	query.Set("sslmode", "disable")
	dsn.RawQuery = query.Encode()
	db, err := sql.Open("postgres", dsn.String())
	if err != nil {
		return nil, err
	}
	db.SetMaxOpenConns(20)
	db.SetMaxIdleConns(5)
	db.SetConnMaxIdleTime(5 * time.Minute)
	db.SetConnMaxLifetime(30 * time.Minute)
	return db, nil
}
