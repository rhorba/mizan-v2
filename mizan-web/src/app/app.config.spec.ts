import { HttpBackend, HttpXhrBackend } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ApplicationInitStatus } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { OAuthStorage } from 'angular-oauth2-oidc';
import { fakeAuth } from '../testing/fake-auth';
import { appConfig } from './app.config';
import { TransientOAuthStorage } from './auth/transient-storage';
import { LanguageService } from './i18n/language';

describe('appConfig', () => {
  it('starts language and auth, and keeps OAuth tokens in transient storage', async () => {
    localStorage.setItem('mizan.lang', 'ar');
    const { fake, provider } = fakeAuth();
    TestBed.configureTestingModule({
      providers: [...appConfig.providers, provideHttpClientTesting(), provider],
    });
    await TestBed.inject(ApplicationInitStatus).donePromise;

    expect(TestBed.inject(LanguageService).current()).toBe('ar');
    expect(document.documentElement.dir).toBe('rtl');
    expect(fake.init).toHaveBeenCalled();
    expect(TestBed.inject(OAuthStorage)).toBeInstanceOf(TransientOAuthStorage);
    localStorage.clear();
  });

  it('sends HttpClient requests over XHR: upload progress and browser tracing need it', () => {
    // Angular 22 defaults to fetch; under fetch both silently stop working (.logs/issues.md).
    const { provider } = fakeAuth();
    TestBed.configureTestingModule({ providers: [...appConfig.providers, provider] });

    expect(TestBed.inject(HttpBackend)).toBeInstanceOf(HttpXhrBackend);
  });
});
