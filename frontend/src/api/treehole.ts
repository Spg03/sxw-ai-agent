import { api } from './client'

export interface TreeholeEntry {
  id: number
  title: string
  content: string
  emotionTag?: string
  hermesSummary?: string
  hermesReply?: string
  createdAt: string
}

export interface CreateTreeholeRequest {
  title: string
  content: string
}

export const treeholeApi = {
  create: (req: CreateTreeholeRequest) => api.post<TreeholeEntry>('/treeholes', req),
  list: () => api.get<TreeholeEntry[]>('/treeholes'),
  get: (id: number) => api.get<TreeholeEntry>(`/treeholes/${id}`),
  delete: (id: number) => api.delete<void>(`/treeholes/${id}`),
}
