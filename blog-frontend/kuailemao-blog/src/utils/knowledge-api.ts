import axios from 'axios'
import { GET_TOKEN } from '@/utils/auth'
import { Jwt_Prefix } from '@/const/Jwt'

export type KnowledgeKind = 'NOTE' | 'DOCUMENT' | 'CONCEPT'
export interface KnowledgeContent { kind: KnowledgeKind; title: string; body: string; sourceUrl: string; categoryId: string | null; tagIds: string[] }
export interface KnowledgeEntry { id: string; revision: number; content: KnowledgeContent; fileVersion: string | null; updatedAt: string; deletedAt: string | null }
export interface KnowledgePage<T> { records: T[]; total: number; page: number; pageSize: number }
export interface KnowledgeCapability { allowed: boolean; available: boolean; canManage: boolean; filesAvailable: boolean; message: string }
export interface KnowledgeAck { id: string; revision: number; savedAt: string; deleted: boolean }
export interface KnowledgeLabel { id: string; kind: 'CATEGORY' | 'TAG'; name: string; revision: number }
const client = axios.create({ baseURL: import.meta.env.VITE_APP_BASE_API || '/api', timeout: 12000, withCredentials: false })
export async function knowledgeRequest<T>(method: string, path: string, data?: unknown): Promise<T> {
  const token = GET_TOKEN()
  if (new URL(client.defaults.baseURL || '/api', location.href).origin !== location.origin) throw new Error('知识库仅允许同源请求')
  try {
    const r = await client.request({ method, url: '/knowledge' + path, data, headers: { ...(token ? { Authorization: Jwt_Prefix + token } : {}), 'Content-Type': 'application/json' } })
    if (r.data?.code !== 200) throw Object.assign(new Error(r.data?.msg || '请求未获确认'), { status: r.data?.code })
    return r.data.data as T
  } catch (failure: any) {
    const status = Number(failure.status || failure.response?.status)
    if ([401, 403].includes(status) && GET_TOKEN() === token) window.dispatchEvent(new CustomEvent('knowledge-access-invalid'))
    throw Object.assign(new Error(failure.response?.data?.msg || failure.message || '请求未获确认，请保留编辑'), { status })
  }
}

export async function knowledgeArticleOptions<T>(path: '/category/list' | '/tag/list'): Promise<T> {
  if (new URL(client.defaults.baseURL || '/api', location.href).origin !== location.origin) throw new Error('知识库仅允许同源请求')
  const r = await client.get(path)
  if (r.data?.code !== 200) throw new Error(r.data?.msg || '文章选项加载失败')
  return r.data.data as T
}

export async function knowledgeUploadArticleCover(file: File): Promise<string> {
  if (new URL(client.defaults.baseURL || '/api', location.href).origin !== location.origin) throw new Error('知识库仅允许同源请求')
  const token = GET_TOKEN()
  if (!token) throw new Error('请先登录再上传封面')
  const body = new FormData()
  body.append('articleCover', file, file.name)
  const r = await client.post('/article/upload/articleCover', body,
    { headers: { Authorization: Jwt_Prefix + token }, timeout: 30000 })
  if (r.data?.code !== 200 || typeof r.data.data !== 'string') throw new Error(r.data?.msg || '封面上传失败')
  return r.data.data
}
