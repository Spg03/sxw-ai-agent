import { useState, useEffect } from 'react'
import { hermesApi, type HermesCandidate } from '../api/hermes'
import { useAuth } from '../contexts/AuthContext'
import { Brain, Check, X, RotateCw, Sparkles, BookOpen, TestTube, ShieldAlert, AlertTriangle } from 'lucide-react'
import Pagination from '../components/Pagination'

const TYPE_META: Record<string, { icon: typeof Sparkles; color: string; label: string }> = {
  MEMORY: { icon: Brain, color: 'from-purple-500 to-indigo-500', label: '记忆' },
  KNOWLEDGE: { icon: BookOpen, color: 'from-sky-500 to-blue-500', label: '知识' },
  EVAL_CASE: { icon: TestTube, color: 'from-emerald-500 to-teal-500', label: '评测用例' },
  AGENT_RULE: { icon: ShieldAlert, color: 'from-amber-500 to-orange-500', label: 'Agent 规则' },
  PROMPT_IMPROVEMENT: { icon: Sparkles, color: 'from-rose-500 to-pink-500', label: 'Prompt 改进' },
  TOOL_IMPROVEMENT: { icon: Sparkles, color: 'from-teal-500 to-cyan-500', label: '工具改进' },
}

const STATUS_STYLES: Record<string, string> = {
  PENDING: 'bg-amber-500/20 text-amber-300',
  APPROVED: 'bg-sky-500/20 text-sky-300',
  REJECTED: 'bg-rose-500/20 text-rose-300',
  APPLIED: 'bg-emerald-500/20 text-emerald-300',
  APPLY_FAILED: 'bg-rose-500/20 text-rose-300',
}

type Tab = 'pending' | 'failed'

