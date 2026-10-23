import { Injectable } from '@angular/core';
import { OAuthStorage } from 'angular-oauth2-oidc';

/** Values that must survive the round trip to Keycloak; the library removes them after use. */
const REDIRECT_KEYS = new Set(['PKCE_verifier', 'nonce']);
const PREFIX = 'mizan.oidc.';

/**
 * Tokens live in memory only (docs/security §3: never in localStorage). A reload therefore signs
 * the SPA out locally; Keycloak's SSO session signs it back in without a password prompt.
 */
@Injectable()
export class TransientOAuthStorage implements OAuthStorage {
  private readonly memory = new Map<string, string>();

  getItem(key: string): string | null {
    if (REDIRECT_KEYS.has(key)) {
      return this.session(() => sessionStorage.getItem(PREFIX + key)) ?? null;
    }
    return this.memory.get(key) ?? null;
  }

  setItem(key: string, value: string): void {
    if (REDIRECT_KEYS.has(key)) {
      this.session(() => sessionStorage.setItem(PREFIX + key, value));
      return;
    }
    this.memory.set(key, value);
  }

  removeItem(key: string): void {
    if (REDIRECT_KEYS.has(key)) {
      this.session(() => sessionStorage.removeItem(PREFIX + key));
      return;
    }
    this.memory.delete(key);
  }

  private session<T>(action: () => T): T | undefined {
    try {
      return action();
    } catch {
      return undefined; // Storage blocked: login cannot complete, the user stays signed out.
    }
  }
}
