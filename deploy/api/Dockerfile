FROM golang:1.24.5-alpine3.22 AS deps
WORKDIR /app
COPY apps/api/go.mod apps/api/go.sum ./
RUN --mount=type=cache,id=go-mod,target=/go/pkg/mod \
    go mod download

FROM deps AS builder
WORKDIR /app
COPY apps/api/. .
RUN go build -o api

FROM alpine:3.22.1
WORKDIR /app
COPY --from=builder /app/api /app/api
EXPOSE 8080
CMD ["./api"]
