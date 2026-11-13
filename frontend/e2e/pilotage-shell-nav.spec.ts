// E2E — navigation shell repliable (D-SHELL-NAV / issue #89).
import { expect, test } from '@playwright/test'
import { mockSession, openApp } from './support/pilotage'

test('E2E-SHELL-1 desktop starts with the nav open and the burger collapses it', async ({ page }) => {
  await mockSession(page)
  await openApp(page)
  const shell = page.getByTestId('app-shell')
  await expect(shell).not.toHaveClass(/app-shell--collapsed/)
  await expect(page.getByRole('link', { name: 'Tâches' })).toBeVisible()

  await page.getByTestId('shell-nav-toggle').click()
  await expect(shell).toHaveClass(/app-shell--collapsed/)
  await expect(page.getByRole('link', { name: 'Tâches' })).toBeHidden()

  await page.getByTestId('shell-nav-toggle').click()
  await expect(shell).not.toHaveClass(/app-shell--collapsed/)
  await expect(page.getByRole('link', { name: 'Tâches' })).toBeVisible()
})

test.describe('narrow viewport', () => {
  test.use({ viewport: { width: 390, height: 844 } })

  test('E2E-SHELL-2 mobile starts collapsed; drawer opens, link and backdrop close it', async ({
    page,
  }) => {
    await mockSession(page)
    // openApp attend le logo visible ; replié, seul le bandeau + burger restent (D-SHELL-NAV).
    await page.goto('./')
    await expect(page.getByTestId('shell-nav-toggle')).toBeVisible()
    await expect(page.getByText('Ada')).toBeVisible()
    const shell = page.getByTestId('app-shell')
    await expect(shell).toHaveClass(/app-shell--collapsed/)
    await expect(shell).toHaveClass(/app-shell--narrow/)
    await expect(page.getByRole('link', { name: 'Tâches' })).toBeHidden()

    await page.getByTestId('shell-nav-toggle').click()
    await expect(shell).toHaveClass(/app-shell--drawer-open/)
    await expect(page.getByRole('link', { name: 'Tâches' })).toBeVisible()
    await expect(page.getByTestId('shell-nav-backdrop')).toBeVisible()

    // Clic hors du tiroir (le centre du backdrop est sous l'aside).
    await page.getByTestId('shell-nav-backdrop').click({ position: { x: 340, y: 80 } })
    await expect(shell).toHaveClass(/app-shell--collapsed/)
    await expect(shell).not.toHaveClass(/app-shell--drawer-open/)

    await page.getByTestId('shell-nav-toggle').click()
    await page.getByRole('link', { name: 'Tâches' }).click()
    await expect(page).toHaveURL(/\/tasks/)
    await expect(shell).toHaveClass(/app-shell--collapsed/)
    await expect(shell).not.toHaveClass(/app-shell--drawer-open/)
  })
})
