import { TestBed } from '@angular/core/testing';
import { translocoTesting } from '../../../testing/transloco-testing';
import { LanguageService } from '../../i18n/language';
import { AnalysisStatus } from '../contracts-api';
import { AnalysisStepper } from './analysis-stepper';

describe('AnalysisStepper', () => {
  function render(status: AnalysisStatus, lang: 'fr' | 'ar' = 'fr') {
    TestBed.configureTestingModule({ imports: [AnalysisStepper, translocoTesting()] });
    TestBed.inject(LanguageService).use(lang);
    const fixture = TestBed.createComponent(AnalysisStepper);
    fixture.componentRef.setInput('status', status);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const steps = Array.from(el.querySelectorAll('li')).map((li) => ({
      name: li.querySelector('.name')?.textContent?.trim(),
      state: ['done', 'current', 'todo'].find((s) => li.classList.contains(s)),
      current: li.getAttribute('aria-current'),
    }));
    return { el, steps };
  }

  it.each<[AnalysisStatus, string[], string]>([
    ['PENDING', ['current', 'todo', 'todo'], 'Terminée'],
    ['ANALYZING', ['done', 'current', 'todo'], 'Terminée'],
    ['COMPLETE', ['done', 'done', 'current'], 'Terminée'],
    ['FAILED', ['done', 'done', 'current'], 'Échec'],
  ])('%s marks the steps %j and names the last one "%s"', (status, states, last) => {
    const { steps } = render(status);
    expect(steps.map((s) => s.state)).toEqual(states);
    expect(steps.filter((s) => s.current === 'step')).toHaveLength(1);
    expect(steps[2].name).toBe(last);
  });

  it('is an ordered list with a label, announced politely, in Arabic too', () => {
    const { el, steps } = render('ANALYZING', 'ar');
    const list = el.querySelector('ol');
    expect(list?.getAttribute('aria-label')).toBe('تقدّم التحليل');
    expect(list?.getAttribute('aria-live')).toBe('polite');
    expect(steps[1].name).toBe('جارٍ التحليل');
  });
});
