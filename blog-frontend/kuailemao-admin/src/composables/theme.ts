import { useColorMode } from '@vueuse/core'

/**
 * 管理后台固定浅色主题。
 * 不使用 useDark()：其在「系统=浅色」时会把偏好写成 auto，随后跟随系统变黑，
 * 导致 Ant Design 仍为 light、html.dark 已开启的「又黑又白」。
 */
const colorMode = useColorMode({
  initialValue: 'light',
  emitAuto: false,
})

function forceLight() {
  colorMode.value = 'light'
  try {
    localStorage.setItem('vueuse-color-scheme', 'light')
  }
  catch {
    // ignore
  }
  if (typeof document !== 'undefined')
    document.documentElement.classList.remove('dark')
}

forceLight()

/** 始终为 false；写入会被忽略并重新钉回 light */
export const isDark = computed({
  get: () => false,
  set: () => {
    forceLight()
  },
})

/** 兼容旧调用 toggleDark(true|false)；一律强制浅色 */
export function toggleDark(_value?: boolean) {
  forceLight()
}
