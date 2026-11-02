# syntax=docker/dockerfile:1
# Non-root otel-lgtm, published to our registry. Everything it writes lives in mounted volumes.
FROM grafana/otel-lgtm:0.37.0@sha256:57b5ab1ca6619beb180174e2fccce04762470750669d043f544b4726d0582d07
LABEL org.opencontainers.image.source="https://github.com/rhorba/mizan-v2"
USER 10004:10004
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s CMD ["test", "-f", "/tmp/ready"]
