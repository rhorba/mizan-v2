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

FROM grafana/otel-lgtm:0.39.0@sha256:4c8056c9c05fcb3c8fd93800e85334bfd79b4d0271b47a54a121a64af41fe1fe
LABEL org.opencontainers.image.source="https://github.com/rhorba/mizan-v2"
COPY --from=tempo /out/tempo /otel-lgtm/tempo/tempo
COPY --from=pyroscope /out/pyroscope /otel-lgtm/pyroscope/pyroscope
COPY --from=otelcol /out/otelcol-contrib /otel-lgtm/otelcol-contrib/otelcol-contrib
# Never started or queried here: obi needs ENABLE_OBI, the datasources are not provisioned.
RUN rm -rf /otel-lgtm/obi /otel-lgtm/tempo/tempo-cli /otel-lgtm/tempo/tempo-query \
    && cd /otel-lgtm/grafana/data/plugins-bundled \
    && rm -rf cloudwatch grafana-postgresql-datasource influxdb jaeger mssql mysql opentsdb
USER 10004:10004
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s CMD ["test", "-f", "/tmp/ready"]
