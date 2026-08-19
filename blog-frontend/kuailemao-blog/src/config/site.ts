/** 站点品牌与 OSS 公共前缀（方案一：仅展示层，不改 Java 包名/目录名） */
export const SITE_NAME = '郑陆宇的个人博客'
export const SITE_TITLE = `${SITE_NAME} | 求职 · 技术笔记 · 项目复盘`
export const SITE_AUTHOR = '郑陆宇'
export const SITE_SLUG = 'zhengluyu'

export const GITHUB_URL = 'https://github.com/zhengluyu-oss'
export const GITEE_URL = 'https://github.com/zhengluyu-oss/personal-website'

/** 阿里云 OSS 公共读前缀（Bucket: zhengluyu，华北2） */
export const OSS_CDN_BASE = 'https://zhengluyu.oss-cn-beijing.aliyuncs.com'

export const ossUrl = (path: string) =>
  `${OSS_CDN_BASE}/${path.replace(/^\//, '')}`
