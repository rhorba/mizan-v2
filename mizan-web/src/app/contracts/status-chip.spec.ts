import { TestBed } from '@angular/core/testing';
import { translocoTesting } from '../../testing/transloco-testing';
import { AnalysisStatus } from './contracts-api';
import { StatusChip } from './status-chip';

describe('StatusChip', () => {
  it.each([
    ['PENDING', 'En attente'],
    ['ANALYZING', 'Analyse en cours'],
    ['COMPLETE', 'Terminée'],
    ['FAILED', 'Échec'],
  ] as [AnalysisStatus, string][])(
    '%s reads "%s" (words, not colour alone)',
    async (status, label) => {
      await TestBed.configureTestingModule({
        imports: [StatusChip, translocoTesting()],
      }).compileComponents();
      const fixture = TestBed.createComponent(StatusChip);
      fixture.componentRef.setInput('status', status);
      await fixture.whenStable();
      const chip = (fixture.nativeElement as HTMLElement).querySelector('.chip')!;
      expect(chip.textContent?.trim()).toBe(label);
      expect(chip.classList).toContain(status.toLowerCase());
    },
  );
});
