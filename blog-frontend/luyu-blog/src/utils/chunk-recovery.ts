import type { Router } from 'vue-router'

const RECOVERY_KEY = 'ruyu:chunk-recovery'
const RECOVERY_WINDOW_MS = 60_000

interface RecoveryRecord {
  target: string
  timestamp: number
}
export function isAsyncChunkLoadError(reason: unknown) {
  const message = reason instanceof Error ? reason.message : String(reason || '')
  return /Failed to fetch dynamically imported module|error loading dynamically imported module|Importing a module script failed|Loading chunk \d+ failed|ChunkLoadError/i.test(message)
}

export function canRetryChunkLoad(target: string, storedValue: string | null, now = Date.now()) {
  if (!storedValue) return true
  try {
    const record = JSON.parse(storedValue) as RecoveryRecord
    return record.target !== target || now - record.timestamp > RECOVERY_WINDOW_MS
  } catch {
    return true
  }
}

function reloadOnce(target: string) {
  const storedValue = sessionStorage.getItem(RECOVERY_KEY)
  if (!canRetryChunkLoad(target, storedValue)) return false
  sessionStorage.setItem(RECOVERY_KEY, JSON.stringify({ target, timestamp: Date.now() }))
  window.location.replace(target)
  return true
}

export function installAsyncChunkRecovery(router: Router) {
  router.onError((error, to) => {
    if (isAsyncChunkLoadError(error)) reloadOnce(to.fullPath || window.location.href)
  })

  window.addEventListener('vite:preloadError', (event) => {
    const preloadEvent = event as Event & { payload?: unknown }
    if (!isAsyncChunkLoadError(preloadEvent.payload)) return
    event.preventDefault()
    reloadOnce(window.location.href)
  })

  // 只有初始路由及其异步组件成功解析后才清除标记；持续失败时不会刷新死循环。
  void router.isReady().then(() => sessionStorage.removeItem(RECOVERY_KEY))
}
