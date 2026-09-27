import http from '@/utils/http.ts'

export interface WebsiteShareItem {
  id: number
  title: string
  siteUrl: string
  summary: string
  coverImage?: string
  content?: string
  seoTitle?: string
  seoDescription?: string
  seoKeywords?: string
  createTime?: string
  updateTime?: string
}

export function websiteShareList() { return http.get('/website-share/list') }
export function getWebsiteShare(id: string) { return http.get(`/website-share/${encodeURIComponent(id)}`) }
export function getLegacyWebsiteShareId(id: string) { return http.get(`/website-share/from-article/${encodeURIComponent(id)}`) }
