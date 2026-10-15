import axios from 'axios'
import { GET_TOKEN } from '@/utils/auth'
import { Jwt_Prefix } from '@/const/Jwt'
import type { DiaryAck, DiaryEntry, DiaryWrite } from './diary-save'
export interface DiaryFilter { axis?: 'created' | 'covered'; from?: string | null; to?: string | null; keyword?: string; page?: number; pageSize?: number; deleted?: boolean }
export interface DiaryPage<T> { records: T[]; total: number; page: number; pageSize: number }
export interface DiaryHistory { revision: number; title: string; body: string; coveredFrom: string | null; coveredTo: string | null; action: string; savedAt: string; sourceRevision?: number; deleted: boolean }
export type DiaryAction = { mutationId: string; expectedRevision: number; sourceRevision?: number }
const client = axios.create({ baseURL: import.meta.env.VITE_APP_BASE_API || '/api', timeout: 12000, withCredentials: false })
async function request<T>(method: string, path: string, data?: unknown, download = false): Promise<T> {
  const base = new URL(client.defaults.baseURL || '/api', window.location.href)
  if (base.origin !== window.location.origin) throw Object.assign(new Error('日记仅允许向同源接口发送凭据'), { status: 400 })
  const token = GET_TOKEN()
  if (!token) { window.dispatchEvent(new CustomEvent('diary-session-invalid', { detail: 401 })); throw Object.assign(new Error('请登录后恢复日记'), { status: 401 }) }
  try {
    const response = await client.request({ method, url: '/diary/auth' + path, data, headers: { Authorization: Jwt_Prefix + token, 'Content-Type': 'application/json' }, responseType: download ? 'blob' : 'json' })
    if (download) return response.data as T
    if (response.data?.code !== 200) throw Object.assign(new Error(response.data?.msg || '日记请求失败'), { status: response.data?.code })
    return response.data.data as T
  } catch (error: any) {
    const status = error.status || error.response?.status
    if ([401, 403].includes(Number(status)) && GET_TOKEN() === token) window.dispatchEvent(new CustomEvent('diary-session-invalid', { detail: Number(status) }))
    let message = typeof error.response?.data?.msg === 'string' ? error.response.data.msg : error.message || '日记请求未获确认'
    if (download && error.response?.data instanceof Blob && error.response.data.size < 8192) { try { message = JSON.parse(await error.response.data.text()).msg || message } catch { /* 保留安全的固定错误。 */ } }
    throw Object.assign(new Error(message), { status })
  }
}
export const diaryApi = {
  capabilities: () => request<{ allowed: boolean; available: boolean }>('GET', '/capabilities'),
  read: (id: string) => request<DiaryEntry>('GET', '/entries/' + encodeURIComponent(id)),
  save: (data: DiaryWrite) => request<DiaryAck>(data.expectedRevision === 0 ? 'POST' : 'PUT', '/entries' + (data.expectedRevision === 0 ? '' : '/' + data.entryId), data),
  search: (data: DiaryFilter) => request<DiaryPage<DiaryEntry>>('POST', '/entries/search', data),
  calendar: (month: string) => request<{ date: string; count: number }[]>('GET', '/calendar?month=' + encodeURIComponent(month)),
  history: (id: string, page = 1) => request<DiaryPage<DiaryHistory>>('GET', '/entries/' + id + '/history?page=' + page),
  remove: (id: string, action: DiaryAction) => request<DiaryAck>('DELETE', '/entries/' + id, action),
  restore: (id: string, action: DiaryAction) => request<DiaryAck>('POST', '/entries/' + id + '/restore', action),
  export: (filter: DiaryFilter, format: 'json' | 'markdown') => request<Blob>('POST', '/export', { filter, format }, true),
}
