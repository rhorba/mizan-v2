import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { fakeAuth } from '../../../testing/fake-auth';
import { POLL_MS, StatusEvent, StatusStream, parseSse } from './status-stream';

const URL = 'http://localhost:8080/api/v1/contracts/c-1';

describe('parseSse', () => {
  it('reads named events, joins multi-line data, skips comments and keeps the unfinished tail', () => {
    const { messages, rest } = parseSse(
      ':keep-alive\n\nevent:status\ndata:{"a":1}\n\nevent: status\r\ndata: x\r\ndata: y\r\n\r\nevent:sta',
    );
    expect(messages).toEqual([
      { event: 'status', data: '{"a":1}' },
      { event: 'status', data: 'x\ny' },
    ]);
    expect(rest).toBe('event:sta');
  });

  it('defaults to "message" and ignores blocks without data', () => {
    expect(parseSse('data:hi\n\nevent:status\n\n').messages).toEqual([
      { event: 'message', data: 'hi' },
    ]);
  });
});

describe('StatusStream', () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal('fetch', fetchMock);
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        fakeAuth(true).provider,
        // Real timers at 20 ms: fake timers make Angular's test scheduler loop (heap OOM).
        { provide: POLL_MS, useValue: 20 },
      ],
    });
  });

  afterEach(() => vi.unstubAllGlobals());

  /** A response whose body arrives in the given chunks, then ends. */
  function streamed(...chunks: string[]): Response {
    const body = new ReadableStream<Uint8Array>({
      start(controller) {
        chunks.forEach((c) => controller.enqueue(new TextEncoder().encode(c)));
        controller.close();
      },
    });
    return new Response(body, { status: 200, headers: { 'content-type': 'text/event-stream' } });
  }

  const flush = () => new Promise((resolve) => queueMicrotask(() => resolve(undefined)));
  async function settle() {
    for (let i = 0; i < 20; i++) {
      await flush();
    }
  }

  it('sends the token in a header (never the URL) and emits events split across chunks', async () => {
    fetchMock.mockResolvedValue(
      streamed('event:status\ndata:{"analysisId":"a-1",', '"status":"ANALYZING"}\n\n'),
    );
    const seen: StatusEvent[] = [];
    const errors: unknown[] = [];

    TestBed.inject(StatusStream)
      .stream(`${URL}/events`)
      .subscribe({ next: (e) => seen.push(e), error: (e) => errors.push(e) });
    await settle();

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe(`${URL}/events`);
    expect(url).not.toContain('token');
    expect((init.headers as Record<string, string>)['Authorization']).toBe('Bearer test-token');
    expect(seen).toEqual([{ analysisId: 'a-1', status: 'ANALYZING' }]);
    // The server closing the stream is reported, so watch() can fall back.
    expect(errors).toHaveLength(1);
  });

  it('polls every 3 s by default', () => {
    TestBed.resetTestingModule();
    expect(TestBed.inject(POLL_MS)).toBe(3000);
  });

  it('falls back to polling when the stream cannot open, until unsubscribed', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 503 }));
    const http = TestBed.inject(HttpTestingController);
    const seen: StatusEvent[] = [];
    const summary = (status: string) => ({
      id: 'c-1',
      fileName: 'a.pdf',
      pageCount: 1,
      createdAt: '',
      analysis: { id: 'a-1', language: 'fr', status, requestedAt: '' },
    });
    const nextPoll = async () => {
      await vi
        .waitFor(() => expect(http.match(URL)).toHaveLength(1), { timeout: 1000 })
        .catch(() => undefined);
    };

    const sub = TestBed.inject(StatusStream)
      .watch(URL)
      .subscribe((e) => seen.push(e));
    await vi.waitFor(() => http.expectOne(URL).flush(summary('PENDING')));
    await vi.waitFor(() => http.expectOne(URL).flush(summary('ANALYZING')));
    sub.unsubscribe();
    await nextPoll();

    expect(seen.map((e) => e.status)).toEqual(['PENDING', 'ANALYZING']);
    http.verify();
  });

  it('aborts the request when unsubscribed', async () => {
    let signal: AbortSignal | undefined;
    fetchMock.mockImplementation((_url: string, init: RequestInit) => {
      signal = init.signal ?? undefined;
      return new Promise(() => undefined);
    });

    const sub = TestBed.inject(StatusStream).stream(`${URL}/events`).subscribe();
    sub.unsubscribe();

    expect(signal?.aborted).toBe(true);
  });
});