export default function Hermes() {
  const { user } = useAuth()
  const [tab, setTab] = useState<Tab>('pending')
  const [candidates, setCandidates] = useState<HermesCandidate[]>([])
  const [loading, setLoading] = useState(true)
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [actingId, setActingId] = useState<string | null>(null)

  useEffect(() => {
    const controller = new AbortController()
    loadCandidates(controller.signal)
    return () => controller.abort()
  }, [tab])

  const loadCandidates = async (signal?: AbortSignal) => {
    setLoading(true)
    try {
      const res = tab === 'pending'
        ? await hermesApi.listPending({ signal })
        : await hermesApi.listFailed({ signal })
      if (res.code === 0) {
        setCandidates(res.data ?? [])
      }
    } catch (err) {
      if (err instanceof DOMException && err.name === 'AbortError') return
      console.error('Failed to load candidates', err)
    } finally {
      setLoading(false)
    }
  }

  const handleApprove = async (candidateId: string) => {
    setActingId(candidateId)
    try {
      const res = await hermesApi.approve(candidateId, user?.username || 'admin')
      if (res.code === 0) {
        // 操作成功后从列表移除（已不再是 PENDING）
        setCandidates(prev => prev.filter(c => c.candidateId !== candidateId))
      }
    } catch (err) {
      console.error('Failed to approve', err)
    } finally {
      setActingId(null)
    }
  }

  const handleReject = async (candidateId: string) => {
    setActingId(candidateId)
    try {
      const res = await hermesApi.reject(candidateId, user?.username || 'admin')
      if (res.code === 0) {
        setCandidates(prev => prev.filter(c => c.candidateId !== candidateId))
      }
    } catch (err) {
      console.error('Failed to reject', err)
    } finally {
      setActingId(null)
    }
  }

  const handleRetry = async (candidateId: string) => {
    setActingId(candidateId)
    try {
      const res = await hermesApi.retry(candidateId, user?.username || 'admin')
      if (res.code === 0) {
        setCandidates(prev => prev.filter(c => c.candidateId !== candidateId))
      }
    } catch (err) {
      console.error('Failed to retry', err)
    } finally {
      setActingId(null)
    }
  }

  const paged = candidates.slice((page - 1) * pageSize, page * pageSize)

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-slate-100">Hermes 复盘</h1>
          <p className="text-slate-400 mt-2">审核 Agent 自动生成的改进候选（记忆 / 知识 / 评测 / 规则）</p>
        </div>
        <div className="flex items-center gap-1 p-1 rounded-lg bg-white/5">
          <button
            onClick={() => { setTab('pending'); setPage(1) }}
            className={`px-4 py-2 rounded-md text-sm font-medium transition-colors ${
              tab === 'pending' ? 'bg-amber-500/20 text-amber-300' : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            待审核
          </button>
          <button
            onClick={() => { setTab('failed'); setPage(1) }}
            className={`px-4 py-2 rounded-md text-sm font-medium transition-colors ${
              tab === 'failed' ? 'bg-rose-500/20 text-rose-300' : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            应用失败
          </button>
        </div>
      </div>

      {/* Candidates */}
      {loading ? (
        <div className="text-center py-12 text-slate-400">加载中...</div>
      ) : paged.length === 0 ? (
        <div className="text-center py-12">
          <div className="w-20 h-20 rounded-full bg-gradient-to-br from-purple-500/20 to-indigo-500/20 flex items-center justify-center mx-auto mb-4">
            {tab === 'pending' ? <Brain size={32} className="text-purple-400" /> : <AlertTriangle size={32} className="text-rose-400" />}
          </div>
          <h2 className="text-xl font-semibold text-slate-200 mb-2">
            {tab === 'pending' ? '暂无待审核候选' : '暂无失败候选'}
          </h2>
          <p className="text-sm text-slate-400">
            {tab === 'pending' ? 'Agent 运行后会自动生成改进候选' : '没有应用失败的候选记录'}
          </p>
        </div>
      ) : (
        <div className="space-y-4">
          {paged.map(c => {
            const meta = TYPE_META[c.type] || TYPE_META.MEMORY
            const TypeIcon = meta.icon
            return (
              <div key={c.candidateId} className="glass rounded-xl p-6 hover:border-purple-500/30 transition-all">
                <div className="flex items-start justify-between mb-3">
                  <div className="flex items-center gap-3">
                    <div className={`w-10 h-10 rounded-lg bg-gradient-to-br ${meta.color} flex items-center justify-center flex-shrink-0`}>
                      <TypeIcon size={18} className="text-white" />
                    </div>
                    <div>
                      <h3 className="text-sm font-semibold text-slate-200">{c.title}</h3>
                      <div className="flex items-center gap-2 mt-1">
                        <span className="px-2 py-0.5 rounded-full bg-purple-500/20 text-xs text-purple-300">{meta.label}</span>
                        <span className={`px-2 py-0.5 rounded-full text-xs ${STATUS_STYLES[c.status] || STATUS_STYLES.PENDING}`}>{c.status}</span>
                        <span className="text-xs text-slate-500">置信度 {(c.confidence * 100).toFixed(0)}%</span>
                      </div>
                    </div>
                  </div>
                  <div className="flex items-center gap-2">
                    {tab === 'pending' && (
                      <>
                        <button
                          onClick={() => handleApprove(c.candidateId)}
                          disabled={actingId === c.candidateId}
                          className="flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-semibold bg-emerald-500/20 text-emerald-300 hover:bg-emerald-500/30 transition-colors disabled:opacity-50"
                        >
                          <Check size={14} /> 批准
                        </button>
                        <button
                          onClick={() => handleReject(c.candidateId)}
                          disabled={actingId === c.candidateId}
                          className="flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-semibold bg-rose-500/20 text-rose-300 hover:bg-rose-500/30 transition-colors disabled:opacity-50"
                        >
                          <X size={14} /> 拒绝
                        </button>
                      </>
                    )}
                    {tab === 'failed' && (
                      <button
                        onClick={() => handleRetry(c.candidateId)}
                        disabled={actingId === c.candidateId}
                        className="flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-semibold bg-amber-500/20 text-amber-300 hover:bg-amber-500/30 transition-colors disabled:opacity-50"
                      >
                        <RotateCw size={14} /> 重试
                      </button>
                    )}
                  </div>
                </div>

                <pre className="p-4 rounded-lg bg-black/30 border border-white/10 text-xs text-slate-300 overflow-x-auto whitespace-pre-wrap font-mono max-h-40 overflow-y-auto">
                  {c.content}
                </pre>

                <div className="flex items-center gap-4 mt-3 text-xs text-slate-500">
                  {c.sourceTraceId && <span>Trace: {c.sourceTraceId.slice(-8)}</span>}
                  <span>会话: {c.chatId?.slice(-8)}</span>
                  <span>创建: {new Date(c.createdAt).toLocaleString('zh-CN')}</span>
                  {c.reviewedBy && <span>审核人: {c.reviewedBy}</span>}
                </div>
              </div>
            )
          })}
        </div>
      )}

      <Pagination
        currentPage={page}
        totalPages={Math.max(1, Math.ceil(candidates.length / pageSize))}
        onPageChange={setPage}
        pageSize={pageSize}
        onPageSizeChange={(size) => { setPageSize(size); setPage(1) }}
      />
    </div>
  )
}
