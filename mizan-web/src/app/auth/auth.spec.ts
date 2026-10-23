import { TestBed } from '@angular/core/testing';
import { OAuthEvent, OAuthService, OAuthStorage } from 'angular-oauth2-oidc';
import { Subject } from 'rxjs';
import { translocoTesting } from '../../testing/transloco-testing';
import { LanguageService } from '../i18n/language';
import { AuthService } from './auth';

describe('AuthService', () => {
  let events: Subject<OAuthEvent>;
  let oauth: Record<string, ReturnType<typeof vi.fn>>;
  let storage: { removeItem: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    events = new Subject<OAuthEvent>();
    oauth = {
      configure: vi.fn(),
      loadDiscoveryDocumentAndTryLogin: vi.fn(async () => true),
      setupAutomaticSilentRefresh: vi.fn(),
      hasValidAccessToken: vi.fn(() => false),
      getIdentityClaims: vi.fn(() => null),
      initCodeFlow: vi.fn(),
      logOut: vi.fn(),
    };
    storage = { removeItem: vi.fn() };
    TestBed.configureTestingModule({
      imports: [translocoTesting()],
      providers: [
        { provide: OAuthService, useValue: { ...oauth, events } },
        { provide: OAuthStorage, useValue: storage },
      ],
    });
  });

  const auth = () => TestBed.inject(AuthService);

  it('configures Authorization Code + PKCE against Keycloak, refreshing without an iframe', async () => {
    await auth().init();
    expect(oauth['configure']).toHaveBeenCalledWith(
      expect.objectContaining({
        issuer: 'http://localhost:8180/realms/mizan',
        clientId: 'mizan-web',
        responseType: 'code',
        redirectUri: `${window.location.origin}/contracts`,
        postLogoutRedirectUri: `${window.location.origin}/`,
        useSilentRefresh: false,
      }),
    );
    expect(oauth['setupAutomaticSilentRefresh']).toHaveBeenCalled();
    expect(auth().isAuthenticated()).toBe(false);
    expect(storage.removeItem).not.toHaveBeenCalled();
  });

  it('after the Keycloak callback: signed in, single-use values and iss parameter dropped', async () => {
    window.history.replaceState(null, '', '/contracts?iss=http%3A%2F%2Fkc&x=1');
    oauth['hasValidAccessToken'].mockReturnValue(true);
    oauth['getIdentityClaims'].mockReturnValue({ email: 'karim@mizan.test' });

    await auth().init();

    expect(auth().isAuthenticated()).toBe(true);
    expect(auth().email()).toBe('karim@mizan.test');
    expect(storage.removeItem).toHaveBeenCalledWith('PKCE_verifier');
    expect(storage.removeItem).toHaveBeenCalledWith('nonce');
    expect(window.location.search).toBe('?x=1');
    window.history.replaceState(null, '', '/');
  });

  it('keeps the URL when there is no iss parameter', async () => {
    window.history.replaceState(null, '', '/contracts');
    oauth['hasValidAccessToken'].mockReturnValue(true);
    await auth().init();
    expect(window.location.pathname).toBe('/contracts');
    window.history.replaceState(null, '', '/');
  });

  it('stays signed out when Keycloak cannot be reached', async () => {
    oauth['loadDiscoveryDocumentAndTryLogin'].mockRejectedValue(new Error('offline'));
    await auth().init();
    expect(auth().isAuthenticated()).toBe(false);
  });

  it('follows token events (e.g. refresh or expiry)', async () => {
    await auth().init();
    oauth['hasValidAccessToken'].mockReturnValue(true);
    events.next({ type: 'token_refreshed' } as OAuthEvent);
    expect(auth().isAuthenticated()).toBe(true);
  });

  it('opens login or registration in the current UI language', () => {
    TestBed.inject(LanguageService).use('ar');
    auth().login();
    expect(oauth['initCodeFlow']).toHaveBeenCalledWith('', { ui_locales: 'ar' });
    auth().register();
    expect(oauth['initCodeFlow']).toHaveBeenCalledWith('', {
      prompt: 'create',
      ui_locales: 'ar',
    });
  });

  it('logs out through Keycloak', () => {
    auth().logout();
    expect(oauth['logOut']).toHaveBeenCalled();
  });
});
