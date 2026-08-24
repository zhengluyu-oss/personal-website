import { pinyin } from 'pinyin-pro'

export interface SluggableCategory {
  id: number
  categoryName: string
}

export interface CategorySlugEntry<T extends SluggableCategory = SluggableCategory> {
  category: T
  categoryId: number
  slug: string
  path: string
}

export const BLOG_RESERVED_SLUGS = new Set([
  'articles',
  'archive',
  'categories',
  'tags',
])

const normalizePart = (value: string) => value
  .toLowerCase()
  .replace(/[^a-z0-9]+/g, '-')
  .replace(/-{2,}/g, '-')
  .replace(/^-|-$/g, '')

export function categoryBaseSlug(categoryName: string) {
  const parts: string[] = []
  let englishCaptured = false

  for (const match of categoryName.matchAll(/\p{Script=Han}+|[A-Za-z0-9]+/gu)) {
    const segment = match[0]
    if (/^\p{Script=Han}+$/u.test(segment)) {
      const initials = pinyin(segment, { pattern: 'first', toneType: 'none', type: 'array' }).join('')
      const normalized = normalizePart(initials)
      if (normalized) parts.push(normalized)
      continue
    }

    if (!englishCaptured) {
      const normalized = normalizePart(segment)
      if (normalized) parts.push(normalized)
      englishCaptured = true
    }
  }

  return normalizePart(parts.join('-'))
}

export function buildCategorySlugEntries<T extends SluggableCategory>(categories: T[]): CategorySlugEntry<T>[] {
  const baseSlugs = categories.map(category => categoryBaseSlug(category.categoryName))
  const baseCounts = baseSlugs.reduce((counts, slug) => {
    if (slug) counts.set(slug, (counts.get(slug) || 0) + 1)
    return counts
  }, new Map<string, number>())
  const used = new Set<string>()

  return categories.map((category, index) => {
    const base = baseSlugs[index]
    let slug = !base
      ? `category-${category.id}`
      : BLOG_RESERVED_SLUGS.has(base) || (baseCounts.get(base) || 0) > 1
        ? `${base}-${category.id}`
        : base

    if (used.has(slug)) slug = `${slug}-${category.id}`
    used.add(slug)

    return {
      category,
      categoryId: category.id,
      slug,
      path: `/blog/${slug}`,
    }
  })
}

export function resolveCategorySlug<T extends SluggableCategory>(categories: T[], slug: string) {
  return buildCategorySlugEntries(categories).find(entry => entry.slug === slug)
}

export function categoryRoute<T extends SluggableCategory>(categories: T[], categoryId: number | string) {
  return buildCategorySlugEntries(categories).find(entry => String(entry.categoryId) === String(categoryId))?.path || '/blog'
}
