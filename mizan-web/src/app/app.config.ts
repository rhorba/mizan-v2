import { provideHttpClient, withInterceptorsFromDi, withXhr } from '@angular/common/http';
import {
  ApplicationConfig,
  inject,
  isDevMode,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideTransloco } from '@jsverse/transloco';
import { OAuthStorage, provideOAuthClient } from 'angular-oauth2-oidc';
import { environment } from '../environments/environment';
import { routes } from './app.routes';
import { AuthService } from './auth/auth';
import { TransientOAuthStorage } from './auth/transient-storage';
import { LANGS, LanguageService } from './i18n/language';
import { TranslocoHttpLoader } from './i18n/transloco-loader';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    // withXhr: Angular 22 defaults to fetch, which cannot report upload progress (Story 2.2) and
    // which the browser tracing (XHR instrumentation, Story 6.1) does not see.
    // withInterceptorsFromDi: angular-oauth2-oidc adds the bearer token through a DI interceptor.
    provideHttpClient(withXhr(), withInterceptorsFromDi()),
    provideOAuthClient({
      resourceServer: { allowedUrls: [environment.apiUrl], sendAccessToken: true },
    }),
    { provide: OAuthStorage, useClass: TransientOAuthStorage },
    provideTransloco({
      config: {
        availableLangs: [...LANGS],
        defaultLang: 'fr',
        fallbackLang: 'fr',
        reRenderOnLangChange: true,
        prodMode: !isDevMode(),
      },
      loader: TranslocoHttpLoader,
    }),
    provideAppInitializer(() => inject(LanguageService).init()),
    provideAppInitializer(() => inject(AuthService).init()),
  ],
};
