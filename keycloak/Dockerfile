# Keycloak with DockMind's realm built in, for deployment (locally, Docker Compose mounts the file instead).
FROM quay.io/keycloak/keycloak:26.6.1
COPY --chmod=0644 realm-dockmind.json /opt/keycloak/data/import/realm-dockmind.json
