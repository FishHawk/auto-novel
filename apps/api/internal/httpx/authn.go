package httpx

import (
	"context"
	"net/http"
	"strings"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

const (
	bearerChallenge             string = "Bearer"
	invalidAccessTokenChallenge string = `Bearer error="invalid_token"`
	roleAdmin                   string = "admin"
	roleTrusted                 string = "trusted"
	roleMember                  string = "member"
	roleRestricted              string = "restricted"
	roleBanned                  string = "banned"
)

var AccessTokenSecret string

type accessClaim struct {
	jwt.RegisteredClaims
	UserID    int64            `json:"uid"`
	Role      string           `json:"role"`
	CreatedAt *jwt.NumericDate `json:"crat"`
}

type Principal struct {
	UserID    int64
	Username  string
	Role      string
	CreatedAt time.Time
}

func (p Principal) IsAdmin() bool {
	return p.Role == roleAdmin
}

type principalContextKey struct{}

func verifyAccessToken(tokenString string) (Principal, error) {
	claims := &accessClaim{}
	token, err := jwt.ParseWithClaims(tokenString, claims,
		func(token *jwt.Token) (interface{}, error) {
			return []byte(AccessTokenSecret), nil
		},
	)
	if err != nil {
		return Principal{}, err
	}
	if !token.Valid {
		return Principal{}, jwt.ErrTokenInvalidClaims
	}

	validClaims, ok := token.Claims.(*accessClaim)
	if !ok || validClaims.Subject == "" || validClaims.CreatedAt == nil {
		return Principal{}, jwt.ErrTokenInvalidClaims
	}

	principal := Principal{
		UserID:    validClaims.UserID,
		Username:  validClaims.Subject,
		Role:      validClaims.Role,
		CreatedAt: validClaims.CreatedAt.Time,
	}
	return principal, nil
}

func RequireAccessToken(next http.Handler) http.Handler {
	return EH(func(w http.ResponseWriter, r *http.Request) error {
		authorization := r.Header.Get("Authorization")
		tokenString, ok := strings.CutPrefix(authorization, "Bearer ")
		if !ok {
			w.Header().Set("WWW-Authenticate", bearerChallenge)
			return Unauthorized("缺少访问令牌")
		}
		principal, err := verifyAccessToken(tokenString)
		if err != nil {
			w.Header().Set("WWW-Authenticate", invalidAccessTokenChallenge)
			return Unauthorized("无效的访问令牌")
		}
		ctx := context.WithValue(r.Context(), principalContextKey{}, principal)
		next.ServeHTTP(w, r.WithContext(ctx))
		return nil
	})
}

func OptionalAccessToken(next http.Handler) http.Handler {
	return EH(func(w http.ResponseWriter, r *http.Request) error {
		authorization := r.Header.Get("Authorization")
		if authorization == "" {
			next.ServeHTTP(w, r)
			return nil
		}
		tokenString, ok := strings.CutPrefix(authorization, "Bearer ")
		if !ok {
			w.Header().Set("WWW-Authenticate", bearerChallenge)
			return Unauthorized("无效的访问令牌")
		}
		principal, err := verifyAccessToken(tokenString)
		if err != nil {
			w.Header().Set("WWW-Authenticate", invalidAccessTokenChallenge)
			return Unauthorized("无效的访问令牌")
		}
		ctx := context.WithValue(r.Context(), principalContextKey{}, principal)
		next.ServeHTTP(w, r.WithContext(ctx))
		return nil
	})
}

func roleLevel(role string) (int, bool) {
	switch role {
	case roleBanned:
		return 0, true
	case roleRestricted:
		return 1, true
	case roleMember:
		return 2, true
	case roleTrusted:
		return 3, true
	case roleAdmin:
		return 4, true
	default:
		return 0, false
	}
}

func requireRole(role string) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return RequireAccessToken(EH(func(w http.ResponseWriter, r *http.Request) error {
			principal, err := AuthenticatedPrincipal(r)
			if err != nil {
				return err
			}
			principalLevel, principalRoleExists := roleLevel(principal.Role)
			requiredLevel, requiredRoleExists := roleLevel(role)
			if !principalRoleExists || !requiredRoleExists || principalLevel < requiredLevel {
				return Forbidden("权限不足")
			}
			next.ServeHTTP(w, r)
			return nil
		}))
	}
}

func RequireAdmin(next http.Handler) http.Handler {
	return requireRole(roleAdmin)(next)
}

func RequireTrusted(next http.Handler) http.Handler {
	return requireRole(roleTrusted)(next)
}

func RequireMember(next http.Handler) http.Handler {
	return requireRole(roleMember)(next)
}

func AuthenticatedPrincipal(r *http.Request) (Principal, error) {
	principal, ok := r.Context().Value(principalContextKey{}).(Principal)
	if !ok || principal.Username == "" {
		return Principal{}, Unauthorized("缺少认证上下文")
	}
	return principal, nil
}
