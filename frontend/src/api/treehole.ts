import { api, type RequestOptions } from './client'

export interface TreeholeEntry {
  id: number
  title: string
  content: string
  emotionTag?: string
  hermesSummary?: string
  hermesReply?: string
  mood: Mood
  favorite: boolean
  archived: boolean
  createdAt: string
  updatedAt: string
}

export type Mood = '开心' | '平静' | '疲惫' | '焦虑' | '难过'

export interface CreateTreeholeRequest {
  title: string
  content: string
  mood: Mood
}

export interface TreeholeInsights {
  total: number
  active: number
  moodCounts: Record<string, number>
  trend: { date: string; mood: string; score: number; count: number }[]
  calendar: Record<string, string>
}

export const treeholeApi = {
  create: (req: CreateTreeholeRequest, options?: RequestOptions) => api.post<TreeholeEntry>('/treeholes', req, options),
  list: (options?: RequestOptions, archived = false) => api.get<TreeholeEntry[]>(`/treeholes?archived=${archived}`, options),
  get: (id: number, options?: RequestOptions) => api.get<TreeholeEntry>(`/treeholes/${id}`, options),
  archive: (id: number, value: boolean, options?: RequestOptions) => api.patch<TreeholeEntry>(`/treeholes/${id}/archive`, { value }, options),
  favorite: (id: number, value: boolean, options?: RequestOptions) => api.patch<TreeholeEntry>(`/treeholes/${id}/favorite`, { value }, options),
  insights: (days = 30, options?: RequestOptions) => api.get<TreeholeInsights>(`/treeholes/insights?days=${days}`, options),
  prompts: (options?: RequestOptions) => api.get<string[]>('/treeholes/prompts', options),
  delete: (id: number, options?: RequestOptions) => api.delete<void>(`/treeholes/${id}`, options),
}
