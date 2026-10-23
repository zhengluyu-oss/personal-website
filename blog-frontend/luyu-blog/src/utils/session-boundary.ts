/** The caller supplies Axios' resolved URI, so its baseURL combination stays authoritative. */
export function isTrustedApiUrl(requestUri: string, apiBase: string, pageUrl: string): boolean {
  try {
    const pageOrigin = new URL(pageUrl).origin + '/'
    const api = new URL(apiBase, pageOrigin)
    const target = new URL(requestUri, pageOrigin)
    if (!['https:', 'http:'].includes(api.protocol) || api.username || api.password
      || target.username || target.password || target.origin !== api.origin) return false
    // Do not let encoded separators change the path boundary at a proxy.
    if (/%2f|%5c/i.test(target.pathname) || target.pathname.includes('\\')) return false
    const prefix = api.pathname.replace(/\/+$/, '')
    return !prefix || target.pathname === prefix || target.pathname.startsWith(prefix + '/')
  } catch {
    return false
  }
}

export function isAuthenticationFailure(error: unknown): boolean {
  const value = error as { code?: number; response?: { status?: number; data?: { code?: number } } } | undefined
  if (value?.response?.status && value.response.status >= 500) return false
  return value?.code === 1002 || value?.response?.status === 401 || value?.response?.data?.code === 1002
}
