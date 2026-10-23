import assert from 'node:assert/strict'
import test from 'node:test'
import { groupAdminMenus, type AdminMenuEntry } from './admin-menu-groups.ts'

test('groups existing website and system menus without changing their routes', () => {
  const menus: AdminMenuEntry[] = [
    { path: '/site', title: '网站管理', children: [
      { path: '/articles', title: '文章列表' },
      { path: '/site/info', title: '信息管理' },
      { path: '/photos', title: '相册管理' },
    ] },
    { path: '/system', title: '系统管理', children: [
      { path: '/system/users', title: '用户管理' },
      { path: '/system/menus', title: '菜单管理' },
      { path: '/system/logs', title: '日志管理', children: [
        { path: '/system/operations', title: '操作日志' },
      ] },
    ] },
  ]

  const groups = groupAdminMenus(menus)
  assert.deepEqual(groups.map(group => group.title), ['内容管理', '网站配置', '用户与权限', '个人展示', '系统运维'])
  assert.deepEqual(groups.flatMap(group => group.items.map(item => item.path)), [
    '/articles', '/site/info', '/system/users', '/system/menus', '/photos', '/system/logs',
  ])
  assert.equal(groups.find(group => group.key === 'operations')?.items[0].children?.[0].path, '/system/operations')
})

test('keeps hidden and unknown dynamic menu items in an explicit fallback group', () => {
  const hidden = { path: '/future/private', title: '内部功能', hideInMenu: true }
  const groups = groupAdminMenus([
    { path: '/future', title: '未来功能', children: [hidden] },
    { path: '/integrations', title: '集成管理' },
  ])

  assert.equal(groups.length, 1)
  assert.equal(groups[0].title, '其他功能')
  assert.deepEqual(groups[0].items.map(item => item.path), ['/future', '/integrations'])
  assert.equal(groups[0].items[0].children?.[0], hidden)
  assert.equal(groups[0].items[0].children?.[0].hideInMenu, true)
})
