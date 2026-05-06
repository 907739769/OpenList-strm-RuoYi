import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import Cookies from 'js-cookie'
import type { MenuRoute } from '@/stores/user'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'

NProgress.configure({ showSpinner: false })

export const Layout = () => import('@/layouts/DesktopLayout.vue')

export const constantRoutes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/auth/Login.vue'),
    meta: { title: '登录', hidden: true }
  },
  {
    path: '/404',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '404', hidden: true }
  },
  {
    path: '/',
    redirect: '/dashboard'
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('@/views/dashboard/index.vue'),
    meta: { title: '首页', icon: 'Odometer' }
  }
]

const componentMap: Record<string, () => Promise<any>> = {
  'Layout': () => import('@/layouts/DesktopLayout.vue'),
  'system/dict/type/index': () => import('@/views/system/dict/type/index.vue'),
  'system/dict/data/index': () => import('@/views/system/dict/data/index.vue'),
  'system/dict/index': () => import('@/views/system/dict/type/index.vue'),
  'system/config/index': () => import('@/views/system/config/index.vue'),
  'monitor/job/index': () => import('@/views/monitor/job/index.vue'),
  'monitor/log/index': () => import('@/views/monitor/log/realtime.vue'),
  'openlist/strmTask/index': () => import('@/views/openlist/strmTask/index.vue'),
  'openlist/strmRecord/index': () => import('@/views/openlist/strmRecord/index.vue'),
  'openlist/copyTask/index': () => import('@/views/openlist/copyTask/index.vue'),
  'openlist/copyRecord/index': () => import('@/views/openlist/copyRecord/index.vue'),
  'openlist/renameTask/index': () => import('@/views/openlist/renameTask/index.vue'),
  'openlist/renameDetail/index': () => import('@/views/openlist/renameDetail/index.vue'),
  'openliststrm/task/index': () => import('@/views/openlist/copyTask/index.vue'),
  'openliststrm/copy/index': () => import('@/views/openlist/copyRecord/index.vue'),
  'openliststrm/strm_task/index': () => import('@/views/openlist/strmTask/index.vue'),
  'openliststrm/strm/index': () => import('@/views/openlist/strmRecord/index.vue'),
  'openliststrm/renameTask/index': () => import('@/views/openlist/renameTask/index.vue'),
  'openliststrm/renameDetail/index': () => import('@/views/openlist/renameDetail/index.vue'),
  'views-mobile/strmRecord/index': () => import('@/views-mobile/strmRecord/index.vue'),
  'views-mobile/strmTask/index': () => import('@/views-mobile/strmTask/index.vue'),
  'views-mobile/copyTask/index': () => import('@/views-mobile/copyTask/index.vue'),
  'views-mobile/copyRecord/index': () => import('@/views-mobile/copyRecord/index.vue'),
  'views-mobile/renameTask/index': () => import('@/views-mobile/renameTask/index.vue'),
  'views-mobile/renameDetail/index': () => import('@/views-mobile/renameDetail/index.vue'),
  'views-mobile/dashboard/index': () => import('@/views-mobile/dashboard/index.vue')
}

/**
 * Normalize backend component path to consistent desktop format (openlist/...).
 * Backend may send different path formats depending on menu configuration version:
 *   - "openlist/xxx/index" (canonical)
 *   - "openliststrm/copy/index" (legacy alias)
 *   - "openlist/xxx" (without /index)
 */
function normalizeComponentPath(component: string): string {
  if (!component) return component

  // Already in canonical format
  if (component.startsWith('views/openlist/')) return component

  // Legacy openliststrm aliases -> desktop component paths
  const aliasMap: Record<string, string> = {
    'openliststrm/task/index': 'openlist/copyTask/index',
    'openliststrm/copy/index': 'openlist/copyRecord/index',
    'openliststrm/strm_task/index': 'openlist/strmTask/index',
    'openliststrm/strm/index': 'openlist/strmRecord/index',
    'openliststrm/renameTask/index': 'openlist/renameTask/index',
    'openliststrm/renameDetail/index': 'openlist/renameDetail/index',
  }
  if (aliasMap[component]) return aliasMap[component]

  // 'openlist/xxx/index' format (from DB url like /openlist/copy)
  // Map to the correct component: openlist/copyRecord, openlist/strmTask, etc.
  const directPathMap: Record<string, string> = {
    'openlist/copy/index': 'openlist/copyRecord/index',
    'openlist/copyTask/index': 'openlist/copyTask/index',
    'openlist/strmTask/index': 'openlist/strmTask/index',
    'openlist/strmRecord/index': 'openlist/strmRecord/index',
    'openlist/renameTask/index': 'openlist/renameTask/index',
    'openlist/renameDetail/index': 'openlist/renameDetail/index',
    'openlist/copy/index/index': 'openlist/copyRecord/index',
    'openlist/strmTask/index/index': 'openlist/strmTask/index',
    'openlist/strmRecord/index/index': 'openlist/strmRecord/index',
    'openlist/copyTask/index/index': 'openlist/copyTask/index',
    'openlist/renameTask/index/index': 'openlist/renameTask/index',
    'openlist/renameDetail/index/index': 'openlist/renameDetail/index',
  }
  if (directPathMap[component]) return directPathMap[component]

  // Direct openlist/xxx -> ensure /index suffix
  if (component.startsWith('openlist/')) {
    return component.endsWith('/index')
      ? component
      : `${component}/index`
  }

  return component
}

