export const BLOG_PAGE_SIZE = 9

export function parseBlogPage(value: unknown) {
  const raw = Array.isArray(value) ? value[0] : value
  if (typeof raw !== 'string' || !/^\d+$/.test(raw)) return 1
  const page = Number(raw)
  return Number.isSafeInteger(page) && page >= 1 ? page : 1
}

export function blogPageCount(listTotal: number, pageSize = BLOG_PAGE_SIZE) {
  return Math.max(1, Math.ceil(Math.max(0, listTotal) / pageSize))
}

export function clampBlogPage(page: number, listTotal: number, pageSize = BLOG_PAGE_SIZE) {
  return Math.min(Math.max(1, page), blogPageCount(listTotal, pageSize))
}

export function blogPageQuery(page: number) {
  return page > 1 ? String(page) : undefined
}

export function isCanonicalBlogPage(value: unknown) {
  const raw = Array.isArray(value) ? value[0] : value
  if (raw == null) return true
  const page = parseBlogPage(raw)
  return page > 1 && String(page) === raw
}
