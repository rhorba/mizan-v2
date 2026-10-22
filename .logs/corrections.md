# Corrections & Plan Changes
<!-- Tracks scope changes, pivots, plan adjustments -->
<!-- Format: ### [YYYY-MM-DD HH:MM] SCOPE_CHANGE/PIVOT/REPLAN — Title -->


### [2026-10-22] CORRECTION — Boot 4.0.x → 4.1.2
Architecture said "Spring Boot 4.0.x"; Story 1.1 says "latest compatible GA". 4.1.2 is current GA and spike proves Modulith 2.1.2 + Spring AI 2.0.2 work on it. Pin 4.1.2; record in ADR-8 and update architecture tech-stack row.

### [2026-10-22] NOTE — Checkstyle ConstantName suppressed for *ArchitectureTests.java (ArchUnit idiom: camelCase static final rules).

### [2026-10-22] CORRECTION — JaCoCo *Application exclusion removed; main() covered by tests instead (gate must reflect raw coverage).
