import { useEffect, useState } from 'react'
import { useSearchParams, Link } from 'react-router-dom'
import { Activity, Clock, Zap, RotateCw, Search, Copy, AlertTriangle, CheckCircle, MessageSquare } from 'lucide-react'
import { traceApi, type TraceRun } from '../api/traces'
import { ConsoleHeader, ConsoleNotice, ConsolePager, EmptyState, StatusBadge } from '../components/ConsoleUI'
import { formatDate, resultData, errorMessage } from '../utils/console'

function latency(start: string, end: string | null) {
  if (!end) return '进行中'
  const ms = new Date(end).getTime() - new Date(start).getTime()
  return !Number.isFinite(ms) || ms < 0 ? '—' : ms < 1000 ? ms + ' ms' : (ms / 1000).toFixed(1) + ' s'
}

export default function Traces() {
  const [params, setParams] = useSearchParams()
  const selectedId = params.get('traceId') || ''
  const [traces, setTraces] = useState<TraceRun[]>([])
  const [detail, setDetail] = useState<TraceRun | null>(null)
  const [loading, setLoading] = useState(true)
  const [detailLoading, setDetailLoading] = useState(false)
  const [error, setError] = useState('')
  const [detailError, setDetailError] = useState('')
  const [notice, setNotice] = useState('')
  const [reload, setReload] = useState(0)
  const [detailReload, setDetailReload] = useState(0)
  const [query, setQuery] = useState('')
  const [status, setStatus] = useState('ALL')
  const [page, setPage] = useState(1)
  const [size, setSize] = useState(10)

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true); setError('')
    // API supports a bounded recent list, not server-side page/size.
    traceApi.list(100, controller.signal).then(res => {
      if (!controller.signal.aborted) setTraces(resultData(res) ?? [])
    }).catch(err => { if (!controller.signal.aborted) setError(errorMessage(err)) })
      .finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [reload])

  useEffect(() => {
    const controller = new AbortController()
    setDetail(null); setDetailError(''); setDetailLoading(!!selectedId)
    if (selectedId) traceApi.getByTraceId(selectedId, controller.signal).then(res => {
      if (!controller.signal.aborted) {
        const value = resultData(res)
        if (!value) throw new Error('未找到这条追踪记录，可能已过期或不再保留。')
        setDetail(value)
      }
    }).catch(err => { if (!controller.signal.aborted) setDetailError(errorMessage(err)) })
      .finally(() => { if (!controller.signal.aborted) setDetailLoading(false) })
    return () => controller.abort()
  }, [selectedId, detailReload, reload])

  const filtered = traces.filter(t => (status === 'ALL' || t.status.toUpperCase() === status) && [t.traceId, t.chatId].join(' ').toLowerCase().includes(query.trim().toLowerCase()))
  const currentPage = Math.min(page, Math.max(1, Math.ceil(filtered.length / size)))
  const copyId = async () => {
    try { await navigator.clipboard.writeText(selectedId); setNotice('Trace ID 已复制。') }
    catch { setNotice('复制失败，请选中详情中的 Trace ID 手动复制。') }
  }

  return <div className="console-page">
    <ConsoleHeader icon={Activity} eyebrow="OBSERVABILITY · TRACES" title="执行追踪" description="沿着调用时间线，了解 Agent 如何思考、调用工具与完成任务。">
      <button className="console-button" disabled={loading} onClick={() => setReload(n => n + 1)}><RotateCw size={16} className={loading ? 'console-spin' : ''} />刷新记录</button>
    </ConsoleHeader>
    <div className="console-stats">
      <div className="console-stat"><Activity /><div><strong>{loading ? '—' : traces.length}</strong><span>最近追踪 · 最多 100 条</span></div></div>
      <div className="console-stat"><CheckCircle /><div><strong>{loading ? '—' : traces.filter(t => t.status.toUpperCase() === 'COMPLETED').length}</strong><span>当前记录中已完成</span></div></div>
      <div className="console-stat"><AlertTriangle /><div><strong>{loading ? '—' : traces.filter(t => t.status.toUpperCase() === 'FAILED').length}</strong><span>当前记录中执行失败</span></div></div>
    </div>
    <ConsoleNotice message={error} error /><ConsoleNotice message={notice} />
    <div className="console-trace-grid">
      <section className="console-panel" aria-label="追踪列表">
        <div className="console-panel-header"><div><h2><Activity size={19} />追踪列表</h2><p>最近 100 条记录，按时间倒序排列</p></div></div>
        <div className="console-toolbar"><label className="console-search"><Search size={16} /><input aria-label="搜索追踪" placeholder="Trace ID / 会话 ID" value={query} onChange={e => { setQuery(e.target.value); setPage(1) }} /></label>
          <select className="console-select" aria-label="追踪状态" value={status} onChange={e => { setStatus(e.target.value); setPage(1) }}><option value="ALL">全部状态</option><option value="COMPLETED">已完成</option><option value="FAILED">失败</option><option value="RUNNING">运行中</option></select>
        </div>
        {loading ? <EmptyState icon={Activity} title="正在加载记录…" loading /> : error ? <EmptyState icon={AlertTriangle} title="追踪列表加载失败" description="请检查服务状态后刷新。" /> : !filtered.length ? <EmptyState icon={Activity} title={traces.length ? '没有匹配的记录' : '还没有执行追踪'} description={traces.length ? '更换关键词或清除筛选后重试。' : '发起一次 Agent 对话后，可在这里查看执行过程。'}>
          {traces.length ? <button className="console-button" onClick={() => { setQuery(''); setStatus('ALL'); setPage(1) }}>清除筛选</button> : <Link className="console-button" to="/chat"><MessageSquare size={15} />开始对话</Link>}
        </EmptyState> : <div className="console-trace-list">{filtered.slice((currentPage - 1) * size, currentPage * size).map(trace => <button className="console-trace-item" key={trace.traceId} aria-pressed={selectedId === trace.traceId} aria-label={'查看追踪 ' + trace.traceId} onClick={() => { setNotice(''); setParams({ traceId: trace.traceId }) }}>
          <header><strong title={trace.traceId}># {trace.traceId.slice(-12)}</strong><StatusBadge status={trace.status} /></header>
          <div className="console-meta"><span><Zap size={12} style={{ display: 'inline' }} /> {trace.events?.length ?? 0} 事件</span><span><Clock size={12} style={{ display: 'inline' }} /> {latency(trace.startedAt, trace.finishedAt)}</span></div>
          <div className="console-meta"><span>{formatDate(trace.startedAt)}</span><span title={trace.chatId}>会话 {trace.chatId?.slice(-8) || '—'}</span></div>
        </button>)}</div>}
        {!loading && !error && filtered.length > 0 && <ConsolePager page={currentPage} size={size} total={filtered.length} onPage={setPage} onSize={n => { setSize(n); setPage(1) }} />}
      </section>
      <section className="console-panel console-trace-detail" aria-label="追踪详情" aria-busy={detailLoading}>
        {detailLoading ? <EmptyState icon={Activity} title="正在读取执行详情…" loading /> : detailError ? <EmptyState icon={AlertTriangle} title="无法加载追踪详情" description={detailError}><button className="console-button" onClick={() => setDetailReload(n => n + 1)}>重试加载</button></EmptyState>
          : detail ? <>
            <div className="console-panel-header"><div><h2><Zap size={19} />执行详情</h2><p>从请求开始到最终响应的完整记录</p></div><StatusBadge status={detail.status} /></div>
            <div className="console-trace-summary"><div className="console-card-head"><code>{detail.traceId}</code><button className="console-icon-button" title="复制 Trace ID" aria-label="复制 Trace ID" onClick={copyId}><Copy size={15} /></button></div>
              <p>会话 ID：{detail.chatId || '—'}</p><div className="console-tags"><span>开始 {formatDate(detail.startedAt)}</span><span>耗时 {latency(detail.startedAt, detail.finishedAt)}</span><span>{detail.events?.length ?? 0} 个事件</span></div></div>
            {!detail.events?.length ? <EmptyState icon={Zap} title="暂无事件明细" description="该追踪尚未记录执行事件，可稍后刷新查看。" /> : <ol className="console-timeline">{detail.events.map((event, index) => <li key={index}><article className="console-card">
              <div className="console-card-head"><h3>{event.phase || '执行事件'}{event.toolName && ' · ' + event.toolName}</h3><StatusBadge status={event.status || 'UNKNOWN'} label={event.status ? undefined : '未知状态'} /></div>
              <div className="console-tags"><span>步骤 {event.step}</span><span>{event.latencyMs ?? 0} ms</span><span>{formatDate(event.createdAt)}</span></div>
              {event.outputSummary && <p className="console-content">{event.outputSummary}</p>}
              {event.inputSummary && <details><summary>查看输入摘要</summary><p className="console-content">{event.inputSummary}</p></details>}
            </article></li>)}</ol>}
          </> : <EmptyState icon={Activity} title="选择一条追踪，展开执行过程" description="点击列表中的记录，查看各步骤的输入输出、工具调用和耗时。" />}
      </section>
    </div>
  </div>
}
