import { test, expect } from '@playwright/test'

test('scaffold shell renders FlowForge brand', async ({ page }) => {
  await page.goto('./')
  await expect(page.getByText('FlowForge')).toBeVisible()
})
