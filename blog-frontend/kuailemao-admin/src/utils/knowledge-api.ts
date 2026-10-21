import axios from 'axios'
import { useAuthorization } from '~/composables/authorization'

export type KnowledgeKind = 'NOTE' | 'DOCUMENT' | 'CONCEPT'
export interface KnowledgeContent { kind: KnowledgeKind; title: string; body: string; sourceUrl: string; categoryId: string | null; tagIds: string[] }
export interface KnowledgeEntry { id: string; revision: number; content: KnowledgeContent; fileVersion: string | null; updatedAt: string; deletedAt: string | null }
export interface KnowledgePage<T> { records: T[]; total: number; page: number; pageSize: number }
export interface KnowledgeCapability { allowed: boolean; available: boolean; canManage: boolean; filesAvailable: boolean; message: string }
export interface KnowledgeAck { id: string; revision: number; savedAt: string; deleted: boolean }
export interface KnowledgeLabel { id: string; kind: 'CATEGORY' | 'TAG'; name: string; revision: number }
export interface KnowledgeFileInfo { version: string; filename: string; mime: string; sizeBytes: number; state: 'QUEUED' | 'RUNNING' | 'READY' | 'RETRY_WAIT' | 'FAILED' | 'NEEDS_OCR' | 'CANCELED'; errorCode: string | null }
export interface KnowledgeFileCommit { ack: KnowledgeAck; accepted: boolean; version: string | null }

const client = axios.create({ baseURL: import.meta.env.VITE_APP_BASE_API || '/api', timeout: 12000, withCredentials: false })
function token(): string {
  const saved = useAuthorization().value
  if (!saved) throw new Error('请先登录管理后台')
  try {
    const value = JSON.parse(saved) as { token?: string; expires?: string }
    if (!value.token || (value.expires && new Date(value.expires) <= new Date())) throw new Error('管理后台登录已过期')
    return value.token
  } catch { throw new Error('管理后台登录已过期，请重新登录') }
}
function sameOrigin() {
  if (new URL(client.defaults.baseURL || '/api', location.href).origin !== location.origin) throw new Error('知识库仅允许同源请求')
}
function failure(error: any): never {
  const status = Number(error.response?.status || error.status)
  if ([401, 403].includes(status)) window.dispatchEvent(new CustomEvent('knowledge-access-invalid'))
  throw Object.assign(new Error(error.response?.data?.msg || error.message || '请求未获确认'), { status })
}
export async function knowledgeRequest<T>(method: string, path: string, data?: unknown): Promise<T> {
  sameOrigin()
  try {
    const response = await client.request({ method, url: '/knowledge' + path, data, headers: { Authorization: 'Bearer ' + token(), 'Content-Type': 'application/json' } })
    if (response.data?.code !== 200) throw Object.assign(new Error(response.data?.msg || '请求未获确认'), { status: response.data?.code })
    return response.data.data as T
  } catch (error) { return failure(error) }
}
export async function knowledgeArticleOptions<T>(path: '/category/list' | '/tag/list'): Promise<T> {
  sameOrigin()
  try {
    const response = await client.get(path, { headers: { Authorization: 'Bearer ' + token() } })
    if (response.data?.code !== 200) throw new Error(response.data?.msg || '文章选项加载失败')
    return response.data.data as T
  } catch (error) { return failure(error) }
}
export async function knowledgeUploadArticleCover(file: File): Promise<string> {
  sameOrigin()
  const body = new FormData(); body.append('articleCover', file, file.name)
  try {
    const response = await client.post('/article/upload/articleCover', body, { headers: { Authorization: 'Bearer ' + token() }, timeout: 30000 })
    if (response.data?.code !== 200 || typeof response.data.data !== 'string') throw new Error(response.data?.msg || '封面上传失败')
    return response.data.data
  } catch (error) { return failure(error) }
}
export async function knowledgeUploadFile(entryId: string, revision: number, mutationId: string, file: File): Promise<KnowledgeFileCommit> {
  sameOrigin()
  const body = new FormData(); body.append('expectedRevision', String(revision)); body.append('mutationId', mutationId); body.append('file', file, file.name)
  try {
    const response = await client.post(`/knowledge/manage/entries/${entryId}/files`, body, { headers: { Authorization: 'Bearer ' + token() }, timeout: 120000 })
    if (response.data?.code !== 200) throw new Error(response.data?.msg || '文件上传未获确认')
    return response.data.data as KnowledgeFileCommit
  } catch (error) { return failure(error) }
}
export async function knowledgeDownloadFile(entryId: string, version: string, filename: string): Promise<void> {
  sameOrigin()
  try {
    const response = await client.get(`/knowledge/view/files/${entryId}/${version}/content`, { headers: { Authorization: 'Bearer ' + token() }, responseType: 'blob', timeout: 120000 })
    if (response.status !== 200) throw new Error('文件下载失败')
    const url = URL.createObjectURL(response.data)
    try { const link = document.createElement('a'); link.href = url; link.download = filename; link.click() }
    finally { setTimeout(() => URL.revokeObjectURL(url), 60000) }
  } catch (error) { return failure(error) }
}
