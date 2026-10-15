package infra

import (
	"errors"
	"net/http"
	"strings"
	"time"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/credentials"
	"github.com/aws/aws-sdk-go-v2/service/s3"
)

type R2Config struct {
	AccountID       string
	AccessKeyID     string
	SecretAccessKey string
}

// NewR2Client configures an S3 client for Cloudflare R2.
func NewR2Client(config R2Config) (*s3.Client, error) {
	if config.AccountID == "" || config.AccessKeyID == "" || config.SecretAccessKey == "" {
		return nil, errors.New("r2: account ID and credentials are required")
	}
	if strings.ContainsAny(config.AccountID, "/\\.:?#@ \t\r\n") {
		return nil, errors.New("r2: invalid account ID")
	}
	return s3.NewFromConfig(aws.Config{
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
	}), nil
}
