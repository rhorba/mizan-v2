# syntax=docker/dockerfile:1
# Serves the Angular SPA with unprivileged nginx. Build context: mizan-web/.
#   docker build -f docker/web.Dockerfile -t mizan-web mizan-web

ARG NODE_IMAGE=node:22.23.4-alpine@sha256:fe4f6d74e20370acb04ad889077d14d0f64a9c8ea6a42bc60cddf1e844699914
ARG NGINX_IMAGE=nginxinc/nginx-unprivileged:1.30.6-alpine@sha256:f5116ed4606f3eb015dd4a8e28cb9b351476dc0ad4bc6e9f93b19cec130cdf03

# === Build === static output is the same on every platform, so build natively (no QEMU).
FROM --platform=$BUILDPLATFORM ${NODE_IMAGE} AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY . .
# Lint, tests and coverage run in CI; the image build only bundles.
ARG NG_CONFIGURATION=production,preprod
RUN npx ng build --configuration ${NG_CONFIGURATION}

# === Runtime ===
FROM ${NGINX_IMAGE}
LABEL org.opencontainers.image.source="https://github.com/rhorba/mizan-v2"
# CVE-2026-111497 (CRITICAL): the pinned base ships brotli-libs 1.2.0-r1; Alpine has 1.2.1-r0.
# Drop the upgrade once the nginx digest above is bumped to an image that includes the fix.
# curl is not needed at runtime (probes come from the kubelet); removing it also removes
# CVE-2026-92392 (HIGH, curl/libcurl).
USER root
RUN apk upgrade --no-cache brotli-libs && apk del --no-cache curl
USER 101
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/dist/mizan-web/browser/ /usr/share/nginx/html/
USER 101
