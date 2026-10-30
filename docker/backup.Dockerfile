# syntax=docker/dockerfile:1
# Story 7.3: the nightly backup job (docker/backup/backup.sh). Same PostgreSQL image as the
# server, so pg_dump matches its major version; age encrypts, curl uploads.
FROM postgres:18.6-alpine@sha256:10a5c69a83035ab5239f5620d708520e8b28f5aa1f2b20143e6024532edadfb5
LABEL org.opencontainers.image.source="https://github.com/rhorba/mizan-v2"
# gosu only serves the server entrypoint (this job starts as uid 70) and carries an old Go
# runtime; brotli-libs comes with curl and needs the fixed release.
RUN apk add --no-cache age curl \
    && apk upgrade --no-cache brotli-libs \
    && rm /usr/local/bin/gosu
COPY --chmod=0755 backup/backup.sh /usr/local/bin/mizan-backup
USER 70
ENTRYPOINT ["/usr/local/bin/mizan-backup"]
