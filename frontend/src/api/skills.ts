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
  list: (options?: { signal?: AbortSignal }) => api.get<SkillSummary[]>('/skills', options),
  get: (name: string, options?: { signal?: AbortSignal }) => api.get<Skill>(`/skills/${name}`, options),
}
