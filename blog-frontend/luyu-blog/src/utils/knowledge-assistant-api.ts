import axios from 'axios'
import { GET_TOKEN } from '@/utils/auth'
import { Jwt_Prefix } from '@/const/Jwt'

export interface AssistantCapability { available: boolean; message: string }
export interface AssistantSource { id: string; entryId: string; revision: number; fileVersion: string | null; title: string; excerpt: string; page: number | null; fromLine: number | null; toLine: number | null; url: string }
export interface AssistantClaim { text: string; quote: string; sourceId: string }
export interface AssistantTurn { question: string; answer: string }
export interface AssistantAnswer { state: 'ANSWERED' | 'NO_EVIDENCE'; answer: string; sources: AssistantSource[]; claims: AssistantClaim[] }

const client = axios.create({ baseURL: import.meta.env.VITE_APP_BASE_API || '/api', timeout: 25000, withCredentials: false })
async function call<T>(method: 'GET' | 'POST', path: string, data?: unknown): Promise<T> {
  if (new URL(client.defaults.baseURL || '/api', location.href).origin !== location.origin) throw new Error('客服仅允许同源请求')
  const token = GET_TOKEN()
  try {
    const response = await client.request({ method, url: `/knowledge/view/assistant/${path}`, data,
      headers: { ...(token ? { Authorization: Jwt_Prefix + token } : {}), 'Content-Type': 'application/json' } })
    if (response.data?.code !== 200) throw Object.assign(new Error(response.data?.msg || '客服暂不可用'), { status: response.data?.code })
    return response.data.data as T
  } catch (failure: any) {
    const status = Number(failure.status || failure.response?.status)
    if ([401, 403].includes(status) && GET_TOKEN() === token) window.dispatchEvent(new CustomEvent('knowledge-access-invalid'))
    throw Object.assign(new Error(failure.response?.data?.msg || failure.message || '客服暂不可用'), { status })
  }
}
export const assistantCapability = () => call<AssistantCapability>('GET', 'capabilities')
export const askKnowledgeAssistant = (question: string, history: AssistantTurn[] = []) => call<AssistantAnswer>('POST', 'ask', { question, history })
