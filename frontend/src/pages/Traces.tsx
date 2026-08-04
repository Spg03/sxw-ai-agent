import { useState, useEffect } from 'react'
import { traceApi, type TraceRun } from '../api/traces'
import { Activity, Clock, Zap, MessageSquare } from 'lucide-react'
import Pagination from '../components/Pagination'

function formatTime(iso: string): string {
  try {
    const d = new Date(iso)
    return d.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' })
  } catch {
    return iso
  }
}

function computeLatency(startedAt: string, finishedAt: string | null): string {
  if (!finishedAt) return '...'
  const ms = new Date(finishedAt).getTime() - new Date(startedAt).getTime()
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(1)}s`
}

export default function Traces() {
  const [traces, setTraces] = useState<TraceRun[]>([])
  const [selectedTrace, setSelectedTrace] = useState<TraceRun | null>(null)
  const [loading, setLoading] = useState(true)
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [totalPages, setTotalPages] = useState(1)

  useEffect(() => {
    const controller = new AbortController()
    loadTraces(controller.signal)
    return () => controller.abort()
  }, [page, pageSize])

  const loadTraces = async (signal?: AbortSignal) => {
    setLoading(true)
    try {
      const res = await traceApi.list(50, signal, page, pageSize)
      if (res.code === 0) {
        const data = res.data ?? []
        setTraces(data)
        // 后端已分页，根据返回数量估算是否还有更多页
        setTotalPages(data.length >= pageSize ? page + 1 : page)
      }
    } catch (err) {
      if (err instanceof DOMException && err.name === 'AbortError') return
      console.error('Failed to load traces', err)
    } finally {
      setLoading(false)
    }
  }

  const loadTraceDetail = async (traceId: string) => {
    try {
      const res = await traceApi.getByTraceId(traceId)
      if (res.code === 0 && res.data) {
        setSelectedTrace(res.data)
      }
    } catch (err) {
      console.error('Failed to load trace', err)
    }
  }

  const handlePageChange = (newPage: number) => {
    setPage(newPage)
    setSelectedTrace(null)
  }

  const handlePageSizeChange = (size: number) => {
    setPageSize(size)
    setPage(1)
    setSelectedTrace(null)
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-3xl font-bold text-slate-100">执行追踪</h1>
        <p className="text-slate-400 mt-2">查看 Agent 执行过程和工具调用链</p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Traces List */}
        <div className="lg:col-span-1 glass rounded-xl p-6">
          <h2 className="text-lg font-semibold text-slate-100 mb-4">追踪列表</h2>
          {loading ? (
            <div className="text-center py-8 text-slate-400">加载中...</div>
          ) : traces.length === 0 ? (
            <div className="text-center py-8">
              <div className="w-16 h-16 rounded-full bg-gradient-to-br from-sky-500/20 to-blue-500/20 flex items-center justify-center mx-auto mb-3">
                <Activity size={28} className="text-sky-400" />
              </div>
              <p className="text-sm text-slate-400">暂无追踪记录</p>
            </div>
          ) : (
            <div className="space-y-2">
              {traces.map(trace => (
                <div
                  key={trace.traceId}
                  onClick={() => loadTraceDetail(trace.traceId)}
                  className={`p-4 rounded-lg cursor-pointer transition-all ${
                    selectedTrace?.traceId === trace.traceId
                      ? 'bg-gradient-to-r from-sky-500/20 to-blue-500/20 border border-sky-500/30'
                      : 'bg-white/5 hover:bg-white/10'
                  }`}
                >
                  <div className="flex items-center justify-between mb-2">
                    <span className="text-xs font-mono text-slate-300" title={trace.traceId}>
                      {trace.traceId.slice(-8)}
                    </span>
                    <span className={`px-2 py-0.5 rounded-full text-xs ${
                      trace.status === 'completed'
                        ? 'bg-emerald-500/20 text-emerald-300'
                        : trace.status === 'failed'
                          ? 'bg-rose-500/20 text-rose-300'
                          : 'bg-amber-500/20 text-amber-300'
                    }`}>
                      {trace.status}
                    </span>
                  </div>
                  <div className="flex items-center gap-3 text-xs text-slate-400 mb-1">
                    <span className="flex items-center gap-1" title={trace.chatId}>
                      <MessageSquare size={12} />
                      {trace.chatId.slice(-6)}
                    </span>
                    <span className="flex items-center gap-1">
                      <Zap size={12} />
                      {trace.events?.length ?? 0} 事件
                    </span>
                    <span className="flex items-center gap-1">
                      <Clock size={12} />
                      {computeLatency(trace.startedAt, trace.finishedAt)}
                    </span>
                  </div>
                  <div className="text-xs text-slate-500">
                    {formatTime(trace.startedAt)}
                  </div>
                </div>
              ))}
            </div>
          )}
          <Pagination
            currentPage={page}
            totalPages={totalPages}
            onPageChange={handlePageChange}
            pageSize={pageSize}
            onPageSizeChange={handlePageSizeChange}
          />
        </div>

        {/* Trace Detail */}
        <div className="lg:col-span-2 glass rounded-xl p-6">
          {selectedTrace ? (
            <div>
              <div className="flex items-center justify-between mb-6">
                <div>
                  <h2 className="text-xl font-bold text-slate-100">追踪详情</h2>
                  <p className="text-xs font-mono text-slate-400 mt-1">{selectedTrace.traceId}</p>
                  <p className="text-xs text-slate-500 mt-0.5">chatId: {selectedTrace.chatId}</p>
                </div>
                <span className={`px-3 py-1 rounded-full text-sm ${
                  selectedTrace.status === 'completed'
                    ? 'bg-emerald-500/20 text-emerald-300'
                    : selectedTrace.status === 'failed'
                      ? 'bg-rose-500/20 text-rose-300'
                      : 'bg-amber-500/20 text-amber-300'
                }`}>
                  {selectedTrace.status}
                </span>
              </div>

              <div className="space-y-3">
                {(selectedTrace.events ?? []).map((event, idx) => (
                  <div key={idx} className="relative pl-8 pb-4">
                    {idx < (selectedTrace.events?.length ?? 0) - 1 && (
                      <div className="absolute left-3 top-4 bottom-0 w-px bg-white/10" />
                    )}
                    <div className="absolute left-0 top-1 w-6 h-6 rounded-full bg-gradient-to-br from-sky-500 to-blue-500 flex items-center justify-center shadow-lg shadow-sky-500/20">
                      <div className="w-2 h-2 rounded-full bg-white" />
                    </div>

                    <div className="p-4 rounded-lg bg-white/5 border border-white/10">
                      <div className="flex items-center justify-between mb-2">
                        <div className="flex items-center gap-3">
                          <span className="px-2 py-0.5 rounded-full bg-sky-500/20 text-xs text-sky-300">
                            {event.phase}
                          </span>
                          {event.toolName && (
                            <span className="text-xs text-slate-300">{event.toolName}</span>
                          )}
                        </div>
                        <div className="flex items-center gap-2 text-xs text-slate-400">
                          <Clock size={12} />
                          {event.latencyMs}ms
                        </div>
                      </div>
                      <div className="flex items-center gap-2 text-xs text-slate-500 mb-2">
                        <span>步骤 {event.step}</span>
                        <span className={`px-2 py-0.5 rounded-full ${
                          event.status === 'success'
                            ? 'bg-emerald-500/20 text-emerald-300'
                            : event.status === 'error'
                              ? 'bg-rose-500/20 text-rose-300'
                              : 'bg-slate-500/20 text-slate-300'
                        }`}>
                          {event.status}
                        </span>
                      </div>
                      {event.outputSummary && (
                        <p className="text-xs text-slate-400 mt-2">{event.outputSummary}</p>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          ) : (
            <div className="flex items-center justify-center h-full">
              <div className="text-center">
                <div className="w-20 h-20 rounded-full bg-gradient-to-br from-sky-500/20 to-blue-500/20 flex items-center justify-center mx-auto mb-4">
                  <Activity size={32} className="text-sky-400" />
                </div>
                <h2 className="text-xl font-semibold text-slate-200 mb-2">选择一个追踪</h2>
                <p className="text-sm text-slate-400">从左侧列表选择追踪查看执行详情</p>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
