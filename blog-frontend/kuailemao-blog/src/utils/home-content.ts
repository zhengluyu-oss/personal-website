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
  return value.filter(item => item && (typeof item.id === 'number' || typeof item.id === 'string') && typeof item.articleTitle === 'string')
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
  const featured = recommended[0] || latest[0]
  const seen = new Set<string>(featured ? [String(featured.id)] : [])
  const articles = latest.filter(item => {
    const id = String(item.id)
    if (seen.has(id)) return false
    seen.add(id)
    return true
  }).slice(0, 5)
  return { featured, articles }
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
  const text = value.replace(/<[^>]*>/g, ' ').replace(/!\[[^\]]*\]\([^)]*\)/g, '')
    .replace(/\[([^\]]+)\]\([^)]*\)/g, '$1').replace(/[*#>`~|]|\s+/g, ' ').trim()
  return text.length > limit ? `${text.slice(0, limit)}…` : text
}
