import { httpResource } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { TranslocoPipe } from '@jsverse/transloco';
import { environment } from '../../environments/environment';
import { LanguageService } from '../i18n/language';

export interface Quota {
  limit: number;
  used: number;
  remaining: number;
}

/** History (empty until Story 2.1) and today's quota. */
@Component({
  selector: 'app-contracts',
  imports: [MatButtonModule, MatChipsModule, TranslocoPipe],
  templateUrl: './contracts.html',
  styleUrl: './contracts.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Contracts {
  private readonly language = inject(LanguageService);

  readonly quota = httpResource<Quota>(() => `${environment.apiUrl}/api/v1/me/quota`);

  /** CLDR plural category: Arabic has zero/one/two/few/many/other, French one/other. */
  readonly quotaKey = computed(() => {
    const remaining = this.quota.value()?.remaining ?? 0;
    const category = new Intl.PluralRules(this.language.current()).select(remaining);
    return `contracts.quota.${category}`;
  });
}
