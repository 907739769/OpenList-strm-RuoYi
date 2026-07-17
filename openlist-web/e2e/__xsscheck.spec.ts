import { test } from '@playwright/test'

// 验证 realtime.vue 的日志剥离逻辑：tempDiv.innerHTML = rawLine
// detached 元素里的 <img onerror> 是否会真的触发
test('innerHTML 剥离标签是否会激活恶意负载', async ({ page }) => {
  await page.goto('/login')
  const fired = await page.evaluate(() => {
    return new Promise<boolean>(resolve => {
      ;(window as any).__xss = () => resolve(true)
      // 完全复刻 realtime.vue ws.onmessage 里的处理方式
      const rawLine = `<div class='log-item'><img src=x onerror="window.__xss()"></div>`
      const tempDiv = document.createElement('div')
      tempDiv.innerHTML = rawLine
      const textContent = tempDiv.textContent || (tempDiv as any).innerText || rawLine
      void textContent
      setTimeout(() => resolve(false), 1500)
    })
  })
  console.log('>>> onerror 是否被触发(true=存在XSS):', fired)
})
