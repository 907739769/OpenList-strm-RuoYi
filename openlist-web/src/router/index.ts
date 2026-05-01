import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import Cookies from 'js-cookie'
import type { MenuRoute } from '@/stores/user'
import { useUserStore } from '@/stores/user'

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
    component: Layout,
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '首页', icon: 'Odometer', affix: true }
      }
    ]
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
  'openliststrm/renameDetail/index': () => import('@/views/openlist/renameDetail/index.vue')
}

function convertMenuToRoute(menu: MenuRoute): RouteRecordRaw {
  const children = menu.children && menu.children.length > 0
    ? menu.children.map(child => convertMenuToRoute(child))
    : []

  const component = componentMap[menu.component || ''] || (() => import('@/views/error/404.vue'))

  const route: RouteRecordRaw = {
    path: menu.path.startsWith('/') ? menu.path : '/' + menu.path,
    name: menu.name,
    component: menu.component === 'Layout' ? Layout : component,
    meta: {
      title: menu.meta?.title || '',
      icon: menu.meta?.icon || '',
      hidden: menu.hidden || false
    },
    children
  }

  if (children.length === 0 && menu.redirect && typeof menu.redirect === 'string' && menu.redirect.startsWith('/')) {
    route.redirect = menu.redirect
  }

  return route
}

export function addDynamicRoutes(menuList: MenuRoute[]) {
  for (const menu of menuList) {
    const route = convertMenuToRoute(menu)
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
    document.title = `${title} - OpenList-strm-RuoYi`
  }

  const hasToken = Cookies.get('token')

  if (hasToken) {
    if (to.path === '/login') {
      next({ path: '/' })
      NProgress.done()
    } else {
      const userStore = useUserStore()
      if (!userStore.routes.length) {
        try {
          await userStore.getUserInfo()
          const menuRoutes = await userStore.getRouters()
          addDynamicRoutes(menuRoutes)
          next({ path: '/dashboard', replace: true })
        } catch {
          userStore.clearToken()
          next('/login')
        }
      } else {
        next()
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
