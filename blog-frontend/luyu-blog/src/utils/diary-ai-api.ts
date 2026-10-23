import { diaryRequest } from './diary-api'
export interface AiScope { entryIds?: string[]; axis?: 'created' | 'covered'; from?: string; to?: string }
export interface AiRequest { requestId: string; question?: string; scope?: AiScope; snapshotDigest?: string; allowPartialIndex?: boolean }
export interface AiJob { id: string; state: string; kind: string; message: string; reportId?: string; possiblyCharged: boolean }
export interface AiSource { sourceId: string; entryId: string; revision: number; title: string; start: number; end: number; text: string; coveredFrom?: string; coveredTo?: string }
export interface AiStatement { text: string; evidenceIds: string[]; quotes: { sourceId: string; text: string }[] }
export interface AiResult { answer?: AiStatement; summary?: AiStatement; observations: AiStatement[]; hypotheses: AiStatement[]; suggestions: { action: string; reason: string; effort: string; reviewAfter: string; general: boolean; evidenceIds: string[] }[]; limitations: string[]; insufficientEvidence: boolean }
export interface AiReport { id: string; kind: string; result: string; sources: AiSource[]; model: string; createdAt: string; partial: boolean }
export interface AiPreview { entries: number; characters: number; snapshotDigest: string; records: { id: string; title: string }[]; limitation: string }
export interface AiIndex { syncing: boolean; coverage: { total: number; indexed: number; failed: number }; remainingBlocks: number; message: string }
export interface AiReportSummary { id: string; kind: string; state: string; createdAt: string; entries: number; model: string }
export interface AiConversationSummary { id: string; kind: 'QUESTION' | 'REVIEW'; firstQuestion?: string; createdAt: string; updatedAt: string }
export interface AiConversationTurn { id: string; sequence: number; question?: string; job: AiJob; report?: AiReport }
export interface AiConversationDetail { conversation: AiConversationSummary; turns: AiConversationTurn[]; total: number; page: number; canFollow: boolean }
export interface AiConversationSubmitted { conversationId: string; turnId: string; job: AiJob }
const call = <T>(method: string, path: string, data?: unknown) => diaryRequest<T>(method, '/ai' + path, data)
export const diaryAiApi = {
  capabilities: () => call<{ enabled: boolean; available: boolean; message: string }>('GET', '/capabilities'),
  index: () => call<AiIndex>('GET', '/index'),
  indexAction: (start: boolean, requestId: string, retryFailed = false) => call<AiJob>('POST', start ? '/index' : '/index/pause', { requestId, retryFailed }),
  preview: (scope: AiScope) => call<AiPreview>('POST', '/reviews/preview', scope),
  submit: (kind: 'questions' | 'reviews', body: AiRequest) => call<AiJob>('POST', '/' + kind, body),
  job: (id: string) => call<AiJob>('GET', '/jobs/' + encodeURIComponent(id)),
  cancel: (id: string) => call<AiJob>('POST', '/jobs/' + encodeURIComponent(id) + '/cancel'),
  reports: (page: number) => call<{ records: AiReportSummary[]; total: number }>('GET', '/reports?page=' + page),
  report: (id: string) => call<AiReport>('GET', '/reports/' + encodeURIComponent(id)),
  remove: (id: string) => call<void>('DELETE', '/reports/' + encodeURIComponent(id)),
  conversations: (kind: 'QUESTION' | 'REVIEW', page = 1) => call<{ records: AiConversationSummary[]; total: number; page: number; pageSize: number }>('GET', '/conversations?kind=' + kind + '&page=' + page),
  conversation: (id: string, page = 1) => call<AiConversationDetail>('GET', '/conversations/' + encodeURIComponent(id) + '?page=' + page),
  startConversation: (kind: 'questions' | 'reviews', request: AiRequest) => call<AiConversationSubmitted>('POST', '/conversations/' + kind, { request }),
  followup: (id: string, requestId: string, question: string) => call<AiConversationSubmitted>('POST', '/conversations/' + encodeURIComponent(id) + '/turns', { requestId, question }),
  removeConversation: (id: string) => call<void>('DELETE', '/conversations/' + encodeURIComponent(id)),
}
