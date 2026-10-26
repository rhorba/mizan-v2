import { HttpClient } from '@angular/common/http';
import { Injectable, InjectionToken, inject } from '@angular/core';
import { Observable, catchError, exhaustMap, map, switchMap, timer } from 'rxjs';
import { AuthService } from '../../auth/auth';
import { AnalysisStatus, ContractSummary } from '../contracts-api';

/** One "status" event of GET /api/v1/contracts/{id}/events. */
export interface StatusEvent {
  analysisId: string;
  status: AnalysisStatus;
}

export interface SseMessage {
  event: string;
  data: string;
}

/** Fallback cadence when the stream is unavailable (Story 3.4: every 3 s). */
export const POLL_MS = new InjectionToken<number>('status poll interval (ms)', {
  factory: () => 3000,
});

/**
 * Splits a text/event-stream buffer into complete messages; `rest` is the unfinished tail to keep
 * for the next chunk. Comments (keep-alives) and unnamed events are ignored by the caller.
 */
export function parseSse(buffer: string): { messages: SseMessage[]; rest: string } {
  const normalized = buffer.replace(/\r\n?/g, '\n');
  const blocks = normalized.split('\n\n');
  const rest = blocks.pop() ?? '';
  const messages: SseMessage[] = [];
  for (const block of blocks) {
    let event = 'message';
    const data: string[] = [];
    for (const line of block.split('\n')) {
      if (line.startsWith(':')) {
        continue;
      }
      const colon = line.indexOf(':');
      const field = colon === -1 ? line : line.slice(0, colon);
      const value = colon === -1 ? '' : line.slice(colon + 1).replace(/^ /, '');
      if (field === 'event') {
        event = value;
      } else if (field === 'data') {
        data.push(value);
      }
    }
    if (data.length) {
      messages.push({ event, data: data.join('\n') });
    }
  }
  return { messages, rest };
}

/**
 * Live analysis status (Story 3.4, ADR-7). EventSource cannot send the Authorization header and a
 * token in the URL would leak into logs, so the stream is read with fetch. If it cannot open or
 * ends early, polling every 3 s takes over.
 */
@Injectable({ providedIn: 'root' })
export class StatusStream {
  private readonly auth = inject(AuthService);
  private readonly http = inject(HttpClient);
  private readonly pollMs = inject(POLL_MS);

  /** Status events of one contract: the stream while it lasts, then polling. Never errors. */
  watch(contractUrl: string): Observable<StatusEvent> {
    return this.stream(`${contractUrl}/events`).pipe(catchError(() => this.poll(contractUrl)));
  }

  /** Raw stream; errors when it cannot open, completes when the server closes it. */
  stream(url: string): Observable<StatusEvent> {
    return new Observable<StatusEvent>((subscriber) => {
      const abort = new AbortController();
      (async () => {
        const response = await fetch(url, {
          headers: {
            Accept: 'text/event-stream',
            Authorization: `Bearer ${this.auth.accessToken()}`,
          },
          signal: abort.signal,
        });
        if (!response.ok || !response.body) {
          throw new Error(`status stream: HTTP ${response.status}`);
        }
        const reader = response.body.pipeThrough(new TextDecoderStream()).getReader();
        let buffer = '';
        for (;;) {
          const { value, done } = await reader.read();
          if (done) {
            break;
          }
          const parsed = parseSse(buffer + value);
          buffer = parsed.rest;
          parsed.messages
            .filter((m) => m.event === 'status')
            .forEach((m) => subscriber.next(JSON.parse(m.data) as StatusEvent));
        }
        // The server closed it (timeout, restart): keep the page live by polling.
        throw new Error('status stream ended');
      })().catch((error: unknown) => {
        if (!abort.signal.aborted) {
          subscriber.error(error);
        }
      });
      return () => abort.abort();
    });
  }

  private poll(contractUrl: string): Observable<StatusEvent> {
    return timer(0, this.pollMs).pipe(
      // exhaustMap: a slow poll is not cancelled by the next tick; ticks wait for it instead.
      exhaustMap(() => this.http.get<ContractSummary>(contractUrl)),
      map((c) => c.analysis),
      // A contract always has an analysis once uploaded; skip the impossible case quietly.
      switchMap((a) => (a ? [{ analysisId: a.id, status: a.status }] : [])),
    );
  }
}
