import { expect, test } from '@playwright/test';
import { eicar, fakePdf, textPdf } from './support/files';
import { API_URL, signUpNewUser } from './support/session';

/** E2E_PAUSE (ms) holds each step on screen for the sprint recording; 0 in CI. */
const PAUSE = Number(process.env['E2E_PAUSE'] ?? 0);
/** Set when the stack runs the "av" profile (ClamAV), as in CI. */
const AV = process.env['E2E_AV'] === '1';

/**
 * Stories 2.1, 2.2 and 4.2:
 *   refused uploads keep the selection and explain why; a text PDF in Darija lands on its page
 *   as PENDING; it appears in the history; deleting it (after confirmation) removes it for good.
 */
test('upload is validated, lands on its page, shows in the history, and can be deleted', async ({
  page,
  request,
}) => {
  const step = async (title: string, body: () => Promise<void>) => {
    await test.step(title, body);
    if (PAUSE) {
      await page.waitForTimeout(PAUSE);
    }
  };
  const { authorization } = await signUpNewUser(page, request);
  const error = page.locator('#upload-error');
  let contractId = '';

  await step('empty history invites to analyse a contract', async () => {
    await expect(page.getByText("Aucun contrat pour l'instant.")).toBeVisible();
    await page.getByRole('link', { name: 'Analyser un contrat' }).click();
    await expect(page).toHaveURL('http://localhost:4200/contracts/new');
  });

  await step('a file that only pretends to be a PDF is refused by the API and kept', async () => {
    await page.locator('input[type=file]').setInputFiles(fakePdf('notes.pdf'));
    await page.getByRole('button', { name: 'Analyser' }).click();
    await expect(error).toHaveText("Ce fichier n'est pas un PDF.");
    await expect(page.locator('.chosen')).toHaveText('notes.pdf');
  });

  if (AV) {
    await step('ClamAV blocks the EICAR test file', async () => {
      await page.locator('input[type=file]').setInputFiles(eicar('facture.pdf'));
      await page.getByRole('button', { name: 'Analyser' }).click();
      await expect(error).toHaveText("L'analyse antivirus a bloqué ce fichier.");
    });
  }

  await step('a text PDF analysed in Darija lands on its page, pending', async () => {
    await page
      .locator('input[type=file]')
      .setInputFiles(
        textPdf('Contrat de prestation.pdf', 'Article 1. Le client paie sous 30 jours.'),
      );
    await page.getByRole('combobox').click();
    await page.getByRole('option', { name: 'Darija' }).click();
    await page.getByRole('button', { name: 'Analyser' }).click();
    await expect(page).toHaveURL(/\/contracts\/[0-9a-f-]{36}$/);
    contractId = page.url().split('/').pop()!;
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Contrat de prestation.pdf');
    await expect(page.locator('.meta')).toContainText('Darija');
    await expect(page.locator('app-status-chip')).toHaveText('En attente');
  });

  await step('the history lists it with its status', async () => {
    await page.getByRole('link', { name: 'Mes contrats' }).first().click();
    const row = page.locator('.history .row').filter({ hasText: 'Contrat de prestation.pdf' });
    await expect(row).toHaveCount(1);
    await expect(row.locator('app-status-chip')).toHaveText('En attente');
  });

  await step('deleting asks for confirmation, then the contract is gone', async () => {
    await page.getByRole('button', { name: 'Supprimer Contrat de prestation.pdf' }).click();
    const dialog = page.getByRole('dialog');
    await expect(dialog).toContainText('toutes ses analyses');
    await dialog.getByRole('button', { name: 'Supprimer' }).click();
    await expect(page.getByText('Contrat supprimé.')).toBeVisible();
    await expect(page.getByText("Aucun contrat pour l'instant.")).toBeVisible();
  });

  await step('the API now answers 404 for that contract', async () => {
    const gone = await request.get(`${API_URL}/api/v1/contracts/${contractId}`, {
      headers: { Authorization: authorization },
    });
    expect(gone.status()).toBe(404);
    expect((await gone.json()).type).toBe('urn:mizan:problem:contract-not-found');
  });
});
