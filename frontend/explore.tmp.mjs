import { chromium } from '@playwright/test'
const [out, mobile, ...paths] = process.argv.slice(2)
const browser = await chromium.launch()
const opts = mobile === 'm' ? { viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true, locale: 'fr-BE' } : { viewport: { width: 1280, height: 720 }, locale: 'fr-BE' }
const ctx = await browser.newContext(opts)
const page = await ctx.newPage()
await page.goto('http://localhost:8080/supervision')
if (await page.locator('#username').count()) { await page.locator('#username').fill('demo'); await page.locator('#password').fill('demo'); await page.locator('#kc-login').click(); await page.waitForURL(/localhost:8080/) }
for (const p of paths) {
  await page.goto('http://localhost:8080' + p); await page.waitForLoadState('networkidle'); await page.waitForTimeout(800)
  await page.screenshot({ path: `${out}/${mobile}${p.replace(/[^a-z]+/gi, '_')}.png`, fullPage: true })
  console.log('=== ' + p + '\n' + (await page.locator('main').innerText()).slice(0, 1500))
  console.log('--- buttons: ' + (await page.locator('main button').allInnerTexts()).join(' | ').slice(0, 500))
}
await browser.close()
