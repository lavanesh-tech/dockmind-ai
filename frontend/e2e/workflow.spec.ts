import { expect, Page, test } from '@playwright/test';

/**
 * The main dispatcher workflow, end to end through the real login, gateway, services, Kafka and
 * databases: an admin adds docks, a dispatcher books trucks, computes the schedule, reports a late
 * truck, compares plans, approves one, and sees it in the audit trail. A user of another company
 * sees none of it.
 */
const password = process.env['DEMO_USER_PASSWORD'] ?? '';
const run = Date.now().toString(36).toUpperCase();
const day = new Date(Date.now() + 2 * 86_400_000).toISOString().slice(0, 10); // two days ahead: nothing has started

async function signIn(page: Page, username: string): Promise<void> {
  await page.goto('/login');
  await page.getByTestId('sign-in').click();
  await page.locator('#username').fill(username);
  await page.locator('#password').fill(password);
  await page.locator('#kc-login').click();
  await expect(page.getByTestId('who')).toContainText(username);
}

async function signOut(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'Account' }).click();
  await page.getByRole('menuitem', { name: /Sign out/ }).click();
  await expect(page.getByTestId('sign-in')).toBeVisible();
}

async function pick(page: Page, label: string, option: string): Promise<void> {
  await page.getByLabel(label).click();
  await page.getByRole('option', { name: option, exact: true }).click();
}

test.beforeAll(() => {
  expect(password, 'Set DEMO_USER_PASSWORD (scripts/run-local.sh e2e does this from .env)').not.toBe('');
});

test('dispatcher recovers from a late truck with an approved, audited plan', async ({ page }) => {
  // Admin adds two docks.
  await signIn(page, 'admin.acme');
  await page.goto('/docks');
  for (const code of [`A${run}`, `B${run}`]) {
    await page.getByTestId('dock-code').fill(code);
    await page.getByTestId('add-dock').click();
    await expect(page.getByRole('cell', { name: code, exact: true })).toBeVisible();
  }
  await signOut(page);

  // Dispatcher books three trucks for the same morning.
  await signIn(page, 'dispatcher.acme');
  await page.goto('/shipments');
  await page.getByLabel('Day (UTC)').fill(day);
  for (const n of [1, 2, 3]) {
    await page.getByTestId('ref').fill(`E2E-${run}-${n}`);
    await page.getByTestId('book').click();
    await expect(page.getByRole('cell', { name: `E2E-${run}-${n}` })).toBeVisible();
  }

  // Compute the schedule (events reach the scheduling service asynchronously, so retry briefly).
  await page.goto('/schedule');
  await page.getByTestId('schedule-date').fill(day);
  await expect(async () => {
    await page.getByTestId('compute').click();
    await expect(page.getByText(`E2E-${run}-3`)).toBeVisible({ timeout: 3000 });
  }).toPass({ timeout: 60_000 });

  // Report truck 1 as late and get plans.
  await page.goto('/disruptions');
  await page.getByLabel('Day (UTC)').fill(day);
  await pick(page, 'Truck', (await truckOption(page, `E2E-${run}-1`)));
  await page.getByLabel('New arrival (UTC)').fill('11:00');
  await page.getByLabel('Plan from (UTC)').fill('06:00');
  await page.getByTestId('report').click();
  await expect(page.getByTestId('status')).toHaveText('PENDING_APPROVAL');
  await expect(page.getByTestId('plan-MINIMAL_CHANGE')).toBeVisible();

  // Approve the recommended plan; it is applied and audited.
  await page.getByTestId('approve-MINIMAL_CHANGE').click();
  await expect(page.getByTestId('status')).toHaveText('APPLIED');
  await expect(page.getByTestId('audit-row')).toHaveCount(4);
  await expect(page.getByTestId('audit-row').last()).toContainText('APPLIED');
  const url = page.url();
  await signOut(page);

  // Another company can't see it.
  await signIn(page, 'dispatcher.globex');
  await page.goto(url);
  await expect(page.locator('.error-text')).toBeVisible();
});

async function truckOption(page: Page, reference: string): Promise<string> {
  await page.getByLabel('Truck').click();
  const option = page.getByRole('option').filter({ hasText: reference });
  const text = (await option.first().innerText()).trim();
  await page.keyboard.press('Escape');
  return text;
}
