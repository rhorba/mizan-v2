import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../environments/environment';
import { AnalysisStatus } from '../contracts/contracts-api';

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

/** Admin dead-letter endpoints (role admin, enforced by the API). */
@Injectable({ providedIn: 'root' })
export class AdminApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/v1/admin`;

  deadLetters(): Observable<DeadLetter[]> {
    return this.http.get<DeadLetter[]>(`${this.base}/dead-letters`);
  }

  retry(analysisId: string): Observable<void> {
    return this.http
      .post(`${this.base}/analyses/${encodeURIComponent(analysisId)}/retry`, null)
      .pipe(map(() => undefined));
  }
}
