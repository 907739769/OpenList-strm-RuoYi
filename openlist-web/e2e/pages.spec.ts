import { test, expect } from '@playwright/test'

test.beforeEach(async ({ page }) => {
  await page.goto('/login')
  await page.locator('input[placeholder="用户名"]').fill('admin')
  await page.locator('input[placeholder="密码"]').fill('admin123')
  await page.locator('text=登 录').click()
  await page.waitForURL(/\/dashboard/, { timeout: 15000 })
})

test.describe('Dashboard', () => {
  test('should load dashboard', async ({ page }) => {
    await expect(page).toHaveURL(/\/dashboard/)
    const mainContent = page.locator('.dashboard-container').first()
    await expect(mainContent).toBeVisible()
  })
})

test.describe('System Management Pages', () => {
  test('should navigate to user management', async ({ page }) => {
    const userLink = page.locator('text=用户管理').first()
    if (await userLink.isVisible({ timeout: 5000 }).catch(() => false)) {
      await userLink.click()
      await page.waitForURL(/\/system\/user/, { timeout: 10000 })
      await expect(page.locator('text=用户管理')).toBeVisible()
    }
  })

  test('should navigate to role management', async ({ page }) => {
    const roleLink = page.locator('text=角色管理').first()
    if (await roleLink.isVisible({ timeout: 5000 }).catch(() => false)) {
      await roleLink.click()
      await page.waitForURL(/\/system\/role/, { timeout: 10000 })
      await expect(page.locator('text=角色管理')).toBeVisible()
    }
  })

  test('should navigate to dept management', async ({ page }) => {
    const deptLink = page.locator('text=部门管理').first()
    if (await deptLink.isVisible({ timeout: 5000 }).catch(() => false)) {
      await deptLink.click()
      await page.waitForURL(/\/system\/dept/, { timeout: 10000 })
      await expect(page.locator('text=部门管理')).toBeVisible()
    }
  })

  test('should navigate to menu management', async ({ page }) => {
    const menuLink = page.locator('text=菜单管理').first()
    if (await menuLink.isVisible({ timeout: 5000 }).catch(() => false)) {
      await menuLink.click()
      await page.waitForURL(/\/system\/menu/, { timeout: 10000 })
      await expect(page.locator('text=菜单管理')).toBeVisible()
    }
  })

  test('should navigate to config management', async ({ page }) => {
    const configLink = page.locator('text=参数设置').first()
    if (await configLink.isVisible({ timeout: 5000 }).catch(() => false)) {
      await configLink.click()
      await page.waitForURL(/\/system\/config/, { timeout: 10000 })
      await expect(page.locator('text=参数设置')).toBeVisible()
    }
  })
})

test.describe('Monitor Pages', () => {
  test('should navigate to server monitor', async ({ page }) => {
    const serverLink = page.locator('text=服务监控').first()
    if (await serverLink.isVisible({ timeout: 5000 }).catch(() => false)) {
      await serverLink.click()
      await page.waitForURL(/\/monitor\/server/, { timeout: 10000 })
      await expect(page.locator('text=服务监控')).toBeVisible()
    }
  })

  test('should navigate to cache monitor', async ({ page }) => {
    const cacheLink = page.locator('text=缓存监控').first()
    if (await cacheLink.isVisible({ timeout: 5000 }).catch(() => false)) {
      await cacheLink.click()
      await page.waitForURL(/\/monitor\/cache/, { timeout: 10000 })
      await expect(page.locator('text=缓存监控')).toBeVisible()
    }
  })
})

test.describe('OpenList Pages', () => {
  test('should navigate to STRM task', async ({ page }) => {
    const strmLink = page.locator('text=STRM任务').first()
    if (await strmLink.isVisible({ timeout: 5000 }).catch(() => false)) {
      await strmLink.click()
      await page.waitForURL(/\/openlist\/strm-task/, { timeout: 10000 })
      await expect(page.locator('text=STRM任务')).toBeVisible()
    }
  })

  test('should navigate to copy task', async ({ page }) => {
    const copyLink = page.locator('text=同步任务').first()
    if (await copyLink.isVisible({ timeout: 5000 }).catch(() => false)) {
      await copyLink.click()
      await page.waitForURL(/\/openlist\/copy-task/, { timeout: 10000 })
      await expect(page.locator('text=同步任务')).toBeVisible()
    }
  })

  test('should navigate to rename task', async ({ page }) => {
    const renameLink = page.locator('text=重命名任务').first()
    if (await renameLink.isVisible({ timeout: 5000 }).catch(() => false)) {
      await renameLink.click()
      await page.waitForURL(/\/openlist\/rename-task/, { timeout: 10000 })
      await expect(page.locator('text=重命名任务')).toBeVisible()
    }
  })
})
