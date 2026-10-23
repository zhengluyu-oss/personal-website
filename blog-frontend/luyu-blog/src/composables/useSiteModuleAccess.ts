import { shallowRef } from 'vue'
import http from '@/utils/http'
import { validateModuleAccess, type SiteModuleAccess, type SiteModuleKey } from '@/utils/site-module-access'

const access = shallowRef<SiteModuleAccess[]>([])
export const canViewModule = (key: SiteModuleKey) => access.value.some(item => item.key === key && item.allowed)

export async function refreshModuleAccess() {
  access.value = []
  const response: any = await http.get('/site-modules/access')
  if (response.code !== 200) throw new Error('模块访问配置不可用')
  access.value = validateModuleAccess(response.data)
  return access.value
}
