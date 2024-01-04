FROM node:21-slim AS builder
COPY . /web
WORKDIR /web
RUN npm i -g pnpm; \
    pnpm install --frozen-lockfile --prod; \
    pnpm run build

FROM caddy:2.7.4
COPY Caddyfile /etc/caddy/Caddyfile
COPY --from=builder /web/dist /dist