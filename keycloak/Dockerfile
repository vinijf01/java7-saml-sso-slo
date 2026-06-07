FROM quay.io/keycloak/keycloak:23.0.7

COPY realm/ /opt/keycloak/data/import/

ENV KC_HEALTH_ENABLED=true

ENTRYPOINT ["/opt/keycloak/bin/kc.sh", "start-dev", "--import-realm"]
