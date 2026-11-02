# syntax=docker/dockerfile:1
# Non-root ClamAV, published to our registry. The upstream image starts as root to chown the
# database and then drops to the clamav user; here the volume comes from fsGroup instead.
FROM clamav/clamav-debian:1.5.4@sha256:cce3e1a43ae2bb40e11335c52c0e1e98273b12fa3d587e617f63a6907cc4b70b
LABEL org.opencontainers.image.source="https://github.com/rhorba/mizan-v2"
RUN sed -i '/^User clamav/d' /etc/clamav/clamd.conf \
    && sed -i '/^DatabaseOwner/d' /etc/clamav/freshclam.conf
COPY --chmod=0755 clamav/start.sh /usr/local/bin/mizan-clamav
USER 10003:10003
HEALTHCHECK --interval=30s --timeout=10s --start-period=600s CMD ["clamdcheck.sh"]
ENTRYPOINT ["/usr/local/bin/mizan-clamav"]
