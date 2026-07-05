import { api } from './client'

export interface SkillSummary {
  name: string
  description: string
}

export interface Skill {
  name: string
  description: string
  body: string
}

export const skillsApi = {
  list: () => api.get<SkillSummary[]>('/skills'),
  get: (name: string) => api.get<Skill>(`/skills/${name}`),
}
