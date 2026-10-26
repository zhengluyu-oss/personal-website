import { SITE_NAME, SITE_TITLE } from '@/config/site'

export interface SeoMeta { title: string; description: string; keywords: string }

const fixedSeo: Record<string, SeoMeta> = {
  home: { title: SITE_TITLE, description: '陆屿的个人网站，分享前后端开发、项目实践、技术文章、知识整理与成长记录。', keywords: '陆屿,个人网站,技术博客,知识库,Java,Spring Boot,Vue,项目复盘' },
  blog: { title: '个人博客 | 陆屿', description: '浏览陆屿发布的技术文章、项目复盘与成长记录。', keywords: '个人博客,技术文章,项目复盘,陆屿' },
  experience: { title: '工作经历 | 陆屿', description: '了解陆屿的工作经历、项目职责、技术实践与职业成长。', keywords: '陆屿,工作经历,项目经验,软件开发' },
  category: { title: `博客主题 | ${SITE_NAME}`, description: '按主题浏览陆屿的技术笔记、项目实践与开发经验。', keywords: '博客主题,技术笔记,开发经验,陆屿' },
  tags: { title: `文章标签 | ${SITE_NAME}`, description: '通过标签快速发现感兴趣的技术主题与项目记录。', keywords: '文章标签,技术主题,项目记录' },
  tag: { title: `文章标签 | ${SITE_NAME}`, description: '通过标签快速发现感兴趣的技术主题与项目记录。', keywords: '文章标签,技术主题,项目记录' },
  treeHole: { title: `心灵树洞 | ${SITE_NAME}`, description: '记录与分享生活中的想法、感受和温暖片段。', keywords: '心灵树洞,生活记录,随想' },
  messageList: { title: `留言板 | ${SITE_NAME}`, description: '欢迎在陆屿留言交流。', keywords: '留言板,博客交流,陆屿' },
  link: { title: `友情链接 | ${SITE_NAME}`, description: '发现值得访问的个人网站与优质博客。', keywords: '友情链接,个人博客,优质网站' },
  music: { title: `音乐空间 | ${SITE_NAME}`, description: '在音乐中短暂停留，分享喜欢的声音。', keywords: '音乐,歌单,个人博客' },
  about: { title: `关于我 | ${SITE_NAME}`, description: '关于陆屿、这个网站以及背后的故事。', keywords: '关于陆屿,个人介绍,网站介绍' },
  photo: { title: `个人相册 | ${SITE_NAME}`, description: '用照片保存生活、旅途与值得纪念的瞬间。', keywords: '个人相册,摄影,生活记录' },
}

export function setSeoMeta(seo: SeoMeta, canonicalPath = window.location.pathname) {
  document.title = seo.title
  const set = (name: string, content: string) => {
    let element = document.head.querySelector<HTMLMetaElement>(`meta[name="${name}"]`)
    if (!element) { element = document.createElement('meta'); element.name = name; document.head.appendChild(element) }
    element.content = content
  }
  set('description', seo.description)
  set('keywords', seo.keywords)
  let canonical = document.head.querySelector<HTMLLinkElement>('link[rel="canonical"]')
  if (!canonical) {
    canonical = document.createElement('link')
    canonical.rel = 'canonical'
    document.head.appendChild(canonical)
  }
  canonical.href = `${window.location.origin}${canonicalPath}`
}

export function applyFixedSeo(routeName?: string | symbol | null, fallbackTitle = '', canonicalPath?: string) {
  const seo = fixedSeo[String(routeName)]
  setSeoMeta(seo ?? { title: fallbackTitle || SITE_NAME, description: '陆屿的个人网站。', keywords: '陆屿,个人网站' }, canonicalPath)
}
