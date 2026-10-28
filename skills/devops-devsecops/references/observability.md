# Observability Reference — metrics, dashboards, alerts as code

Load before adding a metric, a dashboard panel or an alert rule. Traces and logs: see the B1/B2
notes in `.logs/activity.md` (Sprint 5) — this file is about metrics and what reads them.

## 1. Pipeline (local and preprod)

```
app (Micrometer) ──OTLP/HTTP 4318──▶ otel-lgtm collector ──▶ Prometheus ◀── Grafana (dashboards, alert rules)
app (OTel SDK)   ──traces/logs─────▶                     ──▶ Tempo / Loki ◀──┘
```

- Spring Boot maps `OTEL_EXPORTER_OTLP_ENDPOINT` to metrics, traces and logs. Unset (tests, IDE)
  → nothing leaves the JVM. Tests also set `management.otlp.metrics.export.enabled: false`.
- Push interval = `management.otlp.metrics.export.step` (default 1 min). Alert windows must be
  several steps long (≥ 5 min) or a single late push flaps them.

## 2. Naming: Micrometer → Prometheus

Micrometer names are dotted (`mizan.analysis.outcomes`). The OTLP → Prometheus translation:

| Micrometer | Prometheus series |
|---|---|
| counter `a.b` | `a_b_total` |
| gauge `a.b` | `a_b` |
| timer `a.b` (OTLP base unit = ms) | `a_b_milliseconds_bucket` / `_count` / `_sum` |
| tag `outcome` | label `outcome` |
| resource `service.name` | label `job` (and `service_name` on target_info) |

**Never guess the final name**: confirm against the live Prometheus
(`/api/v1/label/__name__/values`) before writing a query, and record it below.

## 3. Rules for new meters

1. **Tags come from closed sets only** (outcome, type, method, status, templated uri). Never ids,
   user subjects, file names, model output or contract text — cardinality *and* privacy.
2. **Count where the fact happens** (the analyzer knows tokens and outcomes; the API knows caps).
3. **Gauges that read the database**: one cheap indexed query, `strongReference(true)`, and accept
   that a failing query reports NaN rather than breaking the push.
4. **Latency SLOs**: prefer explicit SLO boundaries
   (`management.metrics.distribution.slo.<meter>`) over `percentiles-histogram` (≈ 70 buckets ×
   every uri/status series). Put the alert threshold itself on a boundary so p95 > 1 s is exact
   at the boundary, not interpolated across it.
5. **Long-lived requests** (SSE `/events`, 5 min) are not latency: exclude them in the query.

## 4. Kafka consumer lag

Boot's `KafkaMetricsAutoConfiguration` binds Kafka client metrics to every Boot-built consumer
factory: `kafka.consumer.fetch.manager.records.lag` (per partition; `.lag.max` is NaN between
fetches — alert on `records_lag`). It is the consumer's own
view — **when the consumer is down, the series disappears** instead of growing. Broker-side lag
(kafka-exporter / AdminClient) fixes that; not needed until preprod alerting is real (YAGNI).

## 5. Dashboards and alert rules as code

- Files live in the repo and are mounted read-only into otel-lgtm; no UI-only changes (they are
  lost on container restart). Export → edit → commit if you prototype in the UI.
- Datasources are referenced by **uid** (provisioned by the image — confirm with
  `GET /api/datasources`).
- Grafana alert rule (file provisioning, `apiVersion: 1`): query `A` (datasource, relative time
  range) → `B` reduce (last) → `C` threshold; `for:` = how long it must hold; set `noDataState`
  deliberately (`OK` for "nothing happened", `Alerting` only when silence itself is bad).
- Contact points/notification policies need SMTP (Sprint 6): until then rules evaluate and show
  in Grafana → Alerting, and notify nobody.

## 6. Verifying

- Unit/integration: assert meters by name + tags on the test `MeterRegistry`; assert the histogram
  has the SLO boundary the alert relies on.
- Real stack: Prometheus query returns the series; `GET /api/v1/provisioning/alert-rules` lists
  every rule; the dashboard loads with no "No data" panel after one upload.

## 7. Confirmed names (fill from the live stack)

Confirmed 2026-10-28 on otel-lgtm 0.37.0 (Prometheus native OTLP ingestion, Loki OTLP).

| Signal | Query basis |
|---|---|
| Analyses by outcome | `mizan_analysis_outcomes_total{outcome="completed\|failed"}` (mizan-analyzer) |
| Model tokens | `mizan_analysis_tokens_total{type="input\|output"}` (completed + billed failures) |
| Global cap | `mizan_quota_global_used`, `mizan_quota_global_cap` (mizan-api) |
| API latency | `http_server_requests_milliseconds_bucket{service_name="mizan-api", le="100…5000"}` — buckets in **ms** |
| Kafka lag | `kafka_consumer_fetch_manager_records_lag{service_name, topic, partition}` — not `records_lag_max`, which is NaN between fetches; `topic` appears twice (dotted and underscored), harmless under `max` |
| Logs | Loki stream label `service_name` only; `severity_text`, `trace_id`, `span_id` are structured metadata → filter with `\| severity_text="ERROR"` |
| Traces | API server spans are named `http post /api/v1/contracts` (lowercase); browser spans `GET`/`POST` from `mizan-web`; model call `chat claude-opus-5-5` with `gen_ai.*` attributes only |

Gotchas met:
- `increase()` misses what a counter already held at its first push (process start → first
  export). Low-volume counters read low for the first hour after a restart.
- Angular 22 `HttpClient` defaults to **fetch**; the XHR instrumentation sees nothing unless
  `provideHttpClient(withXhr(), …)`. Unit tests with `provideHttpClientTesting()` cannot catch it
  — `app.config.spec.ts` asserts the real `HttpBackend`.
