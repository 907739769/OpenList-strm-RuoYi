import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

/**
 * 单元测试专用配置。
 * 不复用 vite.config.ts：那份配置带 PWA、自动导入等插件，
 * 在测试环境下只会制造噪音，单测需要的只有 vue 插件与 @ 别名。
 */
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  test: {
    environment: 'jsdom',
    globals: false,
    // 只收 src 下的 .spec.ts，避免把 Playwright 的 e2e 用例也跑进来
    include: ['src/**/*.spec.ts']
  }
})
