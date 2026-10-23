# syntax=docker/dockerfile:1
# Keycloak with build-time options baked in, so it starts with `start --optimized`
# instead of re-running the Quarkus build on every boot (start-dev does).

ARG KEYCLOAK_IMAGE=quay.io/keycloak/keycloak:26.8.1@sha256:79c623a22abacf3fe6c00a5fb30f5b7843d717b234033b80995891c06116d244

FROM ${KEYCLOAK_IMAGE} AS build
ENV KC_DB=postgres \
    KC_HEALTH_ENABLED=true \
    KC_CACHE=local
RUN /opt/keycloak/bin/kc.sh build

FROM ${KEYCLOAK_IMAGE}
COPY --from=build /opt/keycloak/ /opt/keycloak/
# Realm policy passwordBlacklist(mizan.txt) reads this (see keycloak/password-blacklist.NOTICE).
COPY --chown=1000:0 keycloak/themes/ /opt/keycloak/themes/
COPY --chown=1000:0 keycloak/password-blacklist.txt /opt/keycloak/data/password-blacklists/mizan.txt
USER 1000
ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
