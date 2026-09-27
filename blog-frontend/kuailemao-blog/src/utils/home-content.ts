export interface HomeResource<T> {
  status: 'idle' | 'loading' | 'success' | 'error'
  data: T | null
}

export function createHomeResource<T>(): HomeResource<T> {
  return { status: 'idle', data: null }
}

// Pass a reactive resource from Vue. Each request publishes independently.
export async function loadHomeResource<T>(
  resource: HomeResource<T>,
  request: () => Promise<T>,
  isActive: () => boolean = () => true,
) {
  if (!isActive() || resource.status === 'loading') return
  resource.status = 'loading'
  try {
    const data = await request()
    if (!isActive()) return
    resource.data = data
    resource.status = 'success'
  }
  catch {
    if (isActive()) resource.status = 'error'
  }
}

export interface HomeArticle {
  id: number | string
  articleTitle: string
  articleContent?: string
  articleCover?: string
  categoryName?: string
  createTime?: string
}

export function articleItems(value: unknown): HomeArticle[] {
  if (!Array.isArray(value)) throw new Error('Invalid article response')
  return value.filter(item => item && articleKey(item.id) && typeof item.articleTitle === 'string')
}

function articleKey(id: unknown): string {
  if (typeof id === 'number') return Number.isFinite(id) ? String(id) : ''
  return typeof id === 'string' ? id.trim() : ''
}

export function articlePage(value: unknown) {
  const payload = value as { page?: unknown; total?: unknown } | null
  const articles = articleItems(payload?.page)
  if (payload?.total === null || payload?.total === undefined || payload?.total === '') throw new Error('Missing article total')
  const total = Number(payload.total)
  if (!Number.isInteger(total) || total < 0) throw new Error('Invalid article total')
  return { articles, total }
}

export function selectHomeArticles(latest: HomeArticle[], recommended: HomeArticle[]) {
  const seen = new Set<string>()
  const featuredItems = articleItems(recommended).filter(item => {
    const id = articleKey(item.id)
    if (seen.has(id)) return false
    seen.add(id)
    return true
  }).slice(0, 1)
  if (!featuredItems.length) featuredItems.push(...articleItems(latest).slice(0, 1))
  seen.clear()
  featuredItems.forEach(item => seen.add(articleKey(item.id)))
  const articles = [...articleItems(recommended), ...articleItems(latest)].filter(item => {
    const id = articleKey(item.id)
    if (!id || seen.has(id)) return false
    seen.add(id)
    return true
  }).slice(0, 5)
  return { featured: featuredItems[0], featuredItems, articles }
}

export function homeCount(status: HomeResource<unknown>['status'], count?: number) {
  if (count !== undefined) return String(count)
  return status === 'error' ? '暂不可用' : '加载中'
}

export function firstBanner(value: unknown): string {
  if (!Array.isArray(value)) throw new Error('Invalid banner response')
  return value.find(item => typeof item === 'string' && item.trim())?.trim() || ''
}

export function homeExcerpt(value = '', limit = 96) {
  let fence = ''
  const prose = value.split(/\r?\n/).filter(line => {
    const marker = line.match(/^\s{0,3}(`{3,}|~{3,})/)
    if (marker) {
      if (!fence) fence = marker[1]
      else if (marker[1][0] === fence[0] && marker[1].length >= fence.length) fence = ''
      return false
    }
    return !fence
  }).join(' ')
  const text = prose.replace(/<[^>]*>/g, ' ').replace(/!\[[^\]]*\]\([^)]*\)/g, '')
    .replace(/\[([^\]]+)\]\([^)]*\)/g, '$1').replace(/[*#>`~|]/g, '').replace(/\s+/g, ' ').trim()
  return text.length > limit ? `${text.slice(0, limit)}…` : text
}
