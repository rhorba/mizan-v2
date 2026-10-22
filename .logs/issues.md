# Issue Log
<!-- Tracks bugs, errors, blockers with status -->
<!-- Format: ### [YYYY-MM-DD HH:MM] BUG/BLOCKER/ERROR — Title -->


### [2026-10-22] ISSUE (resolved) — victools jsonschema convergence
Enforcer dependencyConvergence: Spring AI 2.0.2 → victools 5.0.0, anthropic-java-core 2.72.0 → 4.38.0 (runtime). Fix: import jsonschema-generator-bom 5.0.0 in parent. Risk: Anthropic SDK's own schema helpers run on 5.x — we only use Spring AI's path. Re-check on Spring AI upgrades.

### [2026-10-22] ISSUE (resolved) — vite 8.3.0 advisories (dev-only)
@angular/build 22.2.4 pins vite 8.3.0 (GHSA-9jrq-w75r-8gcw high + launch-editor/fs.allow advisories, dev server only). Fix: npm overrides vite 8.3.5 (patch). Audit 0. Remove override when @angular/build bumps vite.
