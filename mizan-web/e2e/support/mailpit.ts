import { APIRequestContext, expect } from '@playwright/test';

export const MAILPIT_URL = process.env['E2E_MAILPIT_URL'] ?? 'http://localhost:8025';

interface MailpitSearch {
  messages: { ID: string }[];
}

/** Waits for Keycloak's verification e-mail to `to` and returns the action-token link. */
export async function verificationLink(request: APIRequestContext, to: string): Promise<string> {
  let link = '';
  await expect
    .poll(
      async () => {
        const search = await request.get(`${MAILPIT_URL}/api/v1/search`, {
          params: { query: `to:"${to}"` },
        });
        const { messages } = (await search.json()) as MailpitSearch;
        if (!messages.length) {
          return '';
        }
        const message = await request.get(`${MAILPIT_URL}/api/v1/message/${messages[0].ID}`);
        const { Text } = (await message.json()) as { Text: string };
        link = Text.match(/https?:\/\/\S+login-actions\/action-token\S+/)?.[0] ?? '';
        return link;
      },
      { message: `verification e-mail to ${to}`, timeout: 30_000 },
    )
    .not.toBe('');
  return link;
}
