import axios, { AxiosError, AxiosHeaders, AxiosInstance, InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { Jwt_Prefix } from '@/const/Jwt'
import { GET_TOKEN, REMOVE_TOKEN } from '@/utils/auth.ts'
import useLoadingStore from '@/store/modules/loading.ts'
import { REQUEST_LOADING_PATH } from '@/utils/enum.ts'
import { createRequestTracker, type RequestToken } from '@/utils/request-tracker.ts'
import { isTrustedApiUrl, isAuthenticationFailure } from './session-boundary'

type TrackedRequestConfig = InternalAxiosRequestConfig & {
  __requestToken?: RequestToken
  __trustedApi?: boolean
  __sessionToken?: string
}
const API_BASE = import.meta.env.VITE_APP_BASE_API || '/api'
const SILENT_FALLBACK_PATHS = ['/websiteInfo/front', '/banners/list', '/article/list', '/article/recommend', '/experience']

const http: AxiosInstance = axios.create({
  baseURL: API_BASE,
  timeout: 60000,
  headers: { 'Content-Type': 'application/json;charset=UTF-8' },
})

const tracker = createRequestTracker({
  onProgressStart: () => NProgress.start(),
  onProgressDone: () => NProgress.done(),
  onBlockingStart: () => useLoadingStore().show(),
  onBlockingDone: () => useLoadingStore().hide(),
})

function finalizeRequest(config?: InternalAxiosRequestConfig) {
  tracker.finalize((config as TrackedRequestConfig | undefined)?.__requestToken)
}

function expireSession(config?: TrackedRequestConfig) {
  // An old response must not sign out a newer session established in the meantime.
  if (config?.__trustedApi && config.__sessionToken && config.__sessionToken === GET_TOKEN()) {
    REMOVE_TOKEN()
    window.location.replace('/auth/login')
  }
}

http.interceptors.request.use((config: TrackedRequestConfig) => {
  const url = config.url ?? ''
  const musicBase = import.meta.env.VITE_MUSIC_BASE_API
  if (musicBase && (url === musicBase || url.startsWith(musicBase + '/'))) config.baseURL = ''

  const yiYanApi = import.meta.env.VITE_YIYAN_API as string
  const isYiYan = Boolean(yiYanApi) && url.startsWith(yiYanApi)
  const blocksPage = REQUEST_LOADING_PATH.some(path => url.startsWith(path))
  if (!isYiYan || blocksPage) config.__requestToken = tracker.begin(blocksPage)

  config.__trustedApi = isTrustedApiUrl(http.getUri(config), API_BASE, window.location.href)
  config.headers = AxiosHeaders.from(config.headers)
  config.headers.delete('Authorization')
  config.__sessionToken = undefined
  if (config.__trustedApi) {
    const token = GET_TOKEN()
    if (token) {
      config.headers.set('Authorization', Jwt_Prefix + token)
      config.__sessionToken = token
    }
  } else {
    config.withCredentials = false
  }
  return config
}, error => Promise.reject(error))

http.interceptors.response.use(
  response => {
    finalizeRequest(response.config as InternalAxiosRequestConfig)
    if (response.data?.code === 1002) expireSession(response.config as TrackedRequestConfig)
    if ((response.config as TrackedRequestConfig).__trustedApi && response.data?.code === 1012) {
      ElNotification({ title: '账号已被封禁', message: response.data.msg, type: 'warning' })
    }
    return response.data
  },
  (error: AxiosError) => {
    finalizeRequest(error.config as InternalAxiosRequestConfig | undefined)
    if (isAuthenticationFailure(error)) expireSession(error.config as TrackedRequestConfig | undefined)
    let message = error.message
    if (message === 'Network Error') message = '后端接口连接异常'
    else if (message.includes('timeout')) message = '系统接口请求超时'
    else if (message.includes('Request failed with status code')) message = `系统接口${message.slice(-3)}异常`
    const url = error.config?.url ?? ''
    const hasVisualFallback = SILENT_FALLBACK_PATHS.some(path => url.includes(path))
    if (!hasVisualFallback && !url.startsWith('https://v1.hitokoto.cn')) ElMessage.error(message)
    return Promise.reject(error)
  },
)

export default http
