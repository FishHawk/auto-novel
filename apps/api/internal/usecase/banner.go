package usecase

import (
	"context"
	"crypto/rand"
	"errors"
	"fmt"
	"log/slog"
	"math"
	"net/url"
	"regexp"
	"strings"
	"time"

	"auto-novel/internal/domain"
	"auto-novel/internal/repository"
)

const (
	CodeBannerFocalPointInvalid = "banner.focal_point_invalid"
	CodeBannerIDInvalid         = "banner.id_invalid"
	CodeBannerTypeInvalid       = "banner.type_invalid"
	CodeBannerKeyInvalid        = "banner.key_invalid"
	CodeBannerLinkURLInvalid    = "banner.link_url_invalid"
	CodeBannerNotFound          = "banner.not_found"
	CodeBannerConflict          = "banner.conflict"
	CodeBannerAdminRequired     = "banner.admin_required"
	CodeBannerImageInvalid      = "banner.image_invalid"
	CodeBannerImageNotFound     = "banner.image_not_found"
)

const (
	BannerMaxImageSize       = 5 * 1024 * 1024
	bannerUploadTTL          = 10 * time.Minute
	bannerImageDeleteTimeout = 30 * time.Second
	// Banner image keys are never reused, so browsers and CDNs can cache them for a year.
	bannerImageCacheControl = "public, max-age=31536000, immutable"
)

var bannerObjectKey = regexp.MustCompile(`^banners/[A-Za-z0-9_-]+\.(jpg|png|webp|gif|avif)$`)

type BannerUsecase struct {
	bannerRepo repository.BannerRepository
	objectRepo repository.ObjectRepository
}

func NewBannerUsecase(
	bannerRepo repository.BannerRepository,
	objectRepo repository.ObjectRepository,
) *BannerUsecase {
	if objectRepo == nil {
		panic("banner usecase requires object repository")
	}
	return &BannerUsecase{bannerRepo: bannerRepo, objectRepo: objectRepo}
}

type BannerResult struct {
	domain.Banner
	ImageURL string
}

type BannerUploadInput struct {
	ContentType string
	Size        int64
}

type BannerUploadResult struct {
	Key       string
	UploadURL string
	ImageURL  string
	Headers   map[string]string
	ExpiresAt time.Time
}

type ListBannersQuery struct{ Type domain.BannerType }

// Image keys are exclusive to one banner and must not be reused after replacement
// or deletion. Reusing an image requires uploading it under a new key.
// A nil or blank LinkURL clears the existing link.
type BannerInput struct {
	Type      domain.BannerType
	Key       string
	LinkURL   *string
	SortOrder int32
	FocalX    float64
	FocalY    float64
}

type SetBannerActiveCommand struct {
	ID     int64
	Active bool
}

func (u *BannerUsecase) PresignUpload(
	ctx context.Context,
	actor Actor,
	input BannerUploadInput,
) (*BannerUploadResult, error) {
	if err := checkBannerAdmin(actor); err != nil {
		return nil, err
	}
	extension := bannerImageExtension(input.ContentType)
	if extension == "" || input.Size <= 0 || input.Size > BannerMaxImageSize {
		return nil, Invalid(
			CodeBannerImageInvalid,
			"图片必须为 JPEG、PNG、WebP、GIF 或 AVIF，且不超过 5 MiB",
		)
	}
	key := "banners/" + rand.Text() + extension
	expiresAt := time.Now().Add(bannerUploadTTL)
	upload, err := u.objectRepo.PresignPut(
		ctx, key, input.ContentType, bannerImageCacheControl, input.Size, bannerUploadTTL,
	)
	if err != nil {
		return nil, fmt.Errorf("banner.presign_upload: %w", err)
	}
	return &BannerUploadResult{
		Key:       key,
		UploadURL: upload.URL,
		ImageURL:  u.objectRepo.PublicURL(key),
		Headers:   upload.Headers,
		ExpiresAt: expiresAt,
	}, nil
}

func bannerImageExtension(contentType string) string {
	switch contentType {
	case "image/jpeg":
		return ".jpg"
	case "image/png":
		return ".png"
	case "image/webp":
		return ".webp"
	case "image/gif":
		return ".gif"
	case "image/avif":
		return ".avif"
	default:
		return ""
	}
}

