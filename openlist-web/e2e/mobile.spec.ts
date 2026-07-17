import { test, expect } from '@playwright/test'

async function login(page: any) {
  await page.setViewportSize({ width: 375, height: 812 })
  await page.goto('/login')
  await page.locator('input[placeholder="用户名"]').fill('admin')
  await page.locator('input[placeholder="密码"]').fill('admin123')
  await page.locator('text=登 录').click()
  await page.waitForURL(/\/dashboard/, { timeout: 15000 })
}

test.describe('Mobile Responsive', () => {
  test('should display login page on mobile', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 812 })
    await page.goto('/login')
    await expect(page).toHaveTitle(/登录/)
    await expect(page.locator('.login-card')).toBeVisible()
  })

  test('should render the mobile dashboard, not the desktop one', async ({ page }) => {
    await login(page)
    await expect(page.locator('.mobile-dashboard')).toBeVisible()
    // PC 版首页的 echarts 不应出现在移动端
    await expect(page.locator('.echarts-container')).toHaveCount(0)
    // 统计卡取自接口，数量固定为 6
    await expect(page.locator('.stat-card')).toHaveCount(6)
  })

  test('dashboard quick links should all resolve to a real page', async ({ page }) => {
    await login(page)

    // 快捷入口来自异步下发的菜单，先等它渲染出来再取数
    await expect(page.locator('.action-item').first()).toBeVisible()
    const count = await page.locator('.action-item').count()
    expect(count).toBeGreaterThan(0)

    for (let i = 0; i < count; i++) {
      await page.goto('/dashboard')
      await expect(page.locator('.action-item')).toHaveCount(count)

      const item = page.locator('.action-item').nth(i)
      const name = (await item.innerText()).trim()
      await item.click()

      // 落地页必须真实存在：既不能停在首页，也不能是 404
      await expect(page, `快捷入口「${name}」未跳转`).not.toHaveURL(/\/dashboard/)
      await expect(page.locator('text=404'), `快捷入口「${name}」指向 404`).toHaveCount(0)
    }
  })

  test('dashboard stat cards should link to their record pages', async ({ page }) => {
    await login(page)

    await page.locator('.stat-card.clickable').first().click()
    await expect(page).toHaveURL(/\/openliststrm\/copy/)
  })
})
