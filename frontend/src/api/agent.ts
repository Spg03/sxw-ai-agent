import { api } from './client'

export interface AgentRequest {
  chatId: string
  profile: 'LOVE' | 'GENERAL' | 'HERMES'
  message: string
  stream?: boolean
  metadata?: Record<string, any>
  mode?: 'CHAT' | 'PLAN' | 'EXECUTE'
  planId?: string
  enabledTools?: string[]
  webSearchEnabled?: boolean
  attachmentIds?: string[]
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

/** SSE 流式事件类型 */
export interface StreamTokenEvent {
  type: 'token'
  content: string
}

export interface StreamToolCallEvent {
  type: 'tool_call'
  toolName: string
  arguments: string
}

export interface StreamToolResultEvent {
  type: 'tool_result'
  toolName: string
  result: string
}

export interface StreamDoneEvent {
  type: 'done'
  requestId: string
  traceId: string
  answer: string
  latencyMs: number
}

export interface StreamErrorEvent {
  type: 'error'
  message: string
}

export type StreamEvent =
  | StreamTokenEvent
  | StreamToolCallEvent
  | StreamToolResultEvent
  | StreamDoneEvent
  | StreamErrorEvent

export interface StreamCallbacks {
  onToken: (content: string) => void
  onToolCall?: (toolName: string, args: string) => void
  onToolResult?: (toolName: string, result: string) => void
  onDone?: (event: StreamDoneEvent) => void
  onError?: (message: string) => void
}

export const agentApi = {
  chat: (req: AgentRequest) => api.post<AgentResponse>('/agent/chat', req),
  modes: () => api.get<AgentMode[]>('/agent/profiles'),
  clearMemory: (chatId: string) => api.delete<void>(`/agent/chat/${chatId}/memory`),
  listConversations: () => api.get<ConversationSummary[]>('/conversations'),
  createConversation: (profile: ConversationSummary['profile'] = 'GENERAL', title?: string) => api.post<ConversationSummary>('/conversations', { profile, title }),
  conversationMessages: (id: string) => api.get<ConversationMessage[]>(`/conversations/${encodeURIComponent(id)}/messages`),
  updateConversation: (id: string, body: Partial<Pick<ConversationSummary, 'title' | 'profile' | 'pinned'>>) => api.patch<ConversationSummary>(`/conversations/${encodeURIComponent(id)}`, body),
  deleteConversation: (id: string) => api.delete<void>(`/conversations/${encodeURIComponent(id)}`),
  tools: (profile: string) => api.get<Array<{name: string; description: string; riskLevel: string; requiresApproval: boolean}>>(`/agent/tools?profile=${encodeURIComponent(profile)}`),

  /**
   * SSE 流式对话
   * 使用 EventSource 连接后端 SSE 端点，实时接收 AI 生成的 token。
   * 返回 EventSource 实例，调用方可通过 close() 中断连接。
   */
  streamChat: (
    params: { chatId: string; message: string; profile: string },
    callbacks: StreamCallbacks,
    token?: string | null
  ): EventSource => {
    const searchParams = new URLSearchParams({
      chatId: params.chatId,
      message: params.message,
      profile: params.profile,
    })
    if (token) {
      searchParams.set('token', token)
    }

    const eventSource = new EventSource(`/api/agent/chat/stream?${searchParams.toString()}`)

    eventSource.addEventListener('token', (event: MessageEvent) => {
      try {
        const data: StreamTokenEvent = JSON.parse(event.data)
        callbacks.onToken(data.content)
      } catch (e) {
        console.error('Failed to parse token event:', e)
      }
    })

    eventSource.addEventListener('tool_call', (event: MessageEvent) => {
      try {
        const data: StreamToolCallEvent = JSON.parse(event.data)
        callbacks.onToolCall?.(data.toolName, data.arguments)
      } catch (e) {
        console.error('Failed to parse tool_call event:', e)
      }
    })

    eventSource.addEventListener('tool_result', (event: MessageEvent) => {
      try {
        const data: StreamToolResultEvent = JSON.parse(event.data)
        callbacks.onToolResult?.(data.toolName, data.result)
      } catch (e) {
        console.error('Failed to parse tool_result event:', e)
      }
    })

    eventSource.addEventListener('done', (event: MessageEvent) => {
      try {
        const data: StreamDoneEvent = JSON.parse(event.data)
        callbacks.onDone?.(data)
      } catch (e) {
        console.error('Failed to parse done event:', e)
      }
      eventSource.close()
    })

    eventSource.addEventListener('error', (event: MessageEvent) => {
      // SSE 连接级别的 error 事件（无 data）或业务 error 事件
      if (event.data) {
        try {
          const data: StreamErrorEvent = JSON.parse(event.data)
          callbacks.onError?.(data.message)
        } catch {
          callbacks.onError?.('连接发生错误')
        }
      }
      // 连接断开时 EventSource 会自动重连，但如果是服务端主动关闭则不会
    })

    return eventSource
  },
}