function convertMenuToRoute(menu: MenuRoute): RouteRecordRaw {
  const children = menu.children && menu.children.length > 0
    ? menu.children.map(child => convertMenuToRoute(child))
    : []

  let componentPath = menu.component || ''

  // 1. Normalize to canonical desktop format
  componentPath = normalizeComponentPath(componentPath)

  // 2. Convert to mobile view on mobile devices
  const isMobile = useAppStore().device === 'mobile'
  if (isMobile && componentPath.startsWith('openlist/')) {
    componentPath = componentPath.replace('openlist/', 'views-mobile/')
  }

  const component = componentMap[componentPath] || (() => import('@/views/error/404.vue'))

  const isLayout = menu.component === 'Layout'
  const route: RouteRecordRaw = {
    path: menu.path.startsWith('/') ? menu.path : '/' + menu.path,
    name: menu.name,
    component: isLayout ? Layout : component,
    meta: {
      title: menu.meta?.title || '',
      icon: menu.meta?.icon || '',
      hidden: menu.hidden || false,
      isParentLayout: isLayout
    },
    children
  }

  if (children.length === 0 && menu.redirect && typeof menu.redirect === 'string' && menu.redirect.startsWith('/')) {
    route.redirect = menu.redirect
  }

  return route
}

function extractLeafRoutes(menus: MenuRoute[]): MenuRoute[] {
  const leaves: MenuRoute[] = []
  for (const menu of menus) {
    if (menu.component === 'Layout' && menu.children?.length) {
      leaves.push(...extractLeafRoutes(menu.children))
    } else {
      leaves.push(menu)
    }
  }
  return leaves
}

export function addDynamicRoutes(menuList: MenuRoute[]) {
  const leafMenus = extractLeafRoutes(menuList)
  console.log('[router] leafMenus:', leafMenus.map(m => `${m.path}(${m.component})`))

  for (const menu of leafMenus) {
    const route = convertMenuToRoute(menu)
    const existing = router.getRoutes().find(r => r.path === route.path && r.name === route.name)
    if (existing) {
      console.log('[router] skipping duplicate route:', route.path, route.name)
      continue
    }
    console.log('[router] ADDING route:', route.path, route.name)
    router.addRoute(route)
  }
}

const router = createRouter({
  history: createWebHistory(),
  routes: constantRoutes,
  scrollBehavior: () => ({ top: 0 })
})

router.beforeEach(async (to, _from, next) => {
  NProgress.start()
  const title = to.meta?.title || ''
  if (title) {
      document.title = `${title} - OSR`
  }

  const appStore = useAppStore()
  const isMobile = window.innerWidth < 768
  const newDevice = isMobile ? 'mobile' : 'desktop'
  const deviceChanged = appStore.device !== newDevice

  if (deviceChanged) {
    appStore.toggleDevice(newDevice)
    const userStore = useUserStore()
    userStore.routes = []
  }

  const hasToken = Cookies.get('token')

  if (hasToken) {
    if (to.path === '/login') {
      next({ path: '/' })
      NProgress.done()
    } else {
      const userStore = useUserStore()
      try {
        if (!userStore.routes.length) {
          await userStore.getUserInfo()
          const menuRoutes = await userStore.getRouters()
          addDynamicRoutes(menuRoutes)
        }
        next()
      } catch {
        userStore.clearToken()
        next('/login')
      }
    }
  } else {
    if (to.meta?.hidden === undefined && to.path !== '/login') {
      next(`/login?redirect=${to.path}`)
    } else {
      next()
    }
  }
})

router.afterEach(() => {
  NProgress.done()
})

export default router
