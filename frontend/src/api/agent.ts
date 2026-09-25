import { api } from './client'

export interface AgentRequest {
  chatId: string
  profile: 'LOVE' | 'GENERAL' | 'HERMES'
  message: string
  stream?: boolean
  metadata?: Record<string, unknown>
  mode?: 'CHAT' | 'PLAN' | 'EXECUTE'
  planId?: string
  enabledTools?: string[]
  webSearchEnabled?: boolean
  knowledgeDocumentIds?: string[]
  attachmentIds?: string[]
  requestId?: string
  memoryReadEnabled?: boolean
  memoryWriteEnabled?: boolean
}

export interface ConversationSummary {
  conversationId: string
  title: string
  profile: 'LOVE' | 'GENERAL' | 'HERMES'
  pinned: boolean
  rollingSummary?: string
  createdAt: string
  updatedAt: string
}
export interface ConversationMessage { id: number; role: string; content: string; createdAt: string }
export interface AgentResponse { requestId: string; traceId: string; answer: string; citations?: string[]; toolCalls?: Array<{ name: string; arguments: string; result: string }>; latencyMs: number }
export interface AgentMode { id: string; name: string; description: string }

export interface StreamTokenEvent { type: 'token'; content: string }
export interface StreamToolCallEvent { type: 'tool_call'; toolName: string; arguments: string }
export interface StreamToolResultEvent { type: 'tool_result'; toolName: string; result: string }
export interface StreamDoneEvent { type: 'done'; requestId: string; traceId: string; answer: string; latencyMs: number }
export interface StreamErrorEvent { type: 'error'; message: string }
export interface StreamSecurityEvent { riskLevel: string; sanitized: boolean; blocked: boolean; message: string }
export interface StreamCallbacks {
  onToken: (content: string) => void
  onToolCall?: (toolName: string, args: string) => void
  onToolResult?: (toolName: string, result: string) => void
  onDone?: (event: StreamDoneEvent) => void
  onError?: (message: string) => void
  onSecurity?: (event: StreamSecurityEvent) => void
}
export type ToolCapability = { name: string; description: string; riskLevel: string; requiresApproval: boolean; networkRequired: boolean; available: boolean }
export type ConversationDeletionPreview = { messageCount: number; attachmentCount: number; inferredMemoryCount: number }
export type SavedMemory = {
  memoryId: string; name: string; description: string; memoryType: string; status: string
  sourceKind: string; alwaysOn: boolean; scopeType?: string; scopeId?: string; priority?: string; createdAt: string
}

