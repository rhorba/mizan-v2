# syntax=docker/dockerfile:1
# Non-root otel-lgtm, published to our registry. Upstream binaries carry fixable CVEs, so the
# collector, Tempo and Pyroscope are rebuilt from pinned commits with patched dependencies, and
# components mizan does not use are removed. On every upgrade: repin the commits and re-run Trivy.
ARG GO_IMAGE=golang:1.27.3-alpine@sha256:d200d2dd76b03a99b52651fd1325aff7dcd35422c35c01bac56571b0507f3deb
ARG GO_JOSE=v4.1.6

FROM --platform=$BUILDPLATFORM ${GO_IMAGE} AS go-base
RUN apk add --no-cache git
ENV CGO_ENABLED=0
WORKDIR /src

FROM go-base AS tempo
ARG TARGETOS TARGETARCH GO_JOSE
# v3.1.1
RUN --mount=type=cache,target=/go/pkg/mod,sharing=locked \
    git init -q . && git fetch -q --depth 1 https://github.com/grafana/tempo 9db7df79d2af105a90d96484e6403e1fda0d011e \
 && git checkout -q FETCH_HEAD \
 && go get github.com/go-jose/go-jose/v4@${GO_JOSE} \
 && GOOS=$TARGETOS GOARCH=$TARGETARCH go build -mod=mod -trimpath -buildvcs=false -o /out/tempo ./cmd/tempo

FROM go-base AS pyroscope
ARG TARGETOS TARGETARCH GO_JOSE
# v2.4.2
RUN --mount=type=cache,target=/go/pkg/mod,sharing=locked \
    git init -q . && git fetch -q --depth 1 https://github.com/grafana/pyroscope 10a78e5ef038f8b97d6966bc76f1cf21bb5616ac \
 && git checkout -q FETCH_HEAD \
 && go get github.com/go-jose/go-jose/v4@${GO_JOSE} \
 && GOOS=$TARGETOS GOARCH=$TARGETARCH go build -mod=mod -trimpath -buildvcs=false -tags netgo -o /out/pyroscope ./cmd/pyroscope

FROM go-base AS otelcol
ARG TARGETOS TARGETARCH GO_JOSE
COPY otel-lgtm/otelcol-manifest.yaml manifest.yaml
RUN --mount=type=cache,target=/go/pkg/mod,sharing=locked \
    go install go.opentelemetry.io/collector/cmd/builder@v0.164.0 \
 && builder --config manifest.yaml --skip-compilation \
 && cd _build \
 && go get github.com/go-jose/go-jose/v4@${GO_JOSE} \
 && go mod tidy \
 && GOOS=$TARGETOS GOARCH=$TARGETARCH go build -trimpath -buildvcs=false -tags grpcnotrace -o /out/otelcol-contrib .

# Newer official Grafana Labs releases of the three datasources mizan queries. Downloaded, not
# rebuilt: their signatures stay valid, so unsigned plugins never have to be allowed.
FROM go-base AS plugins
ARG TARGETARCH
RUN set -eu;     case "$TARGETARCH" in       amd64) loki=982cfb81977352db65404cec671952453454643bd9a6c883041c38be519a74ea              tempo=08b0de5ed46b3b60693b41495bc00633ae63c3f2e76faa819c7fa95a4dbbc529              pyroscope=e01bd4c5fcd3e0da0f899f1a5d589fe1dffe64252fa2948bc3ae2bf11e087d9a ;;       arm64) loki=a89809087e7c391daaeefe6a639bbf4c84ba4c8e29ac993778d9cbe81946147d              tempo=6cd104432a03d67016c7f29be7aea646890330a84a7cce2cd15919cfdc0e77ed              pyroscope=8def4aac36f5e779cf13b5ecab7f88f01d8cf46444eab6d170a269f51a9404a1 ;;       *) exit 1 ;;     esac;     fetch() {       wget -qO plugin.zip "https://grafana.com/api/plugins/$1/versions/$2/download?os=linux&arch=$TARGETARCH";       echo "$3  plugin.zip" | sha256sum -c -;       unzip -q plugin.zip -d /out;       rm plugin.zip;     };     fetch loki 13.2.5 "$loki";     fetch tempo 13.4.1 "$tempo";     fetch grafana-pyroscope-datasource 13.0.9 "$pyroscope";     chmod 0755 /out/*/gpx_*

FROM grafana/otel-lgtm:0.39.0@sha256:4c8056c9c05fcb3c8fd93800e85334bfd79b4d0271b47a54a121a64af41fe1fe
LABEL org.opencontainers.image.source="https://github.com/rhorba/mizan-v2"
COPY --from=tempo /out/tempo /otel-lgtm/tempo/tempo
COPY --from=pyroscope /out/pyroscope /otel-lgtm/pyroscope/pyroscope
COPY --from=otelcol /out/otelcol-contrib /otel-lgtm/otelcol-contrib/otelcol-contrib
# Not in the image: the old plugin copies, unused binaries and datasources. obi needs ENABLE_OBI, the datasources are not provisioned.
RUN rm -rf /otel-lgtm/obi /otel-lgtm/tempo/tempo-cli /otel-lgtm/tempo/tempo-query \
    && cd /otel-lgtm/grafana/data/plugins-bundled \
    && rm -rf cloudwatch grafana-postgresql-datasource grafana-pyroscope-datasource influxdb jaeger loki mssql mysql opentsdb tempo
COPY --from=plugins /out/ /otel-lgtm/grafana/data/plugins-bundled/
USER 10004:10004
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s CMD ["test", "-f", "/tmp/ready"]
