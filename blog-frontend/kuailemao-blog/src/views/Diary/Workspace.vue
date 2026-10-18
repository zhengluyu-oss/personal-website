<script setup lang="ts">
import { defineAsyncComponent, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import useUserStore from '@/store/modules/user'
import { diaryReadRequest } from '@/utils/diary-api'

const OwnerWorkspace = defineAsyncComponent(() => import('./index.vue'))
const ReaderWorkspace = defineAsyncComponent(() => import('./ReaderWorkspace.vue'))
const user = useUserStore(), router = useRouter()
const mode = ref<'checking' | 'owner' | 'reader' | 'unavailable'>('checking'), error = ref('')
let sequence = 0, disposed = false, timer: ReturnType<typeof setInterval> | undefined
let restoreRobots = () => {}
async function check(reset = false) {
  const current = ++sequence
  if (reset) mode.value = 'checking'
  try {
    const access = await diaryReadRequest<{ allowed: boolean; available: boolean; canManage: boolean }>('GET', '/capabilities')
    if (current !== sequence || disposed) return
    if (!access.allowed) {
      mode.value = 'checking'
      await router.replace({ name: 'moduleAccessDenied', query: { module: 'diary', redirect: '/diary' } }); return
    }
    error.value = ''
    mode.value = !access.available ? 'unavailable' : access.canManage ? 'owner' : 'reader'
  } catch {
    if (current === sequence && !disposed) {
      // A transient probe failure must not destroy an author's unacknowledged in-memory draft.
      if (mode.value !== 'owner') mode.value = 'unavailable'
      error.value = '暂时无法确认日记权限，请重试。未确认的编辑仍需等待服务器保存确认。'
    }
  }
}
// User-info refresh briefly clears username/loading state; it is not a change of identity.
watch(() => user.token, () => { void check(true) }, { immediate: true, flush: 'sync' })
const refresh = () => { void check() }
onMounted(() => {
  const previous = document.head.querySelector<HTMLMetaElement>('meta[name="robots"]'), old = previous?.content
  const robots = previous || document.createElement('meta'); robots.name = 'robots'; robots.content = 'noindex,nofollow,noarchive'
  if (!previous) document.head.append(robots)
  restoreRobots = () => { if (previous) previous.content = old || ''; else robots.remove() }
  timer = setInterval(refresh, 30000)
  window.addEventListener('focus', refresh)
  window.addEventListener('diary-session-invalid', refresh)
})
onBeforeUnmount(() => {
  disposed = true; sequence++; clearInterval(timer)
  window.removeEventListener('focus', refresh); window.removeEventListener('diary-session-invalid', refresh)
  restoreRobots()
})
</script>
<template>
  <Header />
  <p v-if="mode === 'owner' && error" class="diary-access-warning" role="alert">{{ error }} <button type="button" @click="check()">重新检查</button></p>
  <OwnerWorkspace v-if="mode === 'owner'" />
  <ReaderWorkspace v-else-if="mode === 'reader'" />
  <section v-else class="diary-access-state" role="status">
    <h1>我的日记</h1><p>{{ mode === 'checking' ? '正在确认访问权限…' : error || '日记未启用或存储暂时不可用。' }}</p>
    <el-button v-if="mode === 'unavailable'" @click="check(true)">重新检查</el-button>
  </section>
</template>
<style scoped>
.diary-access-state { max-width: 70rem; margin: 7rem auto; padding: 1.5rem; color: var(--brand-ink); }
.diary-access-warning { position: relative; z-index: 1; margin: 4.5rem 1rem -4rem; padding: .7rem 1rem; background: #fff6ef; color: #75471e; }
</style>
