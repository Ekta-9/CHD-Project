// @ts-check
const { test, expect } = require('@playwright/test');

test.describe('Signup page', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/signup.html');
  });

  test('renders all registration fields', async ({ page }) => {
    await expect(page.locator('#name')).toBeVisible();
    await expect(page.locator('#email')).toBeVisible();
    await expect(page.locator('#phone-number')).toBeVisible();
    await expect(page.locator('#password')).toBeVisible();
    await expect(page.locator('#confirm')).toBeVisible();
    await expect(page.locator('#signupBtn')).toHaveText('Sign Up');
  });

  test('successful registration redirects to login', async ({ page }) => {
    await page.route('**/api/auth/register', (route) =>
      route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify({
          status: 'success',
          message: 'Doctor registered successfully',
          data: { doctorId: '11111111-2222-3333-4444-555555555555', email: 'new@example.com' },
        }),
      })
    );

    await page.fill('#name', 'Dr. New');
    await page.fill('#email', 'new@example.com');
    await page.fill('#phone-number', '9876543210');
    await page.fill('#password', 'password123');
    await page.fill('#confirm', 'password123');
    await page.click('#signupBtn');

    await page.waitForURL('**/login.html');
  });

  test('duplicate email shows backend error', async ({ page }) => {
    await page.route('**/api/auth/register', (route) =>
      route.fulfill({
        status: 400,
        contentType: 'application/json',
        body: JSON.stringify({
          status: 'error',
          error: { code: 'BAD_REQUEST', message: 'Email already exists' },
        }),
      })
    );

    await page.fill('#name', 'Dr. Dup');
    await page.fill('#email', 'existing@example.com');
    await page.fill('#phone-number', '9876543210');
    await page.fill('#password', 'password123');
    await page.fill('#confirm', 'password123');
    await page.click('#signupBtn');

    await expect(page.locator('#errorMsg')).toContainText('Email already exists');
    await expect(page).toHaveURL(/signup\.html/);
  });

  test('mismatched passwords are rejected client-side', async ({ page }) => {
    let backendCalled = false;
    await page.route('**/api/auth/register', (route) => {
      backendCalled = true;
      return route.fulfill({ status: 500, body: '{}' });
    });

    await page.fill('#name', 'Dr. Mismatch');
    await page.fill('#email', 'mm@example.com');
    await page.fill('#phone-number', '9876543210');
    await page.fill('#password', 'password123');
    await page.fill('#confirm', 'different456');
    await page.click('#signupBtn');

    await expect(page.locator('#errorMsg')).toBeVisible();
    expect(backendCalled).toBe(false);
  });
});
