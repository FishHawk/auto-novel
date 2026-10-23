FROM node:22-alpine AS deps
ENV PNPM_HOME="/pnpm"
ENV PATH="$PNPM_HOME:$PATH"
RUN corepack enable
WORKDIR /app
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN corepack install
COPY patches/ ./patches/
COPY packages/crawler/package.json ./packages/crawler/
COPY packages/translator/package.json ./packages/translator/
COPY web/package.json ./web/
COPY apps/admin/package.json ./apps/admin/
RUN --mount=type=cache,id=pnpm,target=/pnpm/store pnpm install --frozen-lockfile

FROM deps AS builder
WORKDIR /app
ARG VITE_COMMIT_SHA=unknown
ARG VITE_BUILD_TIME
COPY packages/crawler/ ./packages/crawler/
COPY packages/translator/ ./packages/translator/
COPY web/ ./web/
COPY apps/admin/ ./apps/admin/
RUN VITE_COMMIT_SHA="${VITE_COMMIT_SHA}" \
    VITE_BUILD_TIME="${VITE_BUILD_TIME}" \
    pnpm --filter @auto-novel/web... --filter @auto-novel/admin run build

FROM caddy:2.7.4
COPY deploy/web/Caddyfile /etc/caddy/Caddyfile
COPY --from=builder /app/web/dist /dist
COPY --from=builder /app/apps/admin/dist /dist/admin
