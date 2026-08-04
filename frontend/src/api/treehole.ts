import { api, type RequestOptions } from './client'

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
  create: (req: CreateTreeholeRequest, options?: RequestOptions) => api.post<TreeholeEntry>('/treeholes', req, options),
  list: (options?: RequestOptions, page = 1, size = 20) => api.get<TreeholeEntry[]>(`/treeholes?page=${page}&size=${size}`, options),
  get: (id: number, options?: RequestOptions) => api.get<TreeholeEntry>(`/treeholes/${id}`, options),
  delete: (id: number, options?: RequestOptions) => api.delete<void>(`/treeholes/${id}`, options),
}
