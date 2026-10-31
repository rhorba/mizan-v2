import { expect, test } from '@playwright/test';
import { textPdf } from '../e2e/support/files';

const PAUSE = Number(process.env['E2E_PAUSE'] ?? 0);
const EMAIL = process.env['E2E_PUBLIC_EMAIL'] ?? '';
const PASSWORD = process.env['E2E_PUBLIC_PASSWORD'] ?? '';

const CONTRACT =
  'CONTRAT DE PRESTATION. Le Prestataire realise un site web pour 20 000 MAD. ' +
  'Paiement a 120 jours. Penalite de 5 % du prix par jour de retard, sans plafond. ' +
  'Le Client peut resilier a tout moment sans preavis ni indemnite.';

/** One real model call on preprod. The contract is left in place for the restore test. */
test('a user signs in, uploads a contract and reads the analysis', async ({ page }) => {
  test.skip(!EMAIL || !PASSWORD, 'needs E2E_PUBLIC_EMAIL and E2E_PUBLIC_PASSWORD');
  const name = `Prestation web ${Date.now()}.pdf`;
  const step = async (title: string, body: () => Promise<void>) => {
    await test.step(title, body);
    if (PAUSE) {
      await page.waitForTimeout(PAUSE);
    }
  };

  await step('sign in over HTTPS through Keycloak', async () => {
    await page.goto('/');
    await page.getByRole('button', { name: 'Se connecter' }).click();
    await page.fill('#username', EMAIL);
    await page.fill('#password', PASSWORD);
    await page.locator('[type=submit]').click();
    await expect(page).toHaveURL(/\/contracts$/);
  });

  await step('upload a contract and follow it live', async () => {
    await page.getByRole('link', { name: 'Analyser un contrat' }).click();
    await page.locator('input[type=file]').setInputFiles(textPdf(name, CONTRACT));
    await page.getByRole('button', { name: 'Analyser' }).click();
    await expect(page.getByRole('heading', { level: 1 })).toHaveText(name);
    await expect(page.locator('[aria-current=step]')).toHaveText(/Terminée/, { timeout: 240_000 });
  });

  await step('read the result', async () => {
    await expect(page.locator('app-analysis-result .summary p').first()).not.toBeEmpty();
    await expect(page.getByRole('note')).toContainText('pas un avis juridique');
    await page.locator('mat-expansion-panel-header').first().click();
    await expect(page.locator('.details').first()).toBeVisible();
  });

  await step('the history lists it as complete', async () => {
    await page.getByRole('link', { name: 'Mes contrats' }).first().click();
    const row = page.locator('.history .row', { hasText: name });
    await expect(row.locator('app-status-chip')).toHaveText(/Terminée/);
  });
});
