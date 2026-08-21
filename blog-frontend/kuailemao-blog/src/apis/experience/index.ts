import http from '@/utils/http.ts'

export interface WorkExperienceItem {
  id: number
  company: string
  roleTitle: string
  startDate: string
  endDate?: string
  isCurrent: number
  highlights?: string
  content?: string
  orderNum: number
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
