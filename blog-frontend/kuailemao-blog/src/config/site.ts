/** 站点品牌与 OSS 公共前缀（方案一：仅展示层，不改 Java 包名/目录名） */
export const SITE_NAME = '郑陆宇的个人博客'
export const SITE_TITLE = `${SITE_NAME} | 求职 · 技术笔记 · 项目复盘`
export const SITE_AUTHOR = '郑陆宇'
export const SITE_SLUG = 'zhengluyu'

export const GITHUB_URL = 'https://github.com/zhengluyu-oss'
export const GITHUB_REPO_URL = 'https://github.com/zhengluyu-oss/personal-website'
/** @deprecated 兼容旧引用，等同仓库地址 */
export const GITEE_URL = GITHUB_REPO_URL

/** About / 欢迎页文案 */
export const ABOUT_HEADLINE = '后端 / 全栈方向 · 求职中'
export const ABOUT_TAGLINE = '用作品说话：技术笔记 · 项目复盘 · 持续学习'
export const ABOUT_BIO =
  '你好，我是郑陆宇。这里记录我在后端与全栈方向的学习与实践，包括项目拆解、问题排查与面试准备相关笔记。欢迎通过 GitHub 了解更多代码与项目。'
export const WELCOME_TITLE = `欢迎来到${SITE_NAME}`
export const WELCOME_SUBTITLE = '技术笔记 · 项目复盘 · 求职作品集'

/** 侧栏打赏榜：个人站默认关闭，避免模板假数据 */
export const SHOW_CHARGING = false

/** 阿里云 OSS 公共读前缀（Bucket: zhengluyu，华北2） */
export const OSS_CDN_BASE = 'https://zhengluyu.oss-cn-beijing.aliyuncs.com'

export const ossUrl = (path: string) =>
  `${OSS_CDN_BASE}/${path.replace(/^\//, '')}`
