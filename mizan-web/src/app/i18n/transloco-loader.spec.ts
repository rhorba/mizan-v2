import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { TranslocoHttpLoader } from './transloco-loader';

describe('TranslocoHttpLoader', () => {
  it('fetches public/i18n/<lang>.json', async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const result = firstValueFrom(TestBed.inject(TranslocoHttpLoader).getTranslation('ar'));
    TestBed.inject(HttpTestingController).expectOne('i18n/ar.json').flush({ hello: 'مرحبا' });
    expect(await result).toEqual({ hello: 'مرحبا' });
  });
});
