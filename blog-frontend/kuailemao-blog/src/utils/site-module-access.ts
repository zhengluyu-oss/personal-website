export const SITE_MODULES = ['home', 'experience', 'blog', 'shares', 'photos', 'about'] as const
export type SiteModuleKey = typeof SITE_MODULES[number]
export interface SiteModuleAccess { key: SiteModuleKey; name: string; publicAccess: boolean; allowed: boolean }

export function moduleForPath(path: string): SiteModuleKey | undefined {
  if (path === '/') return 'home'
  for (const [prefix, key] of [['/experience', 'experience'], ['/blog', 'blog'], ['/website-shares', 'shares'], ['/photos', 'photos'], ['/about', 'about']] as const) {
    if (path === prefix || path.startsWith(prefix + '/')) return key
  }
}

export function validateModuleAccess(value: unknown): SiteModuleAccess[] {
  if (!Array.isArray(value) || value.length !== SITE_MODULES.length) throw new Error('模块访问配置不可用')
  const seen = new Set<string>()
  for (const item of value) {
    if (!item || !SITE_MODULES.includes(item.key) || seen.has(item.key)
      || typeof item.name !== 'string' || typeof item.allowed !== 'boolean' || typeof item.publicAccess !== 'boolean') throw new Error('模块访问配置不可用')
    seen.add(item.key)
  }
  return value
}
