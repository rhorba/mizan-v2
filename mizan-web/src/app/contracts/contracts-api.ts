import { HttpClient, HttpErrorResponse, HttpEvent, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export type AnalysisLanguage = 'fr' | 'ar' | 'ary';
export type AnalysisStatus = 'PENDING' | 'ANALYZING' | 'COMPLETE' | 'FAILED';

export interface AnalysisSummary {
  id: string;
  language: AnalysisLanguage;
  status: AnalysisStatus;
  requestedAt: string;
}

export interface ContractSummary {
  id: string;
  fileName: string;
  pageCount: number;
  createdAt: string;
  analysis: AnalysisSummary | null;
}

export interface ContractPage {
  items: ContractSummary[];
  nextCursor: string | null;
}

/** Mirrors the API's limits (FR-2) so obvious mistakes are caught before uploading. */
export const UPLOAD_LIMITS = { maxBytes: 10 * 1024 * 1024, maxPages: 30 } as const;

/** Problem slugs the API returns as urn:mizan:problem:{slug} (ADR-10). */
export const PROBLEM_SLUGS = [
  'not-a-pdf',
  'too-large',
  'too-many-pages',
  'no-text-layer',
  'encrypted',
  'unreadable',
  'bad-language',
  'malware-detected',
  'scanner-unavailable',
  'rate-limited',
  'budget-exceeded',
  'quota-exceeded',
  'global-quota-exceeded',
  'contract-not-found',
] as const;
export type ProblemSlug = (typeof PROBLEM_SLUGS)[number];

/** Slug of an RFC 9457 error, or null when the body is not one of ours. */
export function problemSlug(error: unknown): ProblemSlug | null {
  if (!(error instanceof HttpErrorResponse)) {
    return null;
  }
  const type = (error.error as { type?: unknown } | null)?.type;
  const slug = typeof type === 'string' ? type.replace('urn:mizan:problem:', '') : '';
  return PROBLEM_SLUGS.find((s) => s === slug) ?? null;
}

@Injectable({ providedIn: 'root' })
export class ContractsApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/v1/contracts`;

  /** Emits upload progress events, then the 202 response. */
  upload(file: File, language: AnalysisLanguage): Observable<HttpEvent<ContractSummary>> {
    const form = new FormData();
    form.append('file', file, file.name);
    form.append('language', language);
    return this.http.post<ContractSummary>(this.base, form, {
      reportProgress: true,
      observe: 'events',
    });
  }

  history(cursor: string | null): Observable<ContractPage> {
    const params = cursor ? new HttpParams().set('cursor', cursor) : undefined;
    return this.http.get<ContractPage>(this.base, { params });
  }

  url(id: string): string {
    return `${this.base}/${encodeURIComponent(id)}`;
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(this.url(id));
  }
}
