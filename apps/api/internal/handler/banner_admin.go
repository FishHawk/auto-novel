package handler

import (
	"net/http"
	"time"

	"auto-novel/internal/domain"
	"auto-novel/internal/httpx"
	"auto-novel/internal/usecase"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/render"
)

func (h *bannerHandler) RegisterAdminRoutes(router chi.Router) {
	router.Get("/", httpx.EH(h.listAdmin))
	router.Post("/", httpx.EH(h.create))
	router.Post("/upload", httpx.EH(h.presignUpload))
	router.Get("/{id}", httpx.EH(h.getAdmin))
	router.Put("/{id}", httpx.EH(h.update))
	router.Put("/{id}/active", httpx.EH(h.activate))
	router.Delete("/{id}/active", httpx.EH(h.deactivate))
	router.Delete("/{id}", httpx.EH(h.delete))
}

type bannerInput struct {
	FocalX    *float64          `json:"focalX" validate:"required,gte=0,lte=1" label:"焦点横坐标"`
	FocalY    *float64          `json:"focalY" validate:"required,gte=0,lte=1" label:"焦点纵坐标"`
	Type      domain.BannerType `json:"type"`
	Key       string            `json:"key" validate:"required" label:"图片对象 Key"`
	LinkURL   *string           `json:"linkUrl"`
	SortOrder int32             `json:"sortOrder"`
}

func (input bannerInput) toUsecaseInput() usecase.BannerInput {
	return usecase.BannerInput{
		Type:      input.Type,
		Key:       input.Key,
		LinkURL:   input.LinkURL,
		SortOrder: input.SortOrder,
		FocalX:    *input.FocalX,
		FocalY:    *input.FocalY,
	}
}

func (h *bannerHandler) listAdmin(w http.ResponseWriter, r *http.Request) error {
	principal, err := httpx.AuthenticatedPrincipal(r)
	if err != nil {
		return err
	}
	query, err := parseBannerQuery(r)
	if err != nil {
		return err
	}
	items, err := h.bannerUsecase.ListAdmin(
		r.Context(), actorFromPrincipal(principal), query,
	)
	if err != nil {
		return transportError(err)
	}
	render.JSON(w, r, newBannerResponses(items))
	return nil
}

func (h *bannerHandler) getAdmin(w http.ResponseWriter, r *http.Request) error {
	principal, err := httpx.AuthenticatedPrincipal(r)
	if err != nil {
		return err
	}
	id, err := httpx.ParseParamPositiveInt(r, "id", "Banner ID")
	if err != nil {
		return err
	}
	banner, err := h.bannerUsecase.GetAdmin(
		r.Context(), actorFromPrincipal(principal), id,
	)
	if err != nil {
		return transportError(err)
	}
	render.JSON(w, r, newBannerResponse(*banner))
	return nil
}

func (h *bannerHandler) create(w http.ResponseWriter, r *http.Request) error {
	principal, err := httpx.AuthenticatedPrincipal(r)
	if err != nil {
		return err
	}
	input, err := httpx.Body[bannerInput](r)
	if err != nil {
		return err
	}
	banner, err := h.bannerUsecase.Create(
		r.Context(), actorFromPrincipal(principal), input.toUsecaseInput(),
	)
	if err != nil {
		return transportError(err)
	}
	render.Status(r, http.StatusCreated)
	render.JSON(w, r, newBannerResponse(*banner))
	return nil
}

func (h *bannerHandler) update(w http.ResponseWriter, r *http.Request) error {
	principal, err := httpx.AuthenticatedPrincipal(r)
	if err != nil {
		return err
	}
	id, err := httpx.ParseParamPositiveInt(r, "id", "Banner ID")
	if err != nil {
		return err
	}
	input, err := httpx.Body[bannerInput](r)
	if err != nil {
		return err
	}
	banner, err := h.bannerUsecase.Update(
		r.Context(), actorFromPrincipal(principal), id, input.toUsecaseInput(),
	)
	if err != nil {
		return transportError(err)
	}
	render.JSON(w, r, newBannerResponse(*banner))
	return nil
}

func (h *bannerHandler) activate(w http.ResponseWriter, r *http.Request) error {
	return h.setActive(w, r, true)
}

func (h *bannerHandler) deactivate(w http.ResponseWriter, r *http.Request) error {
	return h.setActive(w, r, false)
}

func (h *bannerHandler) setActive(
	w http.ResponseWriter,
	r *http.Request,
	active bool,
) error {
	principal, err := httpx.AuthenticatedPrincipal(r)
	if err != nil {
		return err
	}
	id, err := httpx.ParseParamPositiveInt(r, "id", "Banner ID")
	if err != nil {
		return err
	}
	if err := h.bannerUsecase.SetActive(
		r.Context(),
		actorFromPrincipal(principal),
		usecase.SetBannerActiveCommand{ID: id, Active: active},
	); err != nil {
		return transportError(err)
	}
	w.WriteHeader(http.StatusNoContent)
	return nil
}

func (h *bannerHandler) delete(w http.ResponseWriter, r *http.Request) error {
	principal, err := httpx.AuthenticatedPrincipal(r)
	if err != nil {
		return err
	}
	id, err := httpx.ParseParamPositiveInt(r, "id", "Banner ID")
	if err != nil {
		return err
	}
	if err := h.bannerUsecase.Delete(
		r.Context(), actorFromPrincipal(principal), id,
	); err != nil {
		return transportError(err)
	}
	w.WriteHeader(http.StatusNoContent)
	return nil
}

type bannerUploadInput struct {
	ContentType string `json:"contentType" validate:"required" label:"图片类型"`
	Size        int64  `json:"size" validate:"gt=0" label:"图片字节数"`
}

type bannerUploadResponse struct {
	Key       string            `json:"key"`
	UploadURL string            `json:"uploadUrl"`
	ImageURL  string            `json:"imageUrl"`
	Headers   map[string]string `json:"headers"`
	ExpiresAt time.Time         `json:"expiresAt"`
}

func (h *bannerHandler) presignUpload(w http.ResponseWriter, r *http.Request) error {
	principal, err := httpx.AuthenticatedPrincipal(r)
	if err != nil {
		return err
	}
	input, err := httpx.Body[bannerUploadInput](r)
	if err != nil {
		return err
	}
	upload, err := h.bannerUsecase.PresignUpload(
		r.Context(),
		actorFromPrincipal(principal),
		usecase.BannerUploadInput{ContentType: input.ContentType, Size: input.Size},
	)
	if err != nil {
		return transportError(err)
	}
	w.Header().Set("Cache-Control", "no-store")
	render.JSON(w, r, bannerUploadResponse{
		Key:       upload.Key,
		UploadURL: upload.UploadURL,
		ImageURL:  upload.ImageURL,
		Headers:   upload.Headers,
		ExpiresAt: upload.ExpiresAt,
	})
	return nil
}
