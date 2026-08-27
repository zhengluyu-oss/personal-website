export const ARTICLE_PAGE_SIZE_OPTIONS = ['10', '20', '50']

export function normalizeArticlePageSize(value: number) {
  return [10, 20, 50].includes(value) ? value : 10
}

export function articleLastPage(total: number, pageSize: number) {
  return Math.max(1, Math.ceil(Math.max(0, total) / normalizeArticlePageSize(pageSize)))
}

export function validArticlePage(current: number, total: number, pageSize: number) {
  return Math.min(Math.max(1, current), articleLastPage(total, pageSize))
}
