package infra

import (
	"database/sql"
	"net"
	"net/url"
	"strconv"

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
	return sql.Open("postgres", dsn.String())
}
