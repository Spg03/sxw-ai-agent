import { api } from './client'

export interface TraceEvent {
  traceId: string
  chatId: string
  step: number
  phase: string
  toolName: string
  inputSummary: string
  outputSummary: string
  status: string
  latencyMs: number
  createdAt: string
}

export interface TraceRun {
  traceId: string
  chatId: string
  startedAt: string
  finishedAt: string | null
  status: string
  events: TraceEvent[]
}

export const traceApi = {
  list: (limit = 50, signal?: AbortSignal, page = 1, size = 20) =>
    api.get<TraceRun[]>(`/agent/traces?limit=${limit}&page=${page}&size=${size}`, { signal }),
  getByChatId: (chatId: string, limit = 50, signal?: AbortSignal) =>
    api.get<TraceRun[]>(`/agent/traces/${chatId}?limit=${limit}`, { signal }),
  getByTraceId: (traceId: string, signal?: AbortSignal) =>
    api.get<TraceRun>(`/agent/traces/trace/${traceId}`, { signal }),
}
