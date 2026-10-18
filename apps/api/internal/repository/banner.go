package repository

import (
	"context"
	"database/sql"
	"time"

	"auto-novel/.gen/main/public/model"
	"auto-novel/.gen/main/public/table"
	"auto-novel/internal/domain"

	. "github.com/go-jet/jet/v2/postgres"
)

// BannerContent contains the editable fields shared by creation and full updates.
// A nil LinkURL means no link; it does not mean leaving the existing link unchanged.
type BannerContent struct {
	Type      domain.BannerType
	Key       string
	LinkURL   *string
	SortOrder int32
	FocalX    float64
	FocalY    float64
}

type BannerRepository interface {
	Get(ctx context.Context, id int64) (*domain.Banner, error)
	ListByType(ctx context.Context, bannerType domain.BannerType) ([]domain.Banner, error)
	ListActiveByType(ctx context.Context, bannerType domain.BannerType) ([]domain.Banner, error)
	Create(ctx context.Context, content BannerContent) (*domain.Banner, error)
	Update(ctx context.Context, id int64, content BannerContent) (*domain.Banner, error)
	SetActive(ctx context.Context, id int64, active bool) error
	Delete(ctx context.Context, id int64) error
}

type bannerRepository struct{ db *sql.DB }

func NewBannerRepository(db *sql.DB) BannerRepository { return &bannerRepository{db: db} }

func (r *bannerRepository) Get(ctx context.Context, id int64) (*domain.Banner, error) {
	stmt := SELECT(table.Banner.AllColumns).FROM(table.Banner).
		WHERE(table.Banner.ID.EQ(Int64(id)))
	var dest model.Banner
	if err := stmt.QueryContext(ctx, executor(ctx, r.db), &dest); err != nil {
		return nil, storageError(err, "banner.Get")
	}
	banner := bannerFromModel(dest)
	return &banner, nil
}

func (r *bannerRepository) ListByType(
	ctx context.Context,
	bannerType domain.BannerType,
) ([]domain.Banner, error) {
	return r.list(ctx, table.Banner.Type.EQ(Int16(int16(bannerType))), "banner.ListByType")
}

func (r *bannerRepository) ListActiveByType(
	ctx context.Context,
	bannerType domain.BannerType,
) ([]domain.Banner, error) {
	condition := table.Banner.Type.EQ(Int16(int16(bannerType))).
		AND(table.Banner.IsActive.IS_TRUE())
	return r.list(ctx, condition, "banner.ListActiveByType")
}

func (r *bannerRepository) list(
	ctx context.Context,
	condition BoolExpression,
	operation string,
) ([]domain.Banner, error) {
	stmt := SELECT(table.Banner.AllColumns).FROM(table.Banner).
		WHERE(condition).
		ORDER_BY(table.Banner.SortOrder.ASC(), table.Banner.ID.ASC())
	var dest []model.Banner
	if err := stmt.QueryContext(ctx, executor(ctx, r.db), &dest); err != nil {
		return nil, storageError(err, operation)
	}
	return bannersFromModels(dest), nil
}

// Create uses the database defaults for availability and timestamps.
func (r *bannerRepository) Create(
	ctx context.Context,
	content BannerContent,
) (*domain.Banner, error) {
	dest := model.Banner{
		Type:      int16(content.Type),
		Key:       content.Key,
		LinkURL:   content.LinkURL,
		FocalX:    content.FocalX,
		FocalY:    content.FocalY,
		SortOrder: content.SortOrder,
	}
	stmt := table.Banner.INSERT(
		table.Banner.Type,
		table.Banner.Key,
		table.Banner.LinkURL,
		table.Banner.FocalX,
		table.Banner.FocalY,
		table.Banner.SortOrder,
	).
		MODEL(dest).
		RETURNING(table.Banner.AllColumns)
	if err := stmt.QueryContext(ctx, executor(ctx, r.db), &dest); err != nil {
		return nil, storageError(err, "banner.Create")
	}
	banner := bannerFromModel(dest)
	return &banner, nil
}

// Update replaces all editable content; a nil content.LinkURL clears the link.
func (r *bannerRepository) Update(
	ctx context.Context,
	id int64,
	content BannerContent,
) (*domain.Banner, error) {
	dest := model.Banner{
		LinkURL:   content.LinkURL,
		FocalX:    content.FocalX,
		FocalY:    content.FocalY,
		SortOrder: content.SortOrder,
		UpdatedAt: time.Now(),
	}
	stmt := table.Banner.UPDATE(
		table.Banner.LinkURL,
		table.Banner.FocalX,
		table.Banner.FocalY,
		table.Banner.SortOrder,
		table.Banner.UpdatedAt,
	).
		MODEL(dest).
		WHERE(table.Banner.ID.EQ(Int64(id))).
		RETURNING(table.Banner.AllColumns)
	if err := stmt.QueryContext(ctx, executor(ctx, r.db), &dest); err != nil {
		return nil, storageError(err, "banner.Update")
	}
	banner := bannerFromModel(dest)
	return &banner, nil
}

func (r *bannerRepository) SetActive(ctx context.Context, id int64, active bool) error {
	stmt := table.Banner.UPDATE(table.Banner.IsActive, table.Banner.UpdatedAt).
		SET(Bool(active), TimestampzT(time.Now())).
		WHERE(table.Banner.ID.EQ(Int64(id)))
	err := requireAffectedRow(stmt.ExecContext(ctx, executor(ctx, r.db)))
	return storageError(err, "banner.SetActive")
}

// Delete removes only the database record; R2 cleanup belongs to the usecase.
func (r *bannerRepository) Delete(ctx context.Context, id int64) error {
	stmt := table.Banner.DELETE().WHERE(table.Banner.ID.EQ(Int64(id)))
	err := requireAffectedRow(stmt.ExecContext(ctx, executor(ctx, r.db)))
	return storageError(err, "banner.Delete")
}

func bannerFromModel(banner model.Banner) domain.Banner {
	return domain.Banner{
		ID:        banner.ID,
		Type:      domain.BannerType(banner.Type),
		Key:       banner.Key,
		LinkURL:   banner.LinkURL,
		FocalX:    banner.FocalX,
		FocalY:    banner.FocalY,
		SortOrder: banner.SortOrder,
		IsActive:  banner.IsActive,
		CreatedAt: banner.CreatedAt,
		UpdatedAt: banner.UpdatedAt,
	}
}

func bannersFromModels(banners []model.Banner) []domain.Banner {
	if banners == nil {
		return nil
	}
	dest := make([]domain.Banner, len(banners))
	for i, banner := range banners {
		dest[i] = bannerFromModel(banner)
	}
	return dest
}
