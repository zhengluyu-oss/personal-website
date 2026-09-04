export interface LoginParams {
  username: string
  password: string
  type?: 'account'
}

export interface LoginMobileParams {
  mobile: string
  code: string
  type: 'mobile'
}

export interface LoginResultModel {
  token?: string
  expire?: string
  code: number
  challengeId?: string
  maskedEmail?: string
  expiresIn?: number
  resendAfter?: number
  secondFactorRequired?: boolean
}

export interface AdminLoginVerifyParams {
  challengeId: string
  code: string
}

export interface AdminLoginResendParams {
  challengeId: string
}

export function loginApi(params: LoginParams | LoginMobileParams) {
  return usePost<LoginResultModel, LoginParams | LoginMobileParams>('/user/login', params, {
    // 设置为false的时候不会携带token
    token: false,
    // 开发模式下使用自定义的接口
    // customDev: true,
    // 是否开启全局请求loading
    loading: true,
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  })
}

export function verifyAdminLoginApi(params: AdminLoginVerifyParams) {
  return usePost<LoginResultModel, AdminLoginVerifyParams>('/user/admin-login/verify', params, {
    token: false,
    loading: true,
  })
}

export function resendAdminLoginApi(params: AdminLoginResendParams) {
  return usePost<LoginResultModel, AdminLoginResendParams>('/user/admin-login/resend', params, {
    token: false,
    loading: true,
  })
}

export function logoutApi() {
  return useGet('/user/logout')
}
