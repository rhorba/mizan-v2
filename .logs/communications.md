# Communications Log
<!-- Tracks key questions, user preferences, handoffs -->
<!-- Format: ### [YYYY-MM-DD HH:MM] PREFERENCE/HANDOFF/QUESTION — Title -->


### [2026-10-22] Story 1.2 env defaults accepted ("start")
POSTGRES_PASSWORD=postgres, MIZAN_DB_PASSWORD=mizan, KEYCLOAK_DB_PASSWORD=keycloak, KEYCLOAK_ADMIN=admin/admin; ports POSTGRES 5435, KAFKA 9092, KEYCLOAK 8180, GRAFANA 3000. Local-only values.

### [2026-10-22] Branch protection: user chose 'Exempt admins' (AskUserQuestion).

### [2026-10-23] Story 1.4 inputs
User: 🔴, per-user daily cap = 3. Other env defaults (issuer/JWKS localhost:8180, CORS localhost:4200) to be confirmed at PLAN gate.

### [2026-10-23] Env defaults confirmed ("start"): KEYCLOAK_ISSUER_URI/JWK_SET_URI localhost:8180, MIZAN_CORS_ALLOWED_ORIGINS http://localhost:4200, MIZAN_QUOTA_DAILY_PER_USER=3.

### [2026-10-23] Sprint 2 PLAN gate answers
Datasource defaults OK (SPRING_DATASOURCE_URL jdbc:postgresql://localhost:5435/mizan, user mizan_app, password = MIZAN_DB_PASSWORD). ClamAV absent → skip scanning with a warning (fail-open, user choice). XHR backend instead of withFetch OK (upload progress).

### [2026-10-23] B4 → B5: owner replied "go" without objecting to fail-closed when ClamAV is configured but down; treated as accepted (can be revisited).

### [2026-10-26] Sprint 3 UNDERSTAND: owner wants spend kept under $3/month (cap set in Console). Prefers I act directly ("just do it"). Paid/credential steps stay with the owner.

### [2026-10-26] Sprint 4 UNDERSTAND: owner picked per-user 5, global 20, tokens in daily_usage.

### [2026-10-29] Sprint 6 UNDERSTAND — owner choices
Host 144.24.200.83.sslip.io; SMTP Gmail app password (owner puts it in .env, not chat); GHCR packages public (no pull secret). Generated locally: DB/Keycloak/Grafana passwords, age key pair.
