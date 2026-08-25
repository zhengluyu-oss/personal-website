export interface BlogFeedPayload<T extends { id: number | string }> {
  featuredArticle?: T | null
  articles?: T[] | null
  total?: number | string | null
}

export function normalizeBlogFeed<T extends { id: number | string }>(payload?: BlogFeedPayload<T> | null) {
  const featuredArticle = payload?.featuredArticle || undefined
  const articles = Array.isArray(payload?.articles)
    ? payload.articles.filter(article => String(article.id) !== String(featuredArticle?.id ?? ''))
    : []
  return {
    featuredArticle,
    articles,
    total: Number(payload?.total || 0),
  }
}
