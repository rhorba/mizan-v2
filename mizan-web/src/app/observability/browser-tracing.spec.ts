import { trace } from '@opentelemetry/api';
import { startBrowserTracing } from './browser-tracing';

const CONFIG = {
  apiUrl: 'http://localhost:8080',
  otlpUrl: 'http://localhost:4318',
  issuer: 'http://localhost:8180/realms/mizan',
};

/** Opens and sends an XHR, then aborts it; returns the headers the page set on it. */
function headersSentTo(url: string): Record<string, string> {
  const headers: Record<string, string> = {};
  const xhr = new XMLHttpRequest();
  const set = xhr.setRequestHeader.bind(xhr);
  xhr.setRequestHeader = (name: string, value: string) => {
    headers[name.toLowerCase()] = value;
    set(name, value);
  };
  xhr.open('GET', url);
  xhr.send();
  xhr.abort();
  return headers;
}

describe('startBrowserTracing', () => {
  it('stays off without a collector URL', () => {
    expect(startBrowserTracing({ ...CONFIG, otlpUrl: '' })).toBeNull();
  });

  it('sends W3C trace context to the API only, never to Keycloak or other origins', async () => {
    const provider = startBrowserTracing(CONFIG);
    expect(provider).not.toBeNull();
    expect(trace.getTracer('probe').startSpan('probe').spanContext().traceId).toMatch(
      /^[0-9a-f]{32}$/,
    );

    const api = headersSentTo(`${CONFIG.apiUrl}/api/v1/contracts`);
    expect(api['traceparent']).toMatch(/^00-[0-9a-f]{32}-[0-9a-f]{16}-01$/);

    expect(headersSentTo(`${CONFIG.issuer}/protocol/openid-connect/token`)).not.toHaveProperty(
      'traceparent',
    );
    expect(headersSentTo('https://example.com/elsewhere')).not.toHaveProperty('traceparent');

    await provider!.shutdown();
  });
});
