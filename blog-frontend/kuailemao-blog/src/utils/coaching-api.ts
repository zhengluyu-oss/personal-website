import { diaryRequest } from './diary-api'
export type CoachingKind = 'goals' | 'actions' | 'feedback'
export interface CoachingRow { kind: string; id: string; parentId?: string; revision: number; content: Record<string, any>; createdAt: string; updatedAt: string; deletedAt?: string }
export interface CoachingWrite { id: string; mutationId: string; expectedRevision: number; content: Record<string, any> | null; origin?: CoachingOrigin; completeActionRevision?: number }
export interface CoachingOrigin { type: 'AI_REPORT' | 'PERIOD_REPORT'; reportId: string; suggestionIndex: number; suggestionHash: string }
export interface CoachingAck { id: string; mutationId: string; revision: number; savedAt: string }
export interface CoachingJob { id: string; state: string; message: string; reportId?: string; possiblyCharged: boolean; createdAt: string }
export interface CoachingReport { id: string; result: string; window: CoachingPreview['window']; sources: CoachingSource[]; model: string; createdAt: string }
export interface CoachingReportSummary { id: string; state: string; window: CoachingPreview['window']; model: string; createdAt: string }
export interface CoachingAiRequest { requestId: string; period: CoachingPeriod; snapshotDigest: string }
export interface CoachingPeriod { type: 'WEEK' | 'MONTH' | 'CUSTOM'; anchor?: string; from?: string; to?: string; axis: 'covered' | 'created'; goalIds: string[]; includeUndated: boolean }
export interface CoachingSource { sourceId: string; kind: string; entityId: string; revision: number; field: string; start: number; end: number; sha256: string; text: string }
export interface CoachingPreview { window: { type: string; from: string; to: string; axis: string; unfinished: boolean; asOf: string }; diaries: Record<string, any>[]; goals: CoachingRow[]; actions: CoachingRow[]; feedback: CoachingRow[]; statistics: { feedback: number; tried: number; completed: number; skipped: number; helpful: number; notHelpful: number; unsure: number; notEvaluated: number; evaluable: number; helpfulPercent: number | null; undated: number; explanation: string }; sources: CoachingSource[]; characters: number; withinBudget: boolean; snapshotDigest: string; limitations: string[] }
const call = <T>(method: string, path: string, data?: unknown, download = false) => diaryRequest<T>(method, '/coaching' + path, data, download)
export const coachingApi = {
  capabilities: () => call<{ enabled: boolean; available: boolean; aiAvailable: boolean; message: string }>('GET', '/capabilities'),
  list: (kind: CoachingKind, parent = '', page = 1) => call<{ records: CoachingRow[]; total: number }>('GET', '/' + kind + '?size=100&page=' + page + (parent ? '&parent=' + encodeURIComponent(parent) : '')),
  read: (kind: CoachingKind, id: string) => call<CoachingRow>('GET', '/' + kind + '/' + encodeURIComponent(id)),
  write: (kind: CoachingKind, payload: CoachingWrite, remove = false) => call<CoachingAck>(remove ? 'DELETE' : payload.expectedRevision ? 'PUT' : 'POST', '/' + kind + (remove || payload.expectedRevision ? '/' + encodeURIComponent(payload.id) : ''), payload),
  history: (kind: CoachingKind, id: string, page = 1) => call<{ records: { revision: number; content: Record<string, any>; savedAt: string; deletedAt?: string }[]; total: number }>('GET', '/' + kind + '/' + encodeURIComponent(id) + '/history?page=' + page),
  origin: (id: string) => call<string>('GET', '/actions/' + encodeURIComponent(id) + '/origin'),
  suggestion: (origin: CoachingOrigin) => call<Record<string, any>>('POST', '/suggestion', origin),
  preview: (period: CoachingPeriod) => call<CoachingPreview>('POST', '/periods/preview', period),
  submit: (body: CoachingAiRequest) => call<CoachingJob>('POST', '/periods/jobs', body),
  job: (id: string) => call<CoachingJob>('GET', '/periods/jobs/' + encodeURIComponent(id)),
  cancel: (id: string) => call<CoachingJob>('POST', '/periods/jobs/' + encodeURIComponent(id) + '/cancel'),
  reports: (page = 1) => call<{ records: CoachingReportSummary[]; total: number }>('GET', '/periods/reports?page=' + page),
  report: (id: string) => call<CoachingReport>('GET', '/periods/reports/' + encodeURIComponent(id)),
  removeReport: (id: string) => call<void>('DELETE', '/periods/reports/' + encodeURIComponent(id)),
  export: () => call<Blob>('POST', '/export', undefined, true),
}
