import { Injectable, inject, signal } from '@angular/core';
import { OAuthService, OAuthStorage } from 'angular-oauth2-oidc';
import { environment } from '../../environments/environment';
import { LanguageService } from '../i18n/language';

/** Keycloak sign-up, login and logout with Authorization Code + PKCE (ADR-3). */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly oauth = inject(OAuthService);
  private readonly language = inject(LanguageService);
  private readonly storage = inject(OAuthStorage);

  readonly isAuthenticated = signal(false);
  readonly email = signal<string | null>(null);

  async init(): Promise<void> {
    const origin = window.location.origin;
    this.oauth.configure({
      issuer: environment.auth.issuer,
      clientId: environment.auth.clientId,
      responseType: 'code',
      scope: 'openid profile email',
      redirectUri: `${origin}/contracts`,
      postLogoutRedirectUri: `${origin}/`,
      useSilentRefresh: false, // refresh with the refresh token, no hidden iframe
      showDebugInformation: false,
    });
    this.oauth.events.subscribe(() => this.sync());
    try {
      await this.oauth.loadDiscoveryDocumentAndTryLogin();
      this.oauth.setupAutomaticSilentRefresh();
      if (this.oauth.hasValidAccessToken()) {
        this.forgetCallback();
        this.restoreRequestedPath();
      }
    } catch {
      // Keycloak unreachable or a stale callback: stay signed out; the landing page still works.
    }
    this.sync();
  }

  /** @param returnPath where to land after login (carried in the OIDC state, same-origin only) */
  login(returnPath = ''): void {
    this.oauth.initCodeFlow(returnPath, { ui_locales: this.language.current() });
  }

  /** Opens Keycloak's registration form directly (OIDC prompt=create). */
  register(): void {
    this.oauth.initCodeFlow('', { prompt: 'create', ui_locales: this.language.current() });
  }

  logout(): void {
    this.oauth.logOut();
  }

  /** The PKCE verifier and nonce are single-use; drop them and the RFC 9207 iss parameter. */
  private forgetCallback(): void {
    this.storage.removeItem('PKCE_verifier');
    this.storage.removeItem('nonce');
    const url = new URL(window.location.href);
    if (url.searchParams.has('iss')) {
      url.searchParams.delete('iss');
      window.history.replaceState(window.history.state, '', url.pathname + url.search + url.hash);
    }
  }

  /**
   * Keycloak always redirects to /contracts; the page originally asked for travels in the OIDC
   * state. It is restored before the router's first navigation, and only if it is a path on this
   * origin ("/x", never "//host" or "https://…"), so the state cannot become an open redirect.
   */
  private restoreRequestedPath(): void {
    let path: string;
    try {
      path = decodeURIComponent(this.oauth.state ?? '');
    } catch {
      return;
    }
    // Browsers treat "/\host" like "//host", so a backslash after the slash is refused too.
    if (/^\/(?![/\\])/.test(path)) {
      window.history.replaceState(window.history.state, '', path);
    }
  }

  private sync(): void {
    this.isAuthenticated.set(this.oauth.hasValidAccessToken());
    const claims = this.oauth.getIdentityClaims() as { email?: string } | null;
    this.email.set(claims?.email ?? null);
  }
}
