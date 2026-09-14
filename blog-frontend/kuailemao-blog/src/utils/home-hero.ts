interface HeroConfig {
  webmasterName?: string
  heroKicker?: string
  heroTitle?: string
  heroSubtitle?: string
  heroDescription?: string
  heroPrimaryText?: string
  heroPrimaryUrl?: string
  heroSecondaryText?: string
  heroSecondaryUrl?: string
  heroAsideLabel?: string
  heroAsideText?: string
}

const DEFAULT_HERO = {
  kicker: '郑陆宇 · 后端 / 全栈方向',
  title: '你好，我是郑陆宇。',
  subtitle: '专注后端与全栈开发，用技术笔记和项目复盘记录真实问题的解决过程。',
  description: '这里既是我的个人博客，也是持续更新的求职作品集。你可以从文章了解我的技术思考，从工作经历了解我的实践与协作。',
}

const DEFAULT_ACTIONS = {
  primary: { text: '阅读博客', href: '/blog', external: false },
  secondary: { text: '查看工作经历', href: '/experience', external: false },
}

export function safeHeroUrl(value = '') {
  const url = value.trim()
  // Allow ordinary site paths and HTTP(S) destinations only.
  return /[\s\\\u0000-\u001f]/.test(url) ? '' : (/^\/(?!\/)|^https?:\/\//i.test(url) ? url : '')
}

export function resolveHomeHero(info?: HeroConfig) {
  const action = (text?: string, url?: string) => {
    const href = safeHeroUrl(url)
    return text?.trim() && href ? { text: text.trim(), href, external: /^https?:\/\//i.test(href) } : null
  }
  const primary = action(info?.heroPrimaryText, info?.heroPrimaryUrl)
  const secondary = action(info?.heroSecondaryText, info?.heroSecondaryUrl)
  const hasConfiguredAction = Boolean(primary || secondary)

  return {
    identityName: info?.webmasterName?.trim() || '郑陆宇',
    identityRole: '后端 / 全栈开发',
    kicker: info?.heroKicker?.trim() || DEFAULT_HERO.kicker,
    title: info?.heroTitle?.trim() || DEFAULT_HERO.title,
    subtitle: info?.heroSubtitle?.trim() || DEFAULT_HERO.subtitle,
    description: info?.heroDescription?.trim() || DEFAULT_HERO.description,
    primary: hasConfiguredAction ? primary : DEFAULT_ACTIONS.primary,
    secondary: hasConfiguredAction ? secondary : DEFAULT_ACTIONS.secondary,
    asideLabel: info?.heroAsideLabel?.trim() || '',
    asideText: info?.heroAsideText?.trim() || '',
  }
}
