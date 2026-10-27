import { expect, test } from '@playwright/test';
import { expectNoAxeViolations } from './support/axe';
import { textPdf } from './support/files';
import { grantAdmin } from './support/keycloak';
import { signInAgain, signUpNewUser } from './support/session';

/** E2E_PAUSE (ms) holds each step on screen for the sprint recording; 0 in CI. */
const PAUSE = Number(process.env['E2E_PAUSE'] ?? 0);

const CONTRACT =
  'CONTRAT DE PRESTATION. Le Prestataire realise un site web pour 20 000 MAD. ' +
  'Paiement a 120 jours. Penalite de 5 % du prix par jour de retard, sans plafond. ' +
  'Le Client peut resilier a tout moment sans preavis ni indemnite.';

/**
 * Sprint 4 against the real stack:
 *   Stories 3.4, 4.1, 4.3 — the contract page follows the analysis live (no reload), shows the
 *   result, and a re-analysis in Arabic adds a second tab. Two real model calls (a few cents on
 *   Opus), so it only runs with E2E_CLAUDE=1 and a real key in .env; CI never calls Claude.
 *   Story 5.2 and the accessibility audit (WCAG 2.2 AA, axe) run everywhere.
 */
test('the analysis is followed live, its result is shown, and Arabic adds a tab', async ({
  page,
  request,
}) => {
  test.skip(!process.env['E2E_CLAUDE'], 'opt-in: E2E_CLAUDE=1 with ANTHROPIC_API_KEY in .env');
  test.setTimeout(420_000);
  const step = async (title: string, body: () => Promise<void>) => {
    await test.step(title, body);
    if (PAUSE) {
      await page.waitForTimeout(PAUSE);
    }
  };
  await signUpNewUser(page, request);
  const current = page.locator('[aria-current=step]');
  const result = page.locator('app-analysis-result .result');

  await step('upload a French contract and watch it move without reloading', async () => {
    await page.getByRole('link', { name: 'Analyser un contrat' }).click();
    await page.locator('input[type=file]').setInputFiles(textPdf('Prestation web.pdf', CONTRACT));
    await page.getByRole('button', { name: 'Analyser' }).click();
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Prestation web.pdf');
    // Story 3.4: the stepper reaches "Terminée" through the live stream alone.
    await expect(current).toHaveText(/Terminée/, { timeout: 180_000 });
  });

  await step('the result: summary, the not-legal-advice note, and risky clauses', async () => {
    await expect(page.locator('app-analysis-result .summary p').first()).not.toBeEmpty();
    await expect(page.getByRole('note')).toContainText('pas un avis juridique');
    await expect(page.locator('mat-expansion-panel').first()).toBeVisible();
    await page.locator('mat-expansion-panel-header').first().click();
    await expect(page.locator('.details').first()).toBeVisible();
    await expectNoAxeViolations(page, 'result (fr)');
  });

  await step('analyze it again in Arabic: a second tab follows the new analysis', async () => {
    await page.getByRole('button', { name: 'Analyser dans une autre langue' }).click();
    await page.getByRole('menuitem', { name: 'Analyser en arabe' }).click();
    const tabs = page.getByRole('tab');
    await expect(tabs).toHaveCount(2);
    await expect(tabs.nth(1)).toHaveAttribute('aria-selected', 'true');
    // Nothing else may start while it runs.
    await expect(page.getByRole('button', { name: 'Analyser dans une autre langue' })).toHaveCount(
      0,
    );
    await expect(current).toHaveText(/Terminée/, { timeout: 180_000 });
    await expect(result).toHaveAttribute('dir', 'rtl');
    await expectNoAxeViolations(page, 'result (ar tab)');
  });

  await step('the French tab still holds the first result', async () => {
    await page.getByRole('tab', { name: 'Français' }).click();
    // Material keeps the leaving tab body during its animation; wait until only one is left.
    await expect(result).toHaveCount(1);
    await expect(result).toHaveAttribute('dir', 'ltr');
  });

  await step('the history lists it as complete', async () => {
    await page.getByRole('link', { name: 'Mes contrats' }).first().click();
    const row = page.locator('.history .row', { hasText: 'Prestation web.pdf' });
    await expect(row.locator('app-status-chip')).toHaveText(/Terminée/);
  });
});

test('pages of this sprint pass the accessibility audit; admins get the statistics', async ({
  page,
  request,
}) => {
  const step = test.step.bind(test);
  const { email } = await signUpNewUser(page, request);

  await step('history and upload', async () => {
    await expectNoAxeViolations(page, 'history (empty)');
    await page.getByRole('link', { name: 'Analyser un contrat' }).click();
    await expectNoAxeViolations(page, 'upload');
  });

  await step('a contract page while its analysis waits', async () => {
    await page.locator('input[type=file]').setInputFiles(textPdf('Bail.pdf', CONTRACT));
    await page.getByRole('button', { name: 'Analyser' }).click();
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Bail.pdf');
    await expect(page.locator('app-analysis-stepper')).toBeVisible();
    await expectNoAxeViolations(page, 'contract (pending)');
  });

  await step('the admin statistics, in French and in Arabic', async () => {
    await grantAdmin(request, email);
    await signInAgain(page, email);
    await page.getByRole('link', { name: 'Administration' }).click();
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Statistiques');
    await expect(page.locator('svg .hit')).toHaveCount(14);
    // Today's upload counts against the global cap.
    await expect(page.getByRole('meter')).not.toHaveAttribute('aria-valuenow', '0');
    await expectNoAxeViolations(page, 'admin statistics (fr)');

    await page.locator('svg .hit').last().focus();
    await expect(page.locator('.tooltip')).toBeVisible();
    await page.locator('details.table-view summary').click();
    await expect(page.locator('details.table-view tbody tr')).toHaveCount(14);

    await page.locator('.lang-switch').click();
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('الإحصائيات');
    await expectNoAxeViolations(page, 'admin statistics (ar)');
  });
});
