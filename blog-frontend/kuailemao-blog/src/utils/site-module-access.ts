export const SITE_MODULES = ['home', 'experience', 'blog', 'shares', 'photos', 'about', 'diary'] as const
export type SiteModuleKey = typeof SITE_MODULES[number]
export interface SiteModuleAccess { key: SiteModuleKey; name: string; publicAccess: boolean; allowed: boolean }

export function moduleForPath(path: string): SiteModuleKey | undefined {
  if (path === '/') return 'home'
  if (path === '/diary' || path.startsWith('/diary/')) return 'diary'
  for (const [prefix, key] of [['/experience', 'experience'], ['/blog', 'blog'], ['/website-shares', 'shares'], ['/photos', 'photos'], ['/about', 'about']] as const) {
    if (path === prefix || path.startsWith(prefix + '/')) return key
  }
}

export function validateModuleAccess(value: unknown): SiteModuleAccess[] {
  // A rolling upgrade may briefly serve the old six modules. Only diary fails closed.
  if (Array.isArray(value) && value.length === 6 && !value.some(item => item?.key === 'diary'))
    value = [...value, { key: 'diary', name: '我的日记', publicAccess: false, allowed: false }]
  if (!Array.isArray(value) || value.length !== SITE_MODULES.length) throw new Error('模块访问配置不可用')
  const seen = new Set<string>()
  for (const item of value) {
    if (!item || !SITE_MODULES.includes(item.key) || seen.has(item.key)
      || typeof item.name !== 'string' || typeof item.allowed !== 'boolean' || typeof item.publicAccess !== 'boolean') throw new Error('模块访问配置不可用')
    seen.add(item.key)
  }
  return value
}
