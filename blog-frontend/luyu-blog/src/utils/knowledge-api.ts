import axios from 'axios'
import { GET_TOKEN } from '@/utils/auth'
import { Jwt_Prefix } from '@/const/Jwt'

export type KnowledgeKind = 'NOTE' | 'DOCUMENT' | 'CONCEPT'
export interface KnowledgeContent { kind: KnowledgeKind; title: string; body: string; sourceUrl: string; categoryId: string | null; tagIds: string[] }
export interface KnowledgeEntry { id: string; revision: number; content: KnowledgeContent; fileVersion: string | null; updatedAt: string; deletedAt: string | null }
export interface KnowledgePage<T> { records: T[]; total: number; page: number; pageSize: number }
export interface KnowledgeCapability { allowed: boolean; available: boolean; canManage: boolean; filesAvailable: boolean; message: string }
export interface KnowledgeAck { id: string; revision: number; savedAt: string; deleted: boolean }
export interface KnowledgeLabel { id: string; kind: 'CATEGORY' | 'TAG'; name: string; parentCategoryId: string | null; revision: number }
export interface KnowledgeFileInfo { version: string; filename: string; mime: string; sizeBytes: number; state: 'QUEUED' | 'RUNNING' | 'READY' | 'RETRY_WAIT' | 'FAILED' | 'NEEDS_OCR' | 'CANCELED'; errorCode: string | null }
export interface KnowledgeFileCommit { ack: KnowledgeAck; accepted: boolean; version: string | null }
const client = axios.create({ baseURL: import.meta.env.VITE_APP_BASE_API || '/api', timeout: 12000, withCredentials: false })
export async function knowledgeRequest<T>(method: string, path: string, data?: unknown): Promise<T> {
  if (!path.startsWith('/view/') || (method.toUpperCase() !== 'GET' && !(method.toUpperCase() === 'POST' && ['/view/search', '/view/relations/graph', '/view/relations/neighbors'].some(prefix => path.split('?')[0] === prefix))))
    throw new Error('前台知识库仅支持阅读；请到管理后台维护')
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

export async function knowledgeDownloadFile(entryId: string, version: string, filename: string): Promise<void> {
  const token = GET_TOKEN()
  if (new URL(client.defaults.baseURL || '/api', location.href).origin !== location.origin) throw new Error('知识库仅允许同源下载')
  const response = await client.get(`/knowledge/view/files/${entryId}/${version}/content`, { headers: { ...(token ? { Authorization: Jwt_Prefix + token } : {}) }, responseType: 'blob', timeout: 120000 })
  if (response.status !== 200) throw new Error('文件下载失败')
  const url = URL.createObjectURL(response.data)
  try { const link = document.createElement('a'); link.href = url; link.download = filename; link.click() }
  finally { setTimeout(() => URL.revokeObjectURL(url), 60000) }
}

export async function knowledgeImageBlob(imageId: string): Promise<Blob> {
  const token = GET_TOKEN()
  if (new URL(client.defaults.baseURL || '/api', location.href).origin !== location.origin) throw new Error('知识库仅允许同源读取')
  try {
    const response = await client.get(`/knowledge/view/images/${imageId}/content`, { headers: { ...(token ? { Authorization: Jwt_Prefix + token } : {}) }, responseType: 'blob', timeout: 30000 })
    return response.data as Blob
  } catch (failure: any) {
    const status = Number(failure.status || failure.response?.status)
    if ([401, 403].includes(status) && GET_TOKEN() === token) window.dispatchEvent(new CustomEvent('knowledge-access-invalid'))
    throw new Error(failure.response?.data?.msg || failure.message || '图片读取失败')
  }
}
