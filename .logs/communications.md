# Communications Log
<!-- Tracks key questions, user preferences, handoffs -->
<!-- Format: ### [YYYY-MM-DD HH:MM] PREFERENCE/HANDOFF/QUESTION — Title -->


### [2026-10-22] Story 1.2 env defaults accepted ("start")
POSTGRES_PASSWORD=postgres, MIZAN_DB_PASSWORD=mizan, KEYCLOAK_DB_PASSWORD=keycloak, KEYCLOAK_ADMIN=admin/admin; ports POSTGRES 5435, KAFKA 9092, KEYCLOAK 8180, GRAFANA 3000. Local-only values.

### [2026-10-22] Branch protection: user chose 'Exempt admins' (AskUserQuestion).

### [2026-10-23] Story 1.4 inputs
User: 🔴, per-user daily cap = 3. Other env defaults (issuer/JWKS localhost:8180, CORS localhost:4200) to be confirmed at PLAN gate.

### [2026-10-23] Env defaults confirmed ("start"): KEYCLOAK_ISSUER_URI/JWK_SET_URI localhost:8180, MIZAN_CORS_ALLOWED_ORIGINS http://localhost:4200, MIZAN_QUOTA_DAILY_PER_USER=3.
