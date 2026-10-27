import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { translocoTesting } from '../../testing/transloco-testing';
import { LanguageService } from '../i18n/language';
import { StatsOverview } from './admin-api';
import { AdminStats } from './admin-stats';
import { SERIES, niceMax, roundedTop } from './daily-chart';

const URL = 'http://localhost:8080/api/v1/admin/stats';

function overview(): StatsOverview {
  const zero = { PENDING: 0, ANALYZING: 0, COMPLETE: 0, FAILED: 0 };
  const days = Array.from({ length: 14 }, (_, i) => ({
    day: `2026-10-${String(14 + i).padStart(2, '0')}`,
    statuses: { ...zero },
  }));
  days[0].statuses = { ...zero, COMPLETE: 3, FAILED: 1 };
  days[13].statuses = { PENDING: 1, ANALYZING: 1, COMPLETE: 4, FAILED: 2 };
  return {
    from: '2026-10-14',
    to: '2026-10-27',
    days,
    statuses: { PENDING: 1, ANALYZING: 1, COMPLETE: 7, FAILED: 3 },
    failureRate: 0.3,
    risks: { HIGH: 6, MEDIUM: 3, LOW: 0 },
    today: {
      analyses: 17,
      cap: 20,
      remaining: 3,
      inputTokens: 125_000,
      outputTokens: 48_000,
      spentUsd: 1.46,
    },
  };
}

describe('niceMax', () => {
  it('rounds up to four clean steps and never below 4', () => {
    expect(niceMax(0)).toEqual({ max: 4, step: 1 });
    expect(niceMax(7)).toEqual({ max: 8, step: 2 });
    expect(niceMax(17)).toEqual({ max: 20, step: 5 });
    expect(niceMax(41)).toEqual({ max: 80, step: 20 });
  });
});

describe('roundedTop', () => {
  it('rounds the data end and keeps the base square', () => {
    expect(roundedTop(10, 20, 24, 50, 4)).toBe('M10,70V24Q10,20 14,20H30Q34,20 34,24V70Z');
  });

  it('never rounds more than the bar is tall', () => {
    expect(roundedTop(0, 0, 24, 2, 4)).toBe('M0,2V2Q0,0 2,0H22Q24,0 24,2V2Z');
  });
});

