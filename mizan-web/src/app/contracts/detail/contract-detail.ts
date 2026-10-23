import { httpResource } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { ContractSummary, ContractsApi, problemSlug } from '../contracts-api';
import { StatusChip } from '../status-chip';

/** The contract page. Shows status until Story 4.1 adds the analysis result. */
@Component({
  selector: 'app-contract-detail',
  imports: [MatButtonModule, RouterLink, StatusChip, TranslocoPipe],
  templateUrl: './contract-detail.html',
  styleUrl: './contract-detail.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ContractDetail {
  private readonly api = inject(ContractsApi);

  /** Bound from the :id route parameter (withComponentInputBinding). */
  readonly id = input.required<string>();
  readonly contract = httpResource<ContractSummary>(() => this.api.url(this.id()));
  readonly notFound = computed(() => problemSlug(this.contract.error()) === 'contract-not-found');
}
