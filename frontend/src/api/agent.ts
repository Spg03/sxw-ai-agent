import { api } from './client'

export interface AgentRequest {
  chatId: string
  profile: 'LOVE' | 'GENERAL' | 'HERMES'
  message: string
  stream?: boolean
  metadata?: Record<string, any>
}

export interface AgentResponse {
  requestId: string
  traceId: string
  answer: string
  citations?: string[]
  toolCalls?: Array<{
    name: string
    arguments: string
    result: string
  }>
  latencyMs: number
}

export interface AgentMode {
  id: string
  name: string
  description: string
}

export const agentApi = {
  chat: (req: AgentRequest) => api.post<AgentResponse>('/agents/chat', req),
  modes: () => api.get<AgentMode[]>('/agents/modes'),
}