describe('AdminStats', () => {
  async function render(language: 'fr' | 'ar' = 'fr') {
    await TestBed.configureTestingModule({
      imports: [AdminStats, translocoTesting()],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    TestBed.inject(LanguageService).use(language);
    const fixture = TestBed.createComponent(AdminStats);
    fixture.detectChanges();
    return {
      fixture,
      el: fixture.nativeElement as HTMLElement,
      http: TestBed.inject(HttpTestingController),
    };
  }

  async function loaded(data = overview()) {
    const r = await render();
    r.http.expectOne(URL).flush(data);
    await r.fixture.whenStable();
    return r;
  }

  const text = (el: Element | null) => el?.textContent?.replace(/\s+/g, ' ').trim();

  it('shows today against the global cap, tokens, spend and the failure rate', async () => {
    const { el } = await loaded();
    const tiles = [...el.querySelectorAll('.tile')].map(text);
    expect(tiles[0]).toContain('17 / 20');
    expect(tiles[0]).toContain('3 restante(s)');
    expect(tiles[1]).toMatch(/173\s?k/i);
    expect(tiles[1]).toMatch(/Entrée 125\s?k · sortie 48\s?k/i);
    expect(tiles[2]).toMatch(/1,46\s?\$|\$\s?1,46|1,46\s?US\$/);
    expect(tiles[3]).toMatch(/30\s?%/);

    const meter = el.querySelector('[role=meter]')!;
    expect(meter.getAttribute('aria-valuenow')).toBe('17');
    expect(meter.getAttribute('aria-valuemax')).toBe('20');
    // 85 % of the cap: the meter turns to warning.
    expect(meter.classList).toContain('warn');
    expect(meter.querySelector<HTMLElement>('.fill')!.style.inlineSize).toBe('85%');
  });

  it('a window with nothing finished shows no failure rate', async () => {
    const { el } = await loaded({ ...overview(), failureRate: null });
    expect(text(el.querySelectorAll('.tile')[3].querySelector('.value'))).toBe('—');
  });

  it('draws 14 day columns stacked done → in progress → failed, with a legend of totals', async () => {
    const { el } = await loaded();
    expect(el.querySelectorAll('.hit')).toHaveLength(14);
    expect([...el.querySelectorAll('.legend li')].map(text)).toEqual([
      'Terminées 7',
      'En cours 2',
      'Échouées 3',
    ]);

    const today = el.querySelectorAll('svg g')[13];
    const marks = [...today.querySelectorAll('.mark')];
    expect(marks.map((m) => SERIES.find((k) => m.classList.contains(k)))).toEqual([
      'done',
      'running',
      'failed',
    ]);
    // Only the top segment carries the rounded data end.
    expect(marks.map((m) => m.tagName.toLowerCase())).toEqual(['rect', 'rect', 'path']);
    // A 2px surface gap between stacked segments: each segment ends 2px below the next one.
    const done = marks[0];
    const running = marks[1];
    const runningBottom =
      Number(running.getAttribute('y')) + Number(running.getAttribute('height'));
    expect(Number(done.getAttribute('y')) - runningBottom).toBeCloseTo(2);
    // An empty day draws nothing.
    expect(el.querySelectorAll('svg g')[5].querySelectorAll('.mark')).toHaveLength(0);
    // Every other day is labelled, ending on today.
    const dayLabels = [...el.querySelectorAll('svg text.tick')].filter((t) =>
      t.textContent?.includes('/'),
    );
    expect(dayLabels).toHaveLength(7);
    expect(text(dayLabels.at(-1)!)).toBe('27/10');
  });

  it('a hovered or focused day shows its counts and dims the others', async () => {
    const { fixture, el } = await loaded();
    const hits = el.querySelectorAll<SVGRectElement>('.hit');

    hits[13].dispatchEvent(new MouseEvent('mouseenter'));
    fixture.detectChanges();
    const tooltip = text(el.querySelector('.tooltip'));
    expect(tooltip).toContain('27 octobre');
    expect(tooltip).toContain('Terminées 4');
    expect(tooltip).toContain('En cours 2');
    expect(tooltip).toContain('Échouées 2');
    expect(el.querySelector('.tooltip')!.classList).toContain('flip'); // near the end edge
    expect(el.querySelectorAll('svg g.dim')).toHaveLength(13);

    el.querySelector('.plot')!.dispatchEvent(new MouseEvent('mouseleave'));
    fixture.detectChanges();
    expect(el.querySelector('.tooltip')).toBeNull();

    // Keyboard: the first day (index 0) works too.
    hits[0].dispatchEvent(new FocusEvent('focus'));
    fixture.detectChanges();
    expect(text(el.querySelector('.tooltip'))).toContain('Terminées 3');
    expect(hits[0].getAttribute('aria-label')).toContain('terminées 3, en cours 0, échouées 1');
    // Children of role=img are hidden from assistive tech: the chart is a group of labelled days.
    expect(el.querySelector('svg')!.getAttribute('role')).toBe('group');
    expect(hits[0].getAttribute('role')).toBe('img');
    expect(el.querySelector('svg g')!.getAttribute('aria-hidden')).toBe('true');
    hits[0].dispatchEvent(new FocusEvent('blur'));
    fixture.detectChanges();
    expect(el.querySelector('.tooltip')).toBeNull();
  });

  it('offers every value as a table', async () => {
    const { el } = await loaded();
    const rows = el.querySelectorAll('.table-view tbody tr');
    expect(rows).toHaveLength(14);
    expect([...rows[13].querySelectorAll('td')].map(text)).toEqual(['4', '2', '2']);
  });

  it('splits flags by risk in words, one hue, widths relative to the largest', async () => {
    const { el } = await loaded();
    const rows = [...el.querySelectorAll('.bars li')];
    expect(rows.map((r) => text(r.querySelector('.bar-label')))).toEqual([
      'Élevé',
      'Moyen',
      'Faible',
    ]);
    expect(
      rows.map((r) => r.querySelector<HTMLElement>('.bar')!.style.getPropertyValue('--share')),
    ).toEqual(['1', '0.5', '0']);
    expect(rows.map((r) => text(r.querySelector('.bar-value')))).toEqual(['6', '3', '0']);
  });

  it('keeps the time axis left to right in Arabic', async () => {
    const { fixture, el, http } = await render('ar');
    http.expectOne(URL).flush(overview());
    await fixture.whenStable();
    expect(el.querySelector('svg')!.getAttribute('dir')).toBe('ltr');
    expect(text(el.querySelector('h1'))).toBe('الإحصائيات');
  });

  it('says when the statistics cannot be loaded and retries', async () => {
    const { fixture, el, http } = await render();
    expect(el.querySelector('.loading')).not.toBeNull();
    http.expectOne(URL).flush(null, { status: 500, statusText: 'Boom' });
    await fixture.whenStable();
    expect(text(el.querySelector('[role=alert]'))).toContain('Impossible de charger');

    el.querySelector<HTMLButtonElement>('.error + button')!.click();
    fixture.detectChanges();
    http.expectOne(URL).flush(overview());
    await fixture.whenStable();
    expect(el.querySelectorAll('.tile')).toHaveLength(4);
  });
});
