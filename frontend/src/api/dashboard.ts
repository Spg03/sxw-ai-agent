import { api } from './client'

interface RequestOptions {
  signal?: AbortSignal
}

export interface DashboardStats {
  chatCount: number
  treeholeCount: number
  noteCount: number
  evalRunCount: number
}

export const dashboardApi = {
  getStats: (options?: RequestOptions) => api.get<DashboardStats>('/dashboard/stats', options),
  healthCheck: (options?: RequestOptions) => api.get<string>('/dashboard/health', options),
}
