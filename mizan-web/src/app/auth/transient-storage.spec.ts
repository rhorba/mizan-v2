import { TransientOAuthStorage } from './transient-storage';

describe('TransientOAuthStorage', () => {
  beforeEach(() => {
    sessionStorage.clear();
    localStorage.clear();
  });
  afterEach(() => vi.restoreAllMocks());

  it('keeps tokens in memory only', () => {
    const storage = new TransientOAuthStorage();
    storage.setItem('access_token', 'secret');
    storage.setItem('refresh_token', 'secret-too');

    expect(storage.getItem('access_token')).toBe('secret');
    expect(sessionStorage.length).toBe(0);
    expect(localStorage.length).toBe(0);
    expect(new TransientOAuthStorage().getItem('access_token')).toBeNull();

    storage.removeItem('access_token');
    expect(storage.getItem('access_token')).toBeNull();
  });

  it('keeps only the PKCE verifier and nonce in sessionStorage, to survive the redirect', () => {
    const storage = new TransientOAuthStorage();
    storage.setItem('PKCE_verifier', 'v');
    storage.setItem('nonce', 'n');

    expect(sessionStorage.getItem('mizan.oidc.PKCE_verifier')).toBe('v');
    expect(new TransientOAuthStorage().getItem('nonce')).toBe('n');

    storage.removeItem('PKCE_verifier');
    storage.removeItem('nonce');
    expect(sessionStorage.length).toBe(0);
  });

  it('degrades to signed-out when sessionStorage is blocked', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    const storage = new TransientOAuthStorage();
    storage.setItem('nonce', 'n');
    expect(storage.getItem('nonce')).toBeNull();
  });
});