func (u *BannerUsecase) checkImage(ctx context.Context, key string) error {
	info, err := u.objectRepo.Stat(ctx, key)
	if errors.Is(err, repository.ErrObjectNotFound) {
		return NotFound(CodeBannerImageNotFound, "图片尚未上传或已被删除")
	}
	if err != nil {
		return fmt.Errorf("banner.check_image: %w", err)
	}
	extension := bannerImageExtension(info.ContentType)
	if info.Size <= 0 || info.Size > BannerMaxImageSize ||
		extension == "" || !strings.HasSuffix(key, extension) {
		return Invalid(CodeBannerImageInvalid, "已上传图片的格式或大小无效")
	}
	return nil
}

// deleteImageAsync does not delay the response or depend on request cancellation.
// Cleanup is deliberately best-effort: failures are logged, not retried.
func (u *BannerUsecase) deleteImageAsync(ctx context.Context, key string) {
	go func() {
		ctx, cancel := context.WithTimeout(context.WithoutCancel(ctx), bannerImageDeleteTimeout)
		defer cancel()
		if err := u.objectRepo.Delete(ctx, key); err != nil {
			slog.WarnContext(ctx, "Delete unused banner image failed", "key", key, "error", err)
		}
	}()
}

func (u *BannerUsecase) result(banner domain.Banner) BannerResult {
	return BannerResult{Banner: banner, ImageURL: u.objectRepo.PublicURL(banner.Key)}
}

func (u *BannerUsecase) results(banners []domain.Banner) []BannerResult {
	results := make([]BannerResult, len(banners))
	for i, banner := range banners {
		results[i] = u.result(banner)
	}
	return results
}

func checkBannerAdmin(actor Actor) error {
	if !actor.IsAdmin {
		return PermissionDenied(CodeBannerAdminRequired, "需要管理员权限")
	}
	return nil
}

func checkBannerID(id int64) error {
	if id <= 0 {
		return Invalid(CodeBannerIDInvalid, "Banner ID 必须为正整数")
	}
	return nil
}

func checkBannerType(bannerType domain.BannerType) error {
	if bannerType != domain.BannerTypeHome {
		return Invalid(CodeBannerTypeInvalid, "Banner 位置无效")
	}
	return nil
}

func normalizeBannerInput(input BannerInput) (repository.BannerContent, error) {
	if err := checkBannerType(input.Type); err != nil {
		return repository.BannerContent{}, err
	}
	if math.IsNaN(input.FocalX) || math.IsNaN(input.FocalY) ||
		input.FocalX < 0 || input.FocalX > 1 || input.FocalY < 0 || input.FocalY > 1 {
		return repository.BannerContent{}, Invalid(
			CodeBannerFocalPointInvalid, "焦点坐标必须为 0–1 之间的有限数值",
		)
	}
	input.Key = strings.TrimSpace(input.Key)
	if !bannerObjectKey.MatchString(input.Key) {
		return repository.BannerContent{}, Invalid(CodeBannerKeyInvalid, "图片对象 Key 无效")
	}
	var linkURL *string
	if input.LinkURL != nil {
		link := strings.TrimSpace(*input.LinkURL)
		if link != "" {
			if !validBannerLink(link) {
				return repository.BannerContent{}, Invalid(
					CodeBannerLinkURLInvalid,
					"跳转地址必须为站内路径或 HTTP(S) 地址",
				)
			}
			linkURL = &link
		}
	}
	return repository.BannerContent{
		Type:      input.Type,
		Key:       input.Key,
		LinkURL:   linkURL,
		SortOrder: input.SortOrder,
		FocalX:    input.FocalX,
		FocalY:    input.FocalY,
	}, nil
}

func validBannerLink(link string) bool {
	if strings.ContainsAny(link, "\r\n\x00\\") {
		return false
	}
	parsed, err := url.Parse(link)
	if err != nil {
		return false
	}

	if parsed.Scheme == "" {
		return parsed.Host == "" &&
			strings.HasPrefix(link, "/") &&
			!strings.HasPrefix(link, "//")
	}

	isHTTP := parsed.Scheme == "http" || parsed.Scheme == "https"
	return isHTTP && parsed.Hostname() != "" && parsed.User == nil
}

