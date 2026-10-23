import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { ContractsApi, problemSlug } from './contracts-api';

const BASE = 'http://localhost:8080/api/v1/contracts';

describe('ContractsApi', () => {
  beforeEach(() =>
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }),
  );
  const api = () => TestBed.inject(ContractsApi);
  const http = () => TestBed.inject(HttpTestingController);

  it('lists the first page without a cursor and later pages with it', async () => {
    const first = firstValueFrom(api().history(null));
    http().expectOne(BASE).flush({ items: [], nextCursor: 'abc' });
    expect(await first).toEqual({ items: [], nextCursor: 'abc' });

    const next = firstValueFrom(api().history('abc'));
    http().expectOne(`${BASE}?cursor=abc`).flush({ items: [], nextCursor: null });
    await next;
  });

  it('deletes by id, encoding it', async () => {
    const done = firstValueFrom(api().delete('a/b'), { defaultValue: undefined });
    const req = http().expectOne(`${BASE}/a%2Fb`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });
    await done;
  });

  it('reads problem slugs only from our RFC 9457 bodies', () => {
    const problem = (body: unknown) =>
      new HttpErrorResponse({ error: body, status: 400, statusText: 'x' });
    expect(problemSlug(problem({ type: 'urn:mizan:problem:encrypted' }))).toBe('encrypted');
    expect(problemSlug(problem({ type: 'urn:mizan:problem:something-new' }))).toBeNull();
    expect(problemSlug(problem({ type: 42 }))).toBeNull();
    expect(problemSlug(problem(null))).toBeNull();
    expect(problemSlug(new Error('network'))).toBeNull();
  });
});
