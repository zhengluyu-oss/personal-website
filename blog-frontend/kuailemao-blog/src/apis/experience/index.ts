import http from '@/utils/http.ts'

export interface WorkExperienceItem {
  id: number
  company: string
  roleTitle: string
  startDate: string
  endDate?: string
  isCurrent: number
  highlights?: string
  companyIntroduction?: string
  mainBusiness?: string
  projectSummary?: string
  coverImage?: string
  techStack?: string
  responsibilities?: string
  metrics?: string
  content?: string
  orderNum: number
}

export interface ExperienceProjectItem {
  id:number; experienceId:number; projectName:string; summary:string; coverImage?:string;
  startDate?:string; endDate?:string; roleTitle?:string; techStack?:string;
  contributions?:string; outcomes?:string; content?:string; company?:string; companyRoleTitle?:string;
  orderNum:number; status?:number
}

export function experienceList() {
  return http({
    url: '/experience/list',
    method: 'get',
  })
}

export function getExperience(id: string | number) {
  return http({
    url: `/experience/${id}`,
    method: 'get',
  })
}

export function experienceProjectList(experienceId:string|number){return http({url:`/experience/${experienceId}/projects`,method:'get'})}
export function getExperienceProject(experienceId:string|number,projectId:string|number){return http({url:`/experience/${experienceId}/projects/${projectId}`,method:'get'})}
