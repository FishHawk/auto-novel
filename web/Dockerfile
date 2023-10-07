FROM node:19.0-slim AS builder
COPY . /web
WORKDIR /web
ENV NODE_ENV=production
RUN npm install; \
    npm run build

FROM caddy:2.7.4
COPY Caddyfile /etc/caddy/Caddyfile
COPY --from=builder /web/dist /dist