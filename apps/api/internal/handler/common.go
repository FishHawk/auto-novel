package handler

import (
	"auto-novel/internal/httpx"
	"auto-novel/internal/usecase"
)

func actorFromPrincipal(principal httpx.Principal) usecase.Actor {
	return usecase.Actor{
		UserID:    principal.UserID,
		Username:  principal.Username,
		IsAdmin:   principal.IsAdmin(),
		CreatedAt: principal.CreatedAt,
	}
}
