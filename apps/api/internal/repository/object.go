package repository

import (
	"context"
	"errors"
	"fmt"
	"net/url"
	"strings"
	"time"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/service/s3"
	"github.com/aws/smithy-go"
)

var ErrObjectNotFound = errors.New("object not found")

type ObjectInfo struct {
	Size        int64
	ContentType string
}

type PresignedUpload struct {
	URL     string
	Headers map[string]string
}

// ObjectRepository provides object metadata, create-only uploads, deletion and public URLs.
// Its contract does not expose S3 SDK types to usecases.
type ObjectRepository interface {
	PresignPut(ctx context.Context, key, contentType, cacheControl string, size int64, expires time.Duration) (*PresignedUpload, error)
	Stat(ctx context.Context, key string) (ObjectInfo, error)
	Delete(ctx context.Context, key string) error
	PublicURL(key string) string
}

type s3ObjectRepository struct {
	client    *s3.Client
	presigner *s3.PresignClient
	bucket    string
	publicURL *url.URL
}

func NewObjectRepository(client *s3.Client, bucket, publicBaseURL string) (ObjectRepository, error) {
	if client == nil || bucket == "" {
		return nil, errors.New("object: S3 client and bucket are required")
	}
	publicURL, err := url.Parse(strings.TrimRight(publicBaseURL, "/"))
	if err != nil || publicURL.Scheme != "https" || publicURL.Hostname() == "" ||
		publicURL.User != nil || publicURL.RawQuery != "" || publicURL.Fragment != "" {
		return nil, errors.New("object: public base URL must be an absolute HTTPS URL")
	}
	return &s3ObjectRepository{
		client:    client,
		presigner: s3.NewPresignClient(client),
		bucket:    bucket,
		publicURL: publicURL,
	}, nil
}

func (r *s3ObjectRepository) PresignPut(
	ctx context.Context,
	key, contentType, cacheControl string,
	size int64,
	expires time.Duration,
) (*PresignedUpload, error) {
	if key == "" || contentType == "" || size <= 0 ||
		expires <= 0 || expires > 7*24*time.Hour {
		return nil, errors.New("object.presign_put: invalid upload parameters")
	}
	request, err := r.presigner.PresignPutObject(ctx, &s3.PutObjectInput{
		Bucket:        aws.String(r.bucket),
		Key:           aws.String(key),
		ContentType:   aws.String(contentType),
		CacheControl:  aws.String(cacheControl),
		ContentLength: aws.Int64(size),
		IfNoneMatch:   aws.String("*"),
	}, func(options *s3.PresignOptions) {
		options.Expires = expires
	})
	if err != nil {
		return nil, fmt.Errorf("object.presign_put: %w", err)
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

func (r *s3ObjectRepository) Stat(ctx context.Context, key string) (ObjectInfo, error) {
	result, err := r.client.HeadObject(ctx, &s3.HeadObjectInput{
		Bucket: aws.String(r.bucket),
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
		return ObjectInfo{}, fmt.Errorf("object.stat: %w", err)
	}
	return ObjectInfo{
		Size:        aws.ToInt64(result.ContentLength),
		ContentType: aws.ToString(result.ContentType),
	}, nil
}

// Delete makes a single best-effort attempt; missing objects are already deleted.
func (r *s3ObjectRepository) Delete(ctx context.Context, key string) error {
	_, err := r.client.DeleteObject(ctx, &s3.DeleteObjectInput{
		Bucket: aws.String(r.bucket),
		Key:    aws.String(key),
	}, func(options *s3.Options) {
		options.RetryMaxAttempts = 1
	})
	if err != nil {
		return fmt.Errorf("object.delete: %w", err)
	}
	return nil
}

func (r *s3ObjectRepository) PublicURL(key string) string {
	result := *r.publicURL
	result.Path = strings.TrimRight(result.Path, "/") + "/" + key
	result.RawPath = ""
	return result.String()
}
