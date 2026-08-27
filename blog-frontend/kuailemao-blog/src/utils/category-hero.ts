export interface CategoryHeroSource {
  heroEyebrow?: string | null
  heroTitleAccent?: string | null
  heroTitle?: string | null
  heroDescription?: string | null
}

export interface CategoryHeroCopy {
  eyebrow: string
  titleAccent: string
  title: string
  description: string
}

export const DEFAULT_BLOG_HERO: Readonly<CategoryHeroCopy> = Object.freeze({
  eyebrow: 'CODE · NOTES · PRACTICE',
  titleAccent: '在代码之外，',
  title: '记录思考发生的地方。',
  description: '技术实践、工具研究与项目复盘。这里收录我在构建产品、解决问题和持续学习过程中留下的完整记录。',
})

function configured(value: string | null | undefined, fallback: string) {
  const normalized = typeof value === 'string' ? value.trim() : ''
  return normalized || fallback
}

export function resolveCategoryHero(source?: CategoryHeroSource | null): CategoryHeroCopy {
  return {
    eyebrow: configured(source?.heroEyebrow, DEFAULT_BLOG_HERO.eyebrow),
    titleAccent: configured(source?.heroTitleAccent, DEFAULT_BLOG_HERO.titleAccent),
    title: configured(source?.heroTitle, DEFAULT_BLOG_HERO.title),
    description: configured(source?.heroDescription, DEFAULT_BLOG_HERO.description),
  }
}
