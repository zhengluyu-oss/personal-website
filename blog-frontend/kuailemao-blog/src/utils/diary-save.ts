export interface DiaryContent { title: string; body: string; coveredFrom: string | null; coveredTo: string | null }
export interface DiaryEntry extends DiaryContent { id: string; revision: number; createdAt: string; updatedAt: string; deletedAt?: string | null }
export interface DiaryWrite extends DiaryContent { entryId: string; mutationId: string; expectedRevision: number }
export interface DiaryAck { entryId: string; mutationId: string; revision: number; savedAt: string }
export type SaveState = 'clean' | 'dirty' | 'saving' | 'failed' | 'conflict' | 'locked'
export const emptyDiary = (): DiaryContent => ({ title: '', body: '', coveredFrom: null, coveredTo: null })
export function diaryValidation(content: DiaryContent): string {
  if (Array.from(content.title).length > 120 || Array.from(content.body).length > 100000) return '标题最多120字，正文最多100000字'
  if (Boolean(content.coveredFrom) !== Boolean(content.coveredTo) || content.coveredFrom && content.coveredTo && content.coveredFrom > content.coveredTo) return '请完整填写起止日期，开始日期不能晚于结束日期'
  for (const value of [content.coveredFrom, content.coveredTo]) if (value && (!/^\d{4}-\d{2}-\d{2}$/.test(value) || !Number.isFinite(Date.parse(value)) || new Date(value).toISOString().slice(0, 10) !== value)) return '记录日期无效'
  return ''
}

/** 仅保留内存快照。每篇串行写入，超时重试原请求，迟到响应按编辑代次隔离。 */
export class DiarySaver {
  id = ''; content = emptyDiary(); revision = 0; state: SaveState = 'clean'; message = ''; savedAt = ''
  private sequence = 0; private savedSequence = 0; private epoch = 0; private locked = false
  private pending?: { request: DiaryWrite; sequence: number }
  private flight?: Promise<void>; private timer?: ReturnType<typeof setTimeout>; private maximum?: ReturnType<typeof setTimeout>
  private retries = 0; private send: (request: DiaryWrite) => Promise<DiaryAck>; private changed: () => void
  constructor(send: (request: DiaryWrite) => Promise<DiaryAck>, changed: () => void) { this.send = send; this.changed = changed }
  get dirty() { return this.sequence !== this.savedSequence || Boolean(this.pending) }
  snapshot() { return { id: this.id, content: { ...this.content }, revision: this.revision, state: this.state, message: this.message, savedAt: this.savedAt, dirty: this.dirty } }
  private cancelTimers() { clearTimeout(this.timer); clearTimeout(this.maximum); this.timer = undefined; this.maximum = undefined }
  attach(entry: DiaryEntry | { id: string }) {
    this.epoch++; this.cancelTimers(); this.pending = undefined; this.flight = undefined; this.locked = false; this.retries = 0
    this.id = entry.id; this.content = 'body' in entry ? { title: entry.title, body: entry.body, coveredFrom: entry.coveredFrom, coveredTo: entry.coveredTo } : emptyDiary()
    this.revision = 'revision' in entry ? entry.revision : 0; this.savedAt = 'updatedAt' in entry ? entry.updatedAt : ''
    this.sequence = this.savedSequence = 0; this.state = 'clean'; this.message = ''; this.changed()
  }
  edit(patch: Partial<DiaryContent>) {
    if (this.locked || !this.id) return
    this.content = { ...this.content, ...patch }; this.sequence++; this.message = diaryValidation(this.content)
    if (this.state !== 'conflict' && !this.pending) this.state = this.message ? 'failed' : 'dirty'
    this.changed(); if (!this.message && this.state !== 'conflict' && !this.pending) this.schedule()
  }
  private schedule() {
    if (this.locked) return
    clearTimeout(this.timer)
    this.timer = setTimeout(() => { void this.save().catch(() => {}) }, 2000)
    this.maximum ??= setTimeout(() => { void this.save().catch(() => {}) }, 30000)
  }
  save(): Promise<void> {
    if (this.flight) return this.flight
    if (this.locked || this.state === 'conflict') return Promise.reject(new Error('请先恢复登录或处理版本冲突'))
    if (!this.id || !this.dirty) return Promise.resolve()
    const validation = !this.pending && diaryValidation(this.content)
    if (validation) { this.state = 'failed'; this.message = validation; this.changed(); return Promise.reject(new Error(validation)) }
    this.cancelTimers()
    const pending = this.pending ?? { request: { entryId: this.id, mutationId: crypto.randomUUID(), expectedRevision: this.revision, ...this.content }, sequence: this.sequence }
    this.pending = pending; const epoch = this.epoch; this.state = 'saving'; this.message = ''; this.changed()
    const flight = this.send(pending.request).then(ack => {
      if (epoch !== this.epoch) return
      if (ack.entryId !== pending.request.entryId || ack.mutationId !== pending.request.mutationId || !Number.isSafeInteger(ack.revision) || ack.revision < Math.max(1, pending.request.expectedRevision) || !Number.isFinite(Date.parse(ack.savedAt))) throw new Error('未收到有效的保存确认')
      this.revision = ack.revision; this.savedAt = ack.savedAt; this.savedSequence = pending.sequence; this.pending = undefined; this.retries = 0
      this.state = this.locked ? 'locked' : this.dirty ? 'dirty' : 'clean'
    }).catch(error => {
      if (epoch !== this.epoch) return
      const status = Number(error?.status)
      if (status === 409) { this.pending = undefined; this.state = 'conflict'; this.message = '服务器版本已变化，请比较后选择' }
      else if (status === 401 || status === 403 || this.locked) { this.locked = true; this.state = 'locked'; this.message = '请重新确认同一账号的登录身份，草稿仍在此页内存中' }
      else { this.state = 'failed'; this.message = status === 400 ? (error.message || '内容校验失败') : '保存未获确认，请保留此页并重试'; if (status === 400) this.pending = undefined }
      if (!status || status >= 500) { if (++this.retries <= 2 && !this.locked) this.timer = setTimeout(() => { void this.save().catch(() => {}) }, 1000 * 2 ** this.retries) }
      throw error
    }).finally(() => {
      if (this.flight === flight) this.flight = undefined
      if (epoch === this.epoch) { this.changed(); if (this.state === 'dirty' && !this.locked) this.schedule() }
    })
    this.flight = flight; return flight
  }
  async flush() { do { await this.save() } while (this.dirty && this.state === 'dirty') }
  lock() { this.locked = true; this.cancelTimers(); this.state = 'locked'; this.changed() }
  resume(server?: DiaryEntry) {
    this.locked = false
    this.state = server && (server.deletedAt || server.revision !== this.revision) && !this.pending ? 'conflict' : this.pending ? 'failed' : this.dirty ? 'dirty' : 'clean'
    this.message = this.state === 'conflict' ? '登录恢复后发现服务器版本已变化，请先比较' : ''; this.changed()
  }
  resolveUsingLocal(server: DiaryEntry) {
    if (server.deletedAt) throw new Error('日记已删除，请先恢复历史版本')
    this.revision = server.revision; this.pending = undefined; this.sequence++; this.state = 'dirty'; this.message = ''; this.changed()
  }
  clear() { this.attach({ id: '' }) }
  dispose() { this.cancelTimers(); this.epoch++; this.changed = () => {}; this.content = emptyDiary(); this.pending = undefined }
}
