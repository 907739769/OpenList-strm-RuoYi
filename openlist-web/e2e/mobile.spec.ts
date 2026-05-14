import { test, expect } from '@playwright/test'

test.describe('Mobile Responsive', () => {
  test('should display login page on mobile', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 812 })
    await page.goto('/login')
    await expect(page).toHaveTitle(/登录/)
    const card = page.locator('.login-card')
    await expect(card).toBeVisible()
  })

  test('should display dashboard on mobile', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 812 })
    await page.goto('/login')
    await page.locator('input[placeholder="用户名"]').fill('admin')
    await page.locator('input[placeholder="密码"]').fill('admin123')
    await page.locator('text=登 录').click()
    await page.waitForURL(/\/dashboard/, { timeout: 15000 })
    const mainContent = page.locator('.dashboard-container').first()
    await expect(mainContent).toBeVisible()
  })

  test('should render all pages on mobile', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 812 })
    await page.goto('/login')
    await page.locator('input[placeholder="用户名"]').fill('admin')
    await page.locator('input[placeholder="密码"]').fill('admin123')
    await page.locator('text=登 录').click()
    await page.waitForURL(/\/dashboard/, { timeout: 15000 })

    const pages = [
      { name: '用户管理', url: /\/system\/user/ },
      { name: 'STRM任务', url: /\/openlist\/strm-task/ },
      { name: '同步任务', url: /\/openlist\/copy-task/ }
    ]

    for (const pageInfo of pages) {
      const link = page.locator(`text=${pageInfo.name}`).first()
      if (await link.isVisible({ timeout: 3000 }).catch(() => false)) {
        await link.click()
        await page.waitForURL(pageInfo.url, { timeout: 10000 })
        await expect(page.locator(`text=${pageInfo.name}`)).toBeVisible()
      }
    }
  })
})
