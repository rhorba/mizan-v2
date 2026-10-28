import { registerInstrumentations } from '@opentelemetry/instrumentation';
import { XMLHttpRequestInstrumentation } from '@opentelemetry/instrumentation-xml-http-request';
import { OTLPTraceExporter } from '@opentelemetry/exporter-trace-otlp-http';
import { resourceFromAttributes } from '@opentelemetry/resources';
import { ATTR_SERVICE_NAME } from '@opentelemetry/semantic-conventions';
import { BatchSpanProcessor, WebTracerProvider } from '@opentelemetry/sdk-trace-web';

export interface TracingConfig {
  /** Only requests to this origin get a traceparent header (and so continue on the server). */
  apiUrl: string;
  /** OTLP/HTTP base URL of the collector; empty turns browser tracing off. */
  otlpUrl: string;
  /** Never traced: the identity provider (tokens) and the collector itself. */
  issuer: string;
}

/** Escapes a URL prefix for use in a RegExp. */
function prefix(url: string): RegExp {
  // Build-time config (environment.ts), never user input, and every metacharacter is escaped: a
  // literal prefix cannot backtrack. OTel matches only exact strings or RegExps, so a prefix needs one.
  // nosemgrep: javascript.lang.security.audit.detect-non-literal-regexp.detect-non-literal-regexp
  return new RegExp('^' + url.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'));
}

/**
 * Story 6.1: the trace of an upload starts in the browser. Every HttpClient request (XHR) to the
 * API is a client span whose W3C traceparent header the API continues, through Kafka, to the
 * analyzer and back. The live-status stream uses fetch and is deliberately not traced: a span would
 * stay open for the whole stream. Spans carry method, URL and status only — URLs hold ids, never
 * tokens or contract text.
 *
 * @returns the provider, or null when tracing is off (no collector configured)
 */
export function startBrowserTracing(config: TracingConfig): WebTracerProvider | null {
  if (!config.otlpUrl) {
    return null;
  }
  const provider = new WebTracerProvider({
    resource: resourceFromAttributes({ [ATTR_SERVICE_NAME]: 'mizan-web' }),
    spanProcessors: [
      new BatchSpanProcessor(new OTLPTraceExporter({ url: `${config.otlpUrl}/v1/traces` })),
    ],
  });
  provider.register(); // W3C trace context propagator by default
  registerInstrumentations({
    tracerProvider: provider,
    instrumentations: [
      new XMLHttpRequestInstrumentation({
        propagateTraceHeaderCorsUrls: [prefix(config.apiUrl)],
        ignoreUrls: [prefix(config.issuer), prefix(config.otlpUrl)],
      }),
    ],
  });
  return provider;
}
