import AxeBuilder from '@axe-core/playwright';
import { Page, expect, test } from '@playwright/test';

/** WCAG 2.2 A + AA, the level the UX doc commits to. */
const TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'];

/**
 * Fails the test on any axe violation on the current page, listing rule, impact and the first
 * offending selectors so the report says what to fix.
 */
export async function expectNoAxeViolations(page: Page, label: string): Promise<void> {
  const { violations } = await new AxeBuilder({ page }).withTags(TAGS).analyze();
  const summary = violations.map(
    (v) =>
      `${v.id} (${v.impact}): ${v.help} — ${v.nodes
        .slice(0, 3)
        .map((n) => n.target.join(' '))
        .join(' | ')}`,
  );
  await test.info().attach(`axe ${label}`, {
    body: JSON.stringify(violations, null, 2),
    contentType: 'application/json',
  });
  expect(summary, `axe violations on ${label}`).toEqual([]);
}
