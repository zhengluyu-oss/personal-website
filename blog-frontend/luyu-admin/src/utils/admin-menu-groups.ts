export interface AdminMenuEntry {
  path: string
  title?: unknown
  children?: AdminMenuEntry[]
  hideInMenu?: boolean
}

export interface AdminMenuGroup {
  key: string
  title: string
  items: AdminMenuEntry[]
}

const groupOrder = [
  { key: 'content', title: '内容管理' },
  { key: 'site', title: '网站配置' },
  { key: 'access', title: '用户与权限' },
  { key: 'community', title: '互动与社区' },
  { key: 'showcase', title: '个人展示' },
  { key: 'operations', title: '系统运维' },
  { key: 'other', title: '其他功能' },
] as const

function matches(path: string, routes: readonly string[]) {
  return routes.some(route => path === route || path.startsWith(`${route}/`))
}

function groupKeyForPath(path: string) {
  if (matches(path, ['/articles', '/categories', '/tags']))
    return 'content'
  if (matches(path, ['/site/info']))
    return 'site'
  if (matches(path, ['/system/users', '/system/roles', '/system/menus', '/system/permissions', '/roles', '/permissions', '/users']))
    return 'access'
  if (matches(path, ['/messages', '/comments', '/collections', '/tree-hole', '/links', '/blacklist']))
    return 'community'
  if (matches(path, ['/experiences', '/photos', '/blog/website-share']))
    return 'showcase'
  if (matches(path, ['/system/operations', '/system/logins', '/system/server', '/system/logs']))
    return 'operations'
  return 'other'
}

/**
 * Current backend roots `/site` and `/system` are navigation directories rather
 * than destinations. Their visible children are presented as stable business
 * groups; the original menu items, keys, visibility flags and route paths stay
 * intact. Any future or unrecognized backend item falls into “其他功能”.
 */
export function groupAdminMenus<T extends AdminMenuEntry>(menus: readonly T[]): Array<Omit<AdminMenuGroup, 'items'> & { items: T[] }> {
  const entries: T[] = []
  for (const item of menus) {
    if ((item.path === '/site' || item.path === '/system') && item.children?.length)
      entries.push(...item.children as T[])
    else
      entries.push(item)
  }
  const grouped = new Map<string, T[]>()

  for (const item of entries) {
    const groupKey = groupKeyForPath(item.path)
    const items = grouped.get(groupKey) ?? []
    items.push(item)
    grouped.set(groupKey, items)
  }

  return groupOrder.flatMap(({ key, title }) => {
    const items = grouped.get(key)
    return items?.length ? [{ key, title, items }] : []
  })
}
