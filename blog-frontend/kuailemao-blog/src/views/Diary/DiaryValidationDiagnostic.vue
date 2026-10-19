<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import { diaryRequest } from '@/utils/diary-api'
const props = defineProps<{ id: string; kind: 'QUESTION' | 'REVIEW' | 'PERIOD' }>()
interface Diagnostic { status: string; reason?: string; fieldPath?: string; model?: string; promptVersion?: string; expiresAt?: string; raw?: string; sources: { sourceId: string; kind: string; text: string }[] }
const metadata = ref<Diagnostic>(), output = ref<Diagnostic>(), error = ref(''), busy = ref(false)
const reasons: Record<string, string> = { JSON_PARSE: '回答不是有效的 JSON', ROOT_FIELDS: '回答字段缺失或包含不支持的字段', REQUIRED: '缺少必填内容', TYPE: '字段类型不符合要求', TEXT_LIMIT: '文字超出限制或含不支持字符', ARRAY_LIMIT: '条目数量超出限制', SOURCE_UNKNOWN: '引用了不存在或不匹配的来源编号', QUOTE_MISMATCH: '引文与日记原文不逐字一致', EVIDENCE_TYPE: '陈述类型与证据类型不一致', CONTRACT_UNKNOWN: '其他输出校验问题' }
const statuses: Record<string, string> = { NOT_RECORDED: '本次没有留存具体诊断；旧任务无法补回回答。', NOT_RETAINED: '已记录原因，但因容量限制未保留完整回答。', EXPIRED: '七天保留期已结束，回答已清除。', DELETED: '你已删除失败回答。', STALE: '来源已修改或删除，失败回答已清除。' }
let disposed = false, timer: ReturnType<typeof setInterval> | undefined
let expiry: ReturnType<typeof setTimeout> | undefined
const path = () => '/ai/diagnostics/' + props.kind + '/' + encodeURIComponent(props.id)
async function load(body = false) {
  busy.value = true; error.value = ''
  try {
    const value = await diaryRequest<Diagnostic>('GET', path() + (body ? '?body=true' : ''))
    if (disposed) return
    clearTimeout(expiry)
    if (value.status === 'AVAILABLE' && value.expiresAt) {
      const remaining = Date.parse(value.expiresAt) - Date.now()
      if (remaining <= 0) { value.status = 'EXPIRED'; value.raw = undefined; value.sources = [] }
      else expiry = setTimeout(() => { output.value = undefined; if (metadata.value) metadata.value = { ...metadata.value, status: 'EXPIRED' } }, remaining)
    }
    metadata.value = { ...value, raw: undefined, sources: [] }
    if (value.status !== 'AVAILABLE') output.value = undefined
    else if (body) output.value = value
  } catch { if (!disposed) { output.value = undefined; error.value = '诊断暂不可用，请稍后查看；这不会重新调用模型。' } }
  finally { if (!disposed) busy.value = false }
}
async function remove() {
  try { await ElMessageBox.confirm('删除后无法恢复本次失败回答，不影响日记原文或成功报告。', '删除私密诊断') } catch { return }
  if (disposed) return
  busy.value = true
  try { await diaryRequest('DELETE', path()); if (!disposed) { output.value = undefined; await load() } }
  catch { if (!disposed) { output.value = undefined; error.value = '删除尚未确认，请重新查询状态。' } }
  finally { if (!disposed) busy.value = false }
}
onMounted(() => { void load(); timer = setInterval(() => {
  if (metadata.value?.expiresAt && Date.parse(metadata.value.expiresAt) <= Date.now()) { output.value = undefined; metadata.value = { ...metadata.value, status: 'EXPIRED' } }
  if (output.value && !busy.value) void load()
}, 15000) })
onBeforeUnmount(() => { disposed = true; clearInterval(timer); clearTimeout(expiry); metadata.value = undefined; output.value = undefined })
</script>

<template>
  <section class="validation-diagnostic" aria-label="失败原因与私密诊断">
    <h4>具体校验原因</h4>
    <p v-if="metadata?.reason">{{ reasons[metadata.reason] || '暂未识别的校验问题' }}<br><code>{{ metadata.fieldPath }}</code></p>
    <p v-if="metadata && metadata.status !== 'AVAILABLE'">{{ statuses[metadata.status] || '诊断不可用' }}</p>
    <p v-if="error" role="alert">{{ error }}</p>
    <template v-if="metadata?.status === 'AVAILABLE'">
      <p class="muted">失败回答仅你可见，保留至 {{ new Date(metadata.expiresAt!).toLocaleString('zh-CN') }}。查看和删除均不会调用模型。</p>
      <div class="diagnostic-actions"><el-button :disabled="busy" @click="output ? output = undefined : load(true)">{{ output ? '收起并清空回答' : '查看失败回答与原文' }}</el-button><el-button :disabled="busy" @click="remove">提前删除</el-button></div>
      <div v-if="output" class="diagnostic-body"><strong>未通过校验的模型输出，不是已发布的分析结论</strong><p class="muted">{{ output.model }} · {{ output.promptVersion }}</p><pre>{{ output.raw }}</pre><details><summary>本次实际提供给模型的来源</summary><section v-for="(source, i) in output.sources" :key="i"><h5>{{ source.sourceId }} · {{ source.kind }}</h5><pre>{{ source.text }}</pre></section></details></div>
    </template>
    <el-button v-if="!metadata || error" :disabled="busy" @click="load()">查询诊断</el-button>
  </section>
</template>

<style scoped>
.validation-diagnostic{margin-top:20px;border-top:1px solid var(--diary-line);padding-top:16px;font-size:14px;line-height:1.8}.validation-diagnostic h4{margin:0 0 8px}.muted{color:var(--diary-muted);font-size:12px}.diagnostic-actions{display:flex;flex-wrap:wrap;gap:8px}.diagnostic-body{margin-top:18px}.diagnostic-body pre{max-height:360px;overflow:auto;white-space:pre-wrap;overflow-wrap:anywhere;padding:16px;background:var(--diary-tint);font:inherit;font-size:13px}.diagnostic-body strong{color:var(--diary-text)}summary{cursor:pointer}
</style>
