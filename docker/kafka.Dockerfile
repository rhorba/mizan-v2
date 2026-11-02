# syntax=docker/dockerfile:1
# Kafka above UID 10000, published to our registry. The two jars and the Alpine packages are
# patched here because upstream 4.4.0 ships vulnerable versions; drop each once upstream catches up.
FROM apache/kafka:4.4.0@sha256:833b867a9585a141da455dc2b57431c65cbccb27f11c91f694444cca9f777e0f
LABEL org.opencontainers.image.source="https://github.com/rhorba/mizan-v2"
ADD --chmod=0644 --checksum=sha256:58c8e0b813960d2a248e050c353baea73139b48a2ffc55382d42c69707e17325 \
  https://repo1.maven.org/maven2/at/yawk/lz4/lz4-java/1.11.4/lz4-java-1.11.4.jar /opt/kafka/libs/
ADD --chmod=0644 --checksum=sha256:35228628442e4b9797681c7cbd3b26594f6da3379159dbd0cb07094ed3aece0d \
  https://repo1.maven.org/maven2/org/xerial/snappy/snappy-java/1.1.10.9/snappy-java-1.1.10.9.jar /opt/kafka/libs/
USER root
RUN apk upgrade --no-cache brotli-libs libexpat \
    && rm /opt/kafka/libs/lz4-java-1.11.2.jar /opt/kafka/libs/snappy-java-1.1.10.7.jar
USER 10005:10005
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s \
  CMD ["sh", "-c", "/opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server localhost:9092 > /dev/null 2>&1"]
