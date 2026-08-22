import axios, { AxiosError, AxiosInstance, InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { Jwt_Prefix } from '@/const/Jwt'
import { GET_TOKEN } from '@/utils/auth.ts'
import useLoadingStore from '@/store/modules/loading.ts'
import { REQUEST_LOADING_PATH } from '@/utils/enum.ts'
import { createRequestTracker, type RequestToken } from '@/utils/request-tracker.ts'

type TrackedRequestConfig = InternalAxiosRequestConfig & { __requestToken?: RequestToken }
const SILENT_FALLBACK_PATHS = ['/websiteInfo/front', '/banners/list', '/article/list', '/article/recommend', '/experience']

const http: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_APP_BASE_API ?? '/',
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

http.interceptors.request.use((config: TrackedRequestConfig) => {
  const url = config.url ?? ''
  if (url.startsWith(import.meta.env.VITE_MUSIC_BASE_API)) config.baseURL = ''

  const yiYanApi = import.meta.env.VITE_YIYAN_API as string
  const isYiYan = Boolean(yiYanApi) && url.startsWith(yiYanApi)
  const blocksPage = REQUEST_LOADING_PATH.some(path => url.startsWith(path))
  if (!isYiYan || blocksPage) config.__requestToken = tracker.begin(blocksPage)

  config.headers['X-Client-Type'] = 'Frontend'
  const token = GET_TOKEN()
  if (token) config.headers.Authorization = Jwt_Prefix + token
  return config
}, error => Promise.reject(error))

http.interceptors.response.use(
  response => {
    finalizeRequest(response.config as InternalAxiosRequestConfig)
    if (response.data.code === 1012) {
      ElNotification({ title: '账号已被封禁', message: response.data.msg, type: 'warning' })
    }
    return response.data
  },
  (error: AxiosError) => {
    finalizeRequest(error.config as InternalAxiosRequestConfig | undefined)
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
