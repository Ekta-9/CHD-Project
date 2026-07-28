// @ts-check
const { test, expect } = require('@playwright/test');

// The backend is never called in these tests: every /api request is
// intercepted with page.route so only the frontend logic is exercised.

test.describe('Login page', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/login.html');
  });

  test('renders email, password and submit button', async ({ page }) => {
    await expect(page.locator('#email')).toBeVisible();
    await expect(page.locator('#password')).toBeVisible();
    await expect(page.locator('#loginBtn')).toHaveText('Log In');
  });

  test('has a link to the signup page', async ({ page }) => {
    await expect(page.locator('a[href="signup.html"]')).toBeVisible();
  });

  test('successful login stores tokens and redirects to dashboard', async ({ page }) => {
    await page.route('**/api/auth/login', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          status: 'success',
          data: {
            accessToken: 'fake-access-token',
            refreshToken: 'fake-refresh-token',
            expiresIn: 900,
            tokenType: 'Bearer',
            sessionId: '11111111-2222-3333-4444-555555555555',
          },
        }),
      })
    );
    // main.html loads data on arrival - stub everything it asks for
    await page.route('**/api/**', (route) => {
      if (route.request().url().includes('/auth/login')) return route.fallback();
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ status: 'success', data: { content: [], pagination: {} } }),
      });
    });

    await page.fill('#email', 'doc@example.com');
    await page.fill('#password', 'password123');
    await page.click('#loginBtn');

    await expect(page.locator('#successMsg')).toContainText('Login successful');
    await page.waitForURL('**/main.html');

    const accessToken = await page.evaluate(() => sessionStorage.getItem('accessToken'));
    expect(accessToken).toBe('fake-access-token');
  });

  test('wrong credentials show the backend error message', async ({ page }) => {
    await page.route('**/api/auth/login', (route) =>
      route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({
          status: 'error',
          error: { code: 'UNAUTHORIZED', message: 'Invalid credentials' },
        }),
      })
    );

    await page.fill('#email', 'doc@example.com');
    await page.fill('#password', 'wrong-password');
    await page.click('#loginBtn');

    await expect(page.locator('#errorMsg')).toContainText('Invalid credentials');
    await expect(page).toHaveURL(/login\.html/);
  });

  test('backend unreachable shows a friendly error', async ({ page }) => {
    await page.route('**/api/auth/login', (route) => route.abort('connectionrefused'));

    await page.fill('#email', 'doc@example.com');
    await page.fill('#password', 'password123');
    await page.click('#loginBtn');

    await expect(page.locator('#errorMsg')).toBeVisible();
  });
});
