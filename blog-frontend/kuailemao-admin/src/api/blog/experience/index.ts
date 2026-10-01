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

export async function experienceProjectList(experienceId:string|number){return useGet(`/experience/${experienceId}/projects/back/list`).catch(msg=>message.warn(msg))}
export async function getExperienceProject(experienceId:string|number,projectId:string|number){return useGet(`/experience/${experienceId}/projects/back/get/${projectId}`).catch(msg=>message.warn(msg))}
export async function addExperienceProject(experienceId:string|number,data:any){return usePut(`/experience/${experienceId}/projects/back/add`,data)}
export async function updateExperienceProject(experienceId:string|number,data:any){return usePost(`/experience/${experienceId}/projects/back/update`,data)}
export async function deleteExperienceProjects(experienceId:string|number,ids:Array<string|number>){return useDelete(`/experience/${experienceId}/projects/back/delete`,JSON.stringify(ids)).catch(msg=>message.warn(msg))}
