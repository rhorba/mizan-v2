import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';
import { environment } from './environments/environment';

bootstrapApplication(App, appConfig).catch((err) => console.error(err));

// Story 6.1: browser tracing in its own chunk, loaded after start so the OpenTelemetry SDK
// (~55 kB) stays out of the initial bundle. An upload comes long after; requests made while the
// page first loads may go untraced.
if (environment.otlpUrl) {
  import('./app/observability/browser-tracing')
    .then((m) =>
      m.startBrowserTracing({
        apiUrl: environment.apiUrl,
        otlpUrl: environment.otlpUrl,
        issuer: environment.auth.issuer,
      }),
    )
    .catch((err) => console.error('browser tracing not started', err));
}