func bannerError(err error, operation string) error {
	if errors.Is(err, repository.ErrNotFound) {
		return NotFound(CodeBannerNotFound, "Banner 不存在")
	}
	if errors.Is(err, repository.ErrConflict) {
		return Conflict(CodeBannerConflict, "Banner 已存在")
	}
	if err != nil {
		return fmt.Errorf("banner.%s: %w", operation, err)
	}
	return nil
}

func (u *BannerUsecase) ListActive(
	ctx context.Context,
	query ListBannersQuery,
) ([]BannerResult, error) {
	if err := checkBannerType(query.Type); err != nil {
		return nil, err
	}
	banners, err := u.bannerRepo.ListActiveByType(ctx, query.Type)
	if err != nil {
		return nil, fmt.Errorf("banner.list_active: %w", err)
	}
	return u.results(banners), nil
}

func (u *BannerUsecase) ListAdmin(
	ctx context.Context,
	actor Actor,
	query ListBannersQuery,
) ([]BannerResult, error) {
	if err := checkBannerAdmin(actor); err != nil {
		return nil, err
	}
	if err := checkBannerType(query.Type); err != nil {
		return nil, err
	}
	banners, err := u.bannerRepo.ListByType(ctx, query.Type)
	if err != nil {
		return nil, fmt.Errorf("banner.list: %w", err)
	}
	return u.results(banners), nil
}

func (u *BannerUsecase) GetAdmin(
	ctx context.Context,
	actor Actor,
	id int64,
) (*BannerResult, error) {
	if err := checkBannerAdmin(actor); err != nil {
		return nil, err
	}
	if err := checkBannerID(id); err != nil {
		return nil, err
	}
	banner, err := u.bannerRepo.Get(ctx, id)
	if err != nil {
		return nil, bannerError(err, "get")
	}
	result := u.result(*banner)
	return &result, nil
}

func (u *BannerUsecase) Create(
	ctx context.Context,
	actor Actor,
	input BannerInput,
) (*BannerResult, error) {
	if err := checkBannerAdmin(actor); err != nil {
		return nil, err
	}
	content, err := normalizeBannerInput(input)
	if err != nil {
		return nil, err
	}
	if err := u.checkImage(ctx, content.Key); err != nil {
		return nil, err
	}
	banner, err := u.bannerRepo.Create(ctx, content)
	if err != nil {
		return nil, bannerError(err, "create")
	}
	result := u.result(*banner)
	return &result, nil
}

func (u *BannerUsecase) Update(
	ctx context.Context,
	actor Actor,
	id int64,
	input BannerInput,
) (*BannerResult, error) {
	if err := checkBannerAdmin(actor); err != nil {
		return nil, err
	}
	if err := checkBannerID(id); err != nil {
		return nil, err
	}
	content, err := normalizeBannerInput(input)
	if err != nil {
		return nil, err
	}
	if err := u.checkImage(ctx, content.Key); err != nil {
		return nil, err
	}
	previous, err := u.bannerRepo.Get(ctx, id)
	if err != nil {
		return nil, bannerError(err, "update")
	}
	banner, err := u.bannerRepo.Update(ctx, id, content)
	if err != nil {
		return nil, bannerError(err, "update")
	}
	if previous.Key != banner.Key {
		u.deleteImageAsync(ctx, previous.Key)
	}
	result := u.result(*banner)
	return &result, nil
}

func (u *BannerUsecase) SetActive(
	ctx context.Context,
	actor Actor,
	command SetBannerActiveCommand,
) error {
	if err := checkBannerAdmin(actor); err != nil {
		return err
	}
	if err := checkBannerID(command.ID); err != nil {
		return err
	}
	err := u.bannerRepo.SetActive(ctx, command.ID, command.Active)
	return bannerError(err, "set_active")
}

// Delete removes the database record, then asynchronously tries to delete its image.
func (u *BannerUsecase) Delete(ctx context.Context, actor Actor, id int64) error {
	if err := checkBannerAdmin(actor); err != nil {
		return err
	}
	if err := checkBannerID(id); err != nil {
		return err
	}
	banner, err := u.bannerRepo.Get(ctx, id)
	if err != nil {
		return bannerError(err, "delete")
	}
	if err := u.bannerRepo.Delete(ctx, id); err != nil {
		return bannerError(err, "delete")
	}
	u.deleteImageAsync(ctx, banner.Key)
	return nil
}
