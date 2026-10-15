package usecase

import (
	"context"
	"errors"
	"fmt"
	"net/url"
	"strings"

	"auto-novel/internal/domain"
	"auto-novel/internal/repository"
)

const (
	CodeBannerIDInvalid      = "banner.id_invalid"
	CodeBannerTypeInvalid    = "banner.type_invalid"
	CodeBannerKeyInvalid     = "banner.key_invalid"
	CodeBannerLinkURLInvalid = "banner.link_url_invalid"
	CodeBannerNotFound       = "banner.not_found"
	CodeBannerConflict       = "banner.conflict"
	CodeBannerAdminRequired  = "banner.admin_required"
)

type BannerUsecase struct{ bannerRepo repository.BannerRepository }

func NewBannerUsecase(bannerRepo repository.BannerRepository) *BannerUsecase {
	return &BannerUsecase{bannerRepo: bannerRepo}
}

type ListBannersQuery struct{ Type domain.BannerType }

// A nil or blank LinkURL clears the existing link.
type BannerInput struct {
	Type      domain.BannerType
	Key       string
	LinkURL   *string
	SortOrder int32
}

type SetBannerActiveCommand struct {
	ID     int64
	Active bool
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
	input.Key = strings.TrimSpace(input.Key)
	if input.Key == "" || strings.ContainsAny(input.Key, "\r\n\x00") {
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
) ([]domain.Banner, error) {
	if err := checkBannerType(query.Type); err != nil {
		return nil, err
	}
	banners, err := u.bannerRepo.ListActiveByType(ctx, query.Type)
	if err != nil {
		return nil, fmt.Errorf("banner.list_active: %w", err)
	}
	return banners, nil
}

func (u *BannerUsecase) ListAdmin(
	ctx context.Context,
	actor Actor,
	query ListBannersQuery,
) ([]domain.Banner, error) {
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
	return banners, nil
}

func (u *BannerUsecase) GetAdmin(
	ctx context.Context,
	actor Actor,
	id int64,
) (*domain.Banner, error) {
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
	return banner, nil
}

func (u *BannerUsecase) Create(
	ctx context.Context,
	actor Actor,
	input BannerInput,
) (*domain.Banner, error) {
	if err := checkBannerAdmin(actor); err != nil {
		return nil, err
	}
	content, err := normalizeBannerInput(input)
	if err != nil {
		return nil, err
	}
	banner, err := u.bannerRepo.Create(ctx, content)
	if err != nil {
		return nil, bannerError(err, "create")
	}
	return banner, nil
}

func (u *BannerUsecase) Update(
	ctx context.Context,
	actor Actor,
	id int64,
	input BannerInput,
) (*domain.Banner, error) {
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
	banner, err := u.bannerRepo.Update(ctx, id, content)
	if err != nil {
		return nil, bannerError(err, "update")
	}
	return banner, nil
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

// Delete removes the database record only, not the R2 object.
func (u *BannerUsecase) Delete(ctx context.Context, actor Actor, id int64) error {
	if err := checkBannerAdmin(actor); err != nil {
		return err
	}
	if err := checkBannerID(id); err != nil {
		return err
	}
	return bannerError(u.bannerRepo.Delete(ctx, id), "delete")
}
