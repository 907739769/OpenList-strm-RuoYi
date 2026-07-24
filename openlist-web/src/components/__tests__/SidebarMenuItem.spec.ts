import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import SidebarMenuItem from '../SidebarMenuItem.vue'
import type { MenuRoute } from '@/stores/user'

// el-sub-menu/el-menu-item/el-icon 在测试环境里没有全局注册 Element Plus，
// 用最小 stub 顶替，只保留渲染 index 属性和具名插槽，方便断言层级结构。
const stubs = {
  'el-sub-menu': {
    props: ['index'],
    template: '<div class="stub-sub-menu" :data-index="index"><div class="stub-title"><slot name="title" /></div><slot /></div>'
  },
  'el-menu-item': {
    props: ['index'],
    template: '<div class="stub-menu-item" :data-index="index"><slot name="title" /></div>'
  },
  'el-icon': { template: '<i><slot /></i>' }
}

function leaf(path: string, title: string): MenuRoute {
  return { path, name: title, meta: { title } }
}

describe('SidebarMenuItem', () => {
  it('叶子菜单渲染成 el-menu-item，index 用自身 path', () => {
    const menu = leaf('/openlist/renameConfig', '重命名规则设置')
    const wrapper = mount(SidebarMenuItem, { props: { menu }, global: { stubs } })

    const item = wrapper.find('.stub-menu-item')
    expect(item.exists()).toBe(true)
    expect(item.attributes('data-index')).toBe('/openlist/renameConfig')
    expect(item.text()).toContain('重命名规则设置')
    expect(wrapper.find('.stub-sub-menu').exists()).toBe(false)
  })

  it('目录菜单渲染成 el-sub-menu，index 用 menu.name 而不是 path', () => {
    const menu: MenuRoute = {
      path: '/openliststrm',
      name: '同步管理',
      meta: { title: '同步管理' },
      children: [
        leaf('/openliststrm/task', '同步任务配置'),
        leaf('/openliststrm/copy', '同步任务记录')
      ]
    }
    const wrapper = mount(SidebarMenuItem, { props: { menu }, global: { stubs } })

    const subMenu = wrapper.find('.stub-sub-menu')
    expect(subMenu.exists()).toBe(true)
    expect(subMenu.attributes('data-index')).toBe('同步管理')
    expect(subMenu.text()).toContain('同步管理')

    const items = wrapper.findAll('.stub-menu-item')
    expect(items).toHaveLength(2)
    expect(items[0].attributes('data-index')).toBe('/openliststrm/task')
    expect(items[1].attributes('data-index')).toBe('/openliststrm/copy')
  })

  it('三级嵌套（目录>子目录>叶子）逐级递归渲染，父子目录即使 path 相同，index(name) 也不会撞车', () => {
    const menu: MenuRoute = {
      path: '/openliststrm',
      name: 'OpenListStrm',
      meta: { title: 'OpenListStrm' },
      children: [
        {
          // 刻意让子目录的 path 和父目录一样，模拟后端 derivePath 反推路径撞车的真实场景，
          // 验证用 name 当 index 不受这个影响
          path: '/openliststrm',
          name: '同步管理',
          meta: { title: '同步管理' },
          children: [leaf('/openliststrm/task', '同步任务配置')]
        }
      ]
    }
    const wrapper = mount(SidebarMenuItem, { props: { menu }, global: { stubs } })

    const subMenus = wrapper.findAll('.stub-sub-menu')
    expect(subMenus).toHaveLength(2)
    expect(subMenus[0].attributes('data-index')).toBe('OpenListStrm')
    expect(subMenus[1].attributes('data-index')).toBe('同步管理')
    expect(subMenus[0].attributes('data-index')).not.toBe(subMenus[1].attributes('data-index'))

    const item = wrapper.find('.stub-menu-item')
    expect(item.attributes('data-index')).toBe('/openliststrm/task')
  })

  it('同级多个目录反推出的path相同时（模拟 derivePath 撞车场景），仍能各自正确渲染，index/key不受影响', () => {
    const menu: MenuRoute = {
      path: '/openliststrm',
      name: 'OpenListStrm',
      meta: { title: 'OpenListStrm' },
      children: [
        {
          // 故意和下面两个目录的 path 一样，模拟 derivePath 撞车
          path: '/openliststrm',
          name: '同步管理',
          meta: { title: '同步管理' },
          children: [leaf('/openliststrm/task', '同步任务配置')]
        },
        {
          // 故意和上面/下面目录的 path 一样
          path: '/openliststrm',
          name: 'STRM管理',
          meta: { title: 'STRM管理' },
          children: [leaf('/openliststrm/strm_task', 'strm任务配置')]
        },
        {
          // 故意和上面两个目录的 path 一样
          path: '/openliststrm',
          name: '重命名管理',
          meta: { title: '重命名管理' },
          children: [leaf('/openliststrm/renameTask', '重命名任务配置')]
        }
      ]
    }
    const wrapper = mount(SidebarMenuItem, { props: { menu }, global: { stubs } })

    // 顶层 OpenListStrm 自己是一个 sub-menu，加上3个子目录各自也是 sub-menu，一共4个
    const subMenus = wrapper.findAll('.stub-sub-menu')
    expect(subMenus).toHaveLength(4)

    // v-for 用的 key 撞车（都是 '/openliststrm'）不应导致节点被错误复用或丢失，
    // index(name) 应该各自唯一
    const indexes = subMenus.map(s => s.attributes('data-index'))
    expect(new Set(indexes).size).toBe(indexes.length)
    expect(indexes).toEqual(['OpenListStrm', '同步管理', 'STRM管理', '重命名管理'])

    // 3个叶子节点都正确渲染出来了，且各自 index 对应各自的 url
    const items = wrapper.findAll('.stub-menu-item')
    expect(items).toHaveLength(3)
    expect(items.map(i => i.attributes('data-index'))).toEqual([
      '/openliststrm/task',
      '/openliststrm/strm_task',
      '/openliststrm/renameTask'
    ])
  })
})
