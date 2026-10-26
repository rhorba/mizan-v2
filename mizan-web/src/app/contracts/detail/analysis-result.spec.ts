import { TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { translocoTesting } from '../../../testing/transloco-testing';
import { LanguageService } from '../../i18n/language';
import { AnalysisResult, ClauseFlag } from '../contracts-api';
import { AnalysisResultView } from './analysis-result';

const flag = (
  position: number,
  riskLevel: ClauseFlag['riskLevel'],
  suggestedCorrection: string | null = 'Plafonner la pénalité.',
): ClauseFlag => ({
  position,
  clauseText: `Clause ${position}`,
  riskLevel,
  explanation: `Pourquoi ${position}`,
  suggestedCorrection,
});

const result = (overrides: Partial<AnalysisResult> = {}): AnalysisResult => ({
  id: 'a-1',
  language: 'fr',
  status: 'COMPLETE',
  summary: 'Contrat de prestation entre A et B.',
  failureReason: null,
  requestedAt: '',
  completedAt: '',
  // Model order: LOW, HIGH, MEDIUM, HIGH.
  flags: [flag(0, 'LOW', null), flag(1, 'HIGH'), flag(2, 'MEDIUM'), flag(3, 'HIGH')],
  ...overrides,
});

describe('AnalysisResultView', () => {
  const snackBar = { open: vi.fn() };
  const writeText = vi.fn();

  beforeEach(() => {
    snackBar.open.mockClear();
    writeText.mockReset();
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });
    TestBed.configureTestingModule({
      imports: [AnalysisResultView, translocoTesting()],
      providers: [{ provide: MatSnackBar, useValue: snackBar }],
    });
    TestBed.inject(LanguageService).use('fr');
  });

  function render(value: AnalysisResult) {
    const fixture = TestBed.createComponent(AnalysisResultView);
    fixture.componentRef.setInput('result', value);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const titles = () =>
      Array.from(el.querySelectorAll('.clause-title')).map((t) => t.textContent?.trim());
    const chips = () =>
      Array.from(el.querySelectorAll('mat-chip-option')).map((c) => c.textContent?.trim());
    return { fixture, el, titles, chips };
  }

  it('shows the summary, the notice, and flags sorted HIGH → LOW keeping model order', () => {
    const { el, titles } = render(result());
    expect(el.querySelector('.summary p')?.textContent).toContain('Contrat de prestation');
    expect(el.querySelector('[role=note]')?.textContent).toContain('pas un avis juridique');
    expect(titles()).toEqual(['Clause 1', 'Clause 3', 'Clause 2', 'Clause 0']);
    expect(el.querySelector('.risk.high')?.textContent).toContain('Élevé');
  });

  it('offers filter chips with counts, only for levels present, and filters the list', () => {
    const { fixture, titles, chips } = render(
      result({ flags: [flag(0, 'HIGH'), flag(1, 'HIGH'), flag(2, 'LOW')] }),
    );
    expect(chips()).toEqual(['Toutes (3)', 'Élevé (2)', 'Faible (1)']);

    fixture.componentInstance.select('LOW');
    fixture.detectChanges();
    expect(titles()).toEqual(['Clause 2']);

    fixture.componentInstance.select(undefined); // deselecting the chip
    fixture.detectChanges();
    expect(titles()).toHaveLength(3);
  });

  it('reads Arabic and Darija results right to left, French left to right', () => {
    expect(
      render(result({ language: 'ary' }))
        .el.querySelector('.result')
        ?.getAttribute('dir'),
    ).toBe('rtl');
    const fr = render(result()).el.querySelector('.result');
    expect(fr?.getAttribute('dir')).toBe('ltr');
    expect(fr?.getAttribute('lang')).toBe('fr');
  });

  it('copies the suggested wording, and says so when the clipboard refuses', async () => {
    const { fixture } = render(result());
    const high = fixture.componentInstance.sorted()[0];

    writeText.mockResolvedValue(undefined);
    await fixture.componentInstance.copy(high);
    expect(writeText).toHaveBeenCalledWith('Plafonner la pénalité.');
    expect(snackBar.open).toHaveBeenLastCalledWith('Formulation copiée.', undefined, {
      duration: 3000,
    });

    writeText.mockRejectedValue(new Error('denied'));
    await fixture.componentInstance.copy(high);
    expect(snackBar.open).toHaveBeenLastCalledWith(
      'Copie impossible ; sélectionnez le texte à la main.',
      undefined,
      { duration: 3000 },
    );

    await fixture.componentInstance.copy(flag(9, 'LOW', null));
    expect(writeText).toHaveBeenCalledTimes(2);
  });

  it('expands a flag to show clause, reason and suggestion with a Copy button', async () => {
    const { fixture, el } = render(result({ flags: [flag(1, 'HIGH')] }));
    el.querySelector<HTMLElement>('mat-expansion-panel-header')!.click();
    fixture.detectChanges();
    await fixture.whenStable();
    const details = el.querySelector('.details')?.textContent ?? '';
    expect(details).toContain('Clause 1');
    expect(details).toContain('Pourquoi 1');
    expect(details).toContain('Plafonner la pénalité.');
    expect(el.querySelector('.suggestion button')?.textContent).toContain('Copier');
  });

  it('says when no clause was flagged, without filters', () => {
    const { el } = render(result({ flags: [] }));
    expect(el.querySelector('.no-flags')?.textContent).toContain('Aucune clause');
    expect(el.querySelector('mat-chip-listbox')).toBeNull();
  });
});
