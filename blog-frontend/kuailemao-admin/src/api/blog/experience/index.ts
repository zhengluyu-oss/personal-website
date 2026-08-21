import { message } from 'ant-design-vue'

export async function experienceList() {
  return useGet('/experience/back/list').catch(msg => message.warn(msg))
}

export async function getExperienceById(id: string | number) {
  return useGet(`/experience/back/get/${id}`).catch(msg => message.warn(msg))
}

export async function addExperience(data: any) {
  return usePut('/experience/back/add', data).catch(msg => message.warn(msg))
}

export async function updateExperience(data: any) {
  return usePost('/experience/back/update', data).catch(msg => message.warn(msg))
}

export async function deleteExperienceByIds(ids: Array<string | number>) {
  return useDelete('/experience/back/delete', JSON.stringify(ids)).catch(msg => message.warn(msg))
}