export const agentApi = {
  chat: (req: AgentRequest) => api.post<AgentResponse>('/agent/chat', req),
  modes: () => api.get<AgentMode[]>('/agent/profiles'),
  clearMemory: (chatId: string) => api.delete<void>(`/agent/chat/${chatId}/memory`),
  listConversations: () => api.get<ConversationSummary[]>('/conversations'),
  createConversation: (profile: ConversationSummary['profile'] = 'GENERAL', title?: string) => api.post<ConversationSummary>('/conversations', { profile, title }),
  conversationMessages: (id: string) => api.get<ConversationMessage[]>(`/conversations/${encodeURIComponent(id)}/messages`),
  updateConversation: (id: string, body: Partial<Pick<ConversationSummary, 'title' | 'profile' | 'pinned'>>) => api.patch<ConversationSummary>(`/conversations/${encodeURIComponent(id)}`, body),
  conversationDeletionPreview: (id: string) => api.get<ConversationDeletionPreview>(`/conversations/${encodeURIComponent(id)}/deletion-preview`),
  deleteConversation: (id: string, purgeInferredMemories = false) => api.delete<void>(`/conversations/${encodeURIComponent(id)}?purgeInferredMemories=${purgeInferredMemories}`),
  tools: (profile: string) => api.get<ToolCapability[]>(`/agent/tools?profile=${encodeURIComponent(profile)}`),
  memoryStatus: (id: string) => api.get<{messageCount:number; hasSummary:boolean; workingMemoryVersion:number}>(`/conversations/${encodeURIComponent(id)}/memory/status`),
  memoryCandidates: () => api.get<Array<{candidateId:string; title:string; content:string; status:string; sourceKind:string; createdAt:string}>>('/memories/candidates'),
  decideMemoryCandidate: (id: string, approve: boolean) => api.patch<void>(`/memories/candidates/${encodeURIComponent(id)}/decision`, { approve }),
  memories: (status = 'ACTIVE') => api.get<SavedMemory[]>(`/memories?status=${encodeURIComponent(status)}`),
  archiveMemory: (id: string) => api.patch<void>(`/memories/${encodeURIComponent(id)}/archive`),
  restoreMemory: (id: string) => api.patch<void>(`/memories/${encodeURIComponent(id)}/restore`),
  pinMemory: (id: string, alwaysOn: boolean) => api.patch<void>(`/memories/${encodeURIComponent(id)}/pin`, { alwaysOn }),
  deleteMemory: (id: string, reason = 'user request') => api.delete<void>(`/memories/${encodeURIComponent(id)}?reason=${encodeURIComponent(reason)}`),
  purgeMemory: (id: string, reason = 'user request') => api.post<void>(`/memories/${encodeURIComponent(id)}/purge`, { reason }),
  uploadAttachment: async (conversationId: string, file: File) => {
    const token = api.getToken(); const form = new FormData(); form.append('file', file)
    const response = await fetch(`/api/conversations/${encodeURIComponent(conversationId)}/attachments`, { method: 'POST', headers: token ? { Authorization: `Bearer ${token}` } : {}, body: form })
    if (!response.ok) throw new Error(await responseError(response, `附件上传失败：${response.status}`))
    return (await response.json()).data as { attachmentId: string; name: string; sizeBytes: number; status: string }
  },
  deleteAttachment: (conversationId: string, attachmentId: string) => api.delete<void>(`/conversations/${encodeURIComponent(conversationId)}/attachments/${encodeURIComponent(attachmentId)}`),
  plansForChat: (conversationId: string) => api.get<Array<{planId:string; goal:string; status:string}>>(`/plans/chat/${encodeURIComponent(conversationId)}`),
  approvePlan: (planId: string) => api.post<string>(`/plans/${encodeURIComponent(planId)}/approve`),
  rejectPlan: (planId: string) => api.post<string>(`/plans/${encodeURIComponent(planId)}/reject`),

  streamChat: (params: AgentRequest, callbacks: StreamCallbacks, token?: string | null): { close: () => void } => {
    const controller = new AbortController()
    const dispatch = (eventName: string, raw: string) => {
      try {
        const data = JSON.parse(raw)
        if (eventName === 'token') callbacks.onToken((data as StreamTokenEvent).content)
        else if (eventName === 'tool_call') callbacks.onToolCall?.((data as StreamToolCallEvent).toolName, (data as StreamToolCallEvent).arguments)
        else if (eventName === 'tool_result') callbacks.onToolResult?.((data as StreamToolResultEvent).toolName, (data as StreamToolResultEvent).result)
        else if (eventName === 'done') callbacks.onDone?.(data as StreamDoneEvent)
        else if (eventName === 'security') callbacks.onSecurity?.(data as StreamSecurityEvent)
        else if (eventName === 'error') callbacks.onError?.((data as StreamErrorEvent).message)
      } catch { callbacks.onError?.('SSE response parsing failed') }
    }
    void (async () => {
      try {
        const response = await fetch('/api/agent/chat/stream', {
          method: 'POST', signal: controller.signal,
          headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
          body: JSON.stringify({ ...params, stream: true, requestId: params.requestId ?? crypto.randomUUID() }),
        })
        if (!response.ok) throw new Error(await responseError(response, `请求失败：HTTP ${response.status}`))
        if (!response.body) throw new Error('未收到服务端响应流，请重试')
        const reader = response.body.getReader(); const decoder = new TextDecoder(); let buffer = ''
        while (!controller.signal.aborted) {
          const { value, done } = await reader.read(); if (done) break
          buffer += decoder.decode(value, { stream: true })
          let boundary: number
          while ((boundary = buffer.indexOf('\n\n')) >= 0) {
            const frame = buffer.slice(0, boundary); buffer = buffer.slice(boundary + 2)
            const event = frame.match(/^event:\s*(.+)$/m)?.[1]?.trim() ?? 'message'
            const data = frame.match(/^data:\s*(.+)$/m)?.[1]
            if (data) dispatch(event, data)
          }
        }
      } catch (error) { if (!controller.signal.aborted) callbacks.onError?.(error instanceof Error ? error.message : '') }
    })()
    return { close: () => controller.abort() }
  },
}

/** Preserve actionable validation errors (for example unreadable or unavailable attachments). */
async function responseError(response: Response, fallback: string): Promise<string> {
  try {
    const body: unknown = await response.json()
    if (body && typeof body === 'object' && 'message' in body && typeof body.message === 'string' && body.message.trim()) {
      return body.message
    }
  } catch { /* A gateway may return HTML or an empty body; retain the safe status fallback. */ }
  return fallback
}
