import { api, type RequestOptions } from './client'

export interface DashboardStats {
  chatCount: number
  treeholeCount: number
  noteCount: number
  evalRunCount: number
}

export interface DashboardActivity {
  type: 'treehole' | 'chat' | 'note' | 'eval'
  title: string
  detail: string
  occurredAt: string
}

export interface DashboardConversation {
  id: string
  title: string
  occurredAt: string
}

export interface DashboardOverview {
  stats: DashboardStats
  recentActivities: DashboardActivity[]
  recentConversations: DashboardConversation[]
  companionMinutes: number
  generatedAt: string
}

export const dashboardApi = {
  getStats: (options?: RequestOptions) => api.get<DashboardStats>('/dashboard/stats', options),
  getOverview: (options?: RequestOptions) => api.get<DashboardOverview>('/dashboard/overview', options),
  healthCheck: (options?: RequestOptions) => api.get<string>('/dashboard/health', options),
}
