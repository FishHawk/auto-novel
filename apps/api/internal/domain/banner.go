package domain

import "time"

// BannerType identifies the placement where a banner is displayed.
type BannerType int16

const BannerTypeHome BannerType = 0

// Banner describes a carousel image independently of its storage representation.
type Banner struct {
	ID        int64
	Type      BannerType
	Key       string
	LinkURL   *string
	SortOrder int32
	IsActive  bool
	CreatedAt time.Time
	UpdatedAt time.Time
}
