import { test } from '@playwright/test'

test('输错密码时用户看到什么', async ({ page }) => {
  const navs: string[] = []
  page.on('framenavigated', f => navs.push(f.url().replace('http://localhost:3000','')))

  await page.goto('/login')
  await page.locator('input[placeholder="用户名"]').fill('admin')
  await page.locator('input[placeholder="密码"]').fill('wrongpassword')
  await page.locator('text=登 录').click()
  await page.waitForTimeout(3000)

  console.log('>>> 导航序列(出现重复/login即为硬跳转):', JSON.stringify(navs))
  const msgs = await page.locator('.el-message').allInnerTexts()
  console.log('>>> 页面上的提示消息:', JSON.stringify(msgs))
  console.log('>>> 当前 URL:', page.url().replace('http://localhost:3000',''))
})
