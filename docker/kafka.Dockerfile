# syntax=docker/dockerfile:1
# Kafka above UID 10000, published to our registry.
FROM apache/kafka:4.3.1@sha256:77e3df9054047a88b520d0cc46e16696d3b22022e1d580aeccd2632df6532837
LABEL org.opencontainers.image.source="https://github.com/rhorba/mizan-v2"
USER 10005:10005
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s \
  CMD ["sh", "-c", "/opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server localhost:9092 > /dev/null 2>&1"]
