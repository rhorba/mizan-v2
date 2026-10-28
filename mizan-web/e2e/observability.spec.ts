import { expect, test } from '@playwright/test';
import { textPdf } from './support/files';
import { signUpNewUser } from './support/session';

/** E2E_PAUSE (ms) holds each step on screen for the sprint recording; 0 in CI. */
const PAUSE = Number(process.env['E2E_PAUSE'] ?? 0);
/** Grafana of the local stack (grafana/otel-lgtm, anonymous access). */
const GRAFANA = process.env['E2E_GRAFANA_URL'] ?? 'http://localhost:3000';

const CONTRACT_TEXT = 'Article 1. Le loyer confidentiel est payable sous 30 jours.';
const FILE_NAME = 'Bail observable.pdf';

/**
 * Story 6.1 on the real stack: the upload's trace starts in the browser and continues in the API
 * (the XHR backend matters: under Angular's fetch default no browser span exists), the trace
 * carries no contract text or file name, and Grafana provisions the dashboard and alert rules.
 * The analyzer may be stopped (no Claude call): browser → API → Kafka is enough here.
 */
test('the upload is one trace from the browser, and Grafana shows the dashboard and the alert rules', async ({
  page,
  request,
}) => {
  test.setTimeout(180_000);
  const step = async (title: string, body: () => Promise<void>) => {
    await test.step(title, body);
    if (PAUSE) {
      await page.waitForTimeout(PAUSE);
    }
  };
  await signUpNewUser(page, request);
  let traceId = '';

  await step('upload a contract; the request carries a W3C traceparent', async () => {
    await page.getByRole('link', { name: 'Analyser un contrat' }).click();
    await page.locator('input[type=file]').setInputFiles(textPdf(FILE_NAME, CONTRACT_TEXT));
    const upload = page.waitForRequest(
      (r) => r.method() === 'POST' && r.url().endsWith('/api/v1/contracts'),
    );
    await page.getByRole('button', { name: 'Analyser' }).click();
    const traceparent = (await (await upload).allHeaders())['traceparent'] ?? '';
    expect(traceparent).toMatch(/^00-[0-9a-f]{32}-[0-9a-f]{16}-01$/);
    traceId = traceparent.split('-')[1];
    await expect(page.getByRole('heading', { level: 1 })).toHaveText(FILE_NAME);
  });

  await step('Tempo has one trace with the browser and the API, and no contract text', async () => {
    const trace = async (): Promise<string> => {
      const response = await request.get(
        `${GRAFANA}/api/datasources/proxy/uid/tempo/api/traces/${traceId}`,
      );
      return response.ok() ? await response.text() : '';
    };
    const services = async (): Promise<string[]> => {
      const body = await trace();
      if (!body) {
        return [];
      }
      const batches = (JSON.parse(body).batches ?? []) as {
        resource: { attributes: { key: string; value: { stringValue?: string } }[] };
      }[];
      return batches.flatMap((b) =>
        b.resource.attributes
          .filter((a) => a.key === 'service.name')
          .map((a) => a.value.stringValue ?? ''),
      );
    };
    // The browser exports in batches (~5 s) and Tempo indexes shortly after.
    await expect
      .poll(services, { timeout: 90_000, intervals: [2_000] })
      .toEqual(expect.arrayContaining(['mizan-web', 'mizan-api']));
    const body = await trace();
    expect(body).not.toContain('loyer confidentiel');
    expect(body).not.toContain(FILE_NAME);
  });

  await step('the provisioned dashboard shows the Mizan panels', async () => {
    await page.goto(`${GRAFANA}/d/mizan-overview`);
    await expect(page.getByText('Mizan — overview').first()).toBeVisible();
    for (const panel of [
      'Analyses per hour by outcome',
      'Model tokens per hour',
      'Global daily cap used',
      'Recent upload traces',
    ]) {
      await expect(page.getByText(panel).first()).toBeVisible();
    }
  });

  await step('the five alert rules from docs/devops §6 are provisioned', async () => {
    await page.goto(`${GRAFANA}/alerting/list?search=namespace:Mizan`);
    // The list shows folder and group; the group's own page lists its rules.
    await page.getByRole('link', { name: 'mizan', exact: true }).click();
    for (const rule of [
      'API p95 latency above 1 s',
      'Kafka consumer lag above 50',
      'More than 20 % of analyses failed in the last hour',
      'Global daily cap above 80 %',
      'More than 5 ERROR logs in 5 min',
    ]) {
      await expect(page.getByText(rule).first()).toBeVisible();
    }
  });
});
