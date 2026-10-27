import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../environments/environment';
import { AnalysisStatus, RiskLevel } from '../contracts/contracts-api';

/** One analysis request in the dead-letter topic; never includes the contract text. */
export interface DeadLetter {
  partition: number;
  offset: number;
  failedAt: string;
  analysisId: string | null;
  contractId: string | null;
  language: string | null;
  error: string;
  /** Current status, null once the contract was deleted. */
  status: AnalysisStatus | null;
}

/** GET /api/v1/admin/stats (Story 5.2): counts only, last 14 Casablanca days. */
export interface StatsOverview {
  from: string;
  to: string;
  /** Oldest first, every day present. */
  days: { day: string; statuses: Record<AnalysisStatus, number> }[];
  statuses: Record<AnalysisStatus, number>;
  /** FAILED over finished analyses in the window; null when none finished. */
  failureRate: number | null;
  risks: Record<RiskLevel, number>;
  today: {
    analyses: number;
    cap: number;
    remaining: number;
    inputTokens: number;
    outputTokens: number;
    spentUsd: number;
  };
}

/** Admin endpoints (role admin, enforced by the API). */
@Injectable({ providedIn: 'root' })
export class AdminApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/v1/admin`;
  readonly statsUrl = `${this.base}/stats`;

  deadLetters(): Observable<DeadLetter[]> {
    return this.http.get<DeadLetter[]>(`${this.base}/dead-letters`);
  }

  retry(analysisId: string): Observable<void> {
    return this.http
      .post(`${this.base}/analyses/${encodeURIComponent(analysisId)}/retry`, null)
      .pipe(map(() => undefined));
  }
}
