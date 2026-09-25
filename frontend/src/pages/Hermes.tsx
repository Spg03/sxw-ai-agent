import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Brain, Check, X, RotateCw, Sparkles, BookOpen, TestTube, ShieldCheck, AlertTriangle, Search } from 'lucide-react'
import { hermesApi, type HermesCandidate } from '../api/hermes'
import { useAuth } from '../contexts/AuthContext'
import { ConsoleHeader, ConsoleDialog, ConsoleNotice, ConsolePager, EmptyState, StatusBadge } from '../components/ConsoleUI'
import { formatDate, resultData, errorMessage } from '../utils/console'

const TYPES = {
  MEMORY: { icon: Brain, label: '记忆', tone: '' }, KNOWLEDGE: { icon: BookOpen, label: '知识', tone: 'knowledge' },
  EVAL_CASE: { icon: TestTube, label: '评测用例', tone: 'eval' }, AGENT_RULE: { icon: ShieldCheck, label: 'Agent 规则', tone: 'rule' },
  PROMPT_IMPROVEMENT: { icon: Sparkles, label: 'Prompt 改进', tone: '' }, TOOL_IMPROVEMENT: { icon: Sparkles, label: '工具改进', tone: 'knowledge' },
}
type Action = 'approve' | 'reject' | 'retry'
const ACTION_LABELS = { approve: '批准', reject: '拒绝', retry: '重试应用' }

export default function Hermes() {
  const { user } = useAuth()
  const [tab, setTab] = useState<'pending' | 'failed'>('pending')
  const [data, setData] = useState<{ pending: HermesCandidate[]; failed: HermesCandidate[] }>({ pending: [], failed: [] })
  const [loading, setLoading] = useState(true)
  const [reload, setReload] = useState(0)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [query, setQuery] = useState('')
  const [type, setType] = useState('ALL')
  const [page, setPage] = useState(1)
  const [size, setSize] = useState(10)
  const [expanded, setExpanded] = useState<string[]>([])
  const [confirmation, setConfirmation] = useState<{ candidate: HermesCandidate; action: Action } | null>(null)
  const [busy, setBusy] = useState(false)
  const [actionError, setActionError] = useState('')

  const canReview = user?.role === 'ADMIN'
  useEffect(() => {
    if (!canReview) { setLoading(false); return }
    const controller = new AbortController()
    setLoading(true); setError('')
    Promise.all([hermesApi.listPending({ signal: controller.signal }), hermesApi.listFailed({ signal: controller.signal })])
      .then(([pending, failed]) => { if (!controller.signal.aborted) setData({ pending: resultData(pending) ?? [], failed: resultData(failed) ?? [] }) })
      .catch(err => { if (!controller.signal.aborted) setError(errorMessage(err)) })
      .finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [reload, canReview])

  const filtered = data[tab].filter(c => (type === 'ALL' || c.type === type) && [c.title, c.content, c.sourceTraceId].join(' ').toLowerCase().includes(query.trim().toLowerCase()))
  const currentPage = Math.min(page, Math.max(1, Math.ceil(filtered.length / size)))
  const ask = (candidate: HermesCandidate, action: Action) => { setActionError(''); setConfirmation({ candidate, action }) }
  const act = async () => {
    if (!confirmation || busy) return
    if (!user?.username) { setActionError('无法获取审核人信息，请重新登录'); return }
    setBusy(true); setActionError('')
    try {
      resultData(await hermesApi[confirmation.action](confirmation.candidate.candidateId))
      setNotice(confirmation.action === 'approve' ? '已提交批准，将按审核流程应用；如应用失败，可在“应用失败”中查看。' : '已' + ACTION_LABELS[confirmation.action] + '「' + confirmation.candidate.title + '」')
      setConfirmation(null); setReload(n => n + 1)
    } catch (err) { setActionError(errorMessage(err)) } finally { setBusy(false) }
  }

  if (!canReview) return <div className="console-page"><EmptyState icon={ShieldCheck} title="需要管理员审核权限" description="这里的候选会影响共享记忆、知识与规则，请联系管理员审核。个人记忆可在对话的记忆管理中维护。" /></div>

  return <div className="console-page">
    <ConsoleHeader icon={Sparkles} eyebrow="HERMES · REVIEW" title="Hermes 复盘" description="把执行经验转化为改进，让每一次学习都经过你的审核。">
      <button className="console-button" disabled={loading} onClick={() => setReload(n => n + 1)}><RotateCw size={16} className={loading ? 'console-spin' : ''} />刷新候选</button>
    </ConsoleHeader>
    <div className="console-stats">
      <div className="console-stat"><ShieldCheck /><div><strong>{loading ? '—' : data.pending.length}</strong><span>等待你的审核</span></div></div>
      <div className="console-stat"><AlertTriangle /><div><strong>{loading ? '—' : data.failed.length}</strong><span>失败或中断 · 可重试</span></div></div>
      <div className="console-stat"><Brain /><div><strong>{loading ? '—' : new Set([...data.pending, ...data.failed].map(c => c.type)).size}</strong><span>当前候选类型</span></div></div>
    </div>
    <ConsoleNotice message={error} error /><ConsoleNotice message={notice} />
    <section className="console-panel" aria-label="改进候选">
      <div className="console-panel-header"><div><h2><Sparkles size={19} />改进候选</h2><p>先核对内容与来源，再决定是否沉淀到系统。</p></div></div>
      <div className="console-toolbar">
        <div className="console-tabs" aria-label="候选状态">
          <button aria-pressed={tab === 'pending'} onClick={() => { setTab('pending'); setPage(1) }}>待审核 <span>{data.pending.length}</span></button>
          <button aria-pressed={tab === 'failed'} onClick={() => { setTab('failed'); setPage(1) }}>应用失败 <span>{data.failed.length}</span></button>
        </div>
        <label className="console-search"><Search size={17} /><input aria-label="搜索候选" placeholder="搜索标题、内容或 Trace ID" value={query} onChange={e => { setQuery(e.target.value); setPage(1) }} /></label>
        <select className="console-select" aria-label="候选类型" value={type} onChange={e => { setType(e.target.value); setPage(1) }}><option value="ALL">全部类型</option>{Object.entries(TYPES).map(([key, value]) => <option key={key} value={key}>{value.label}</option>)}</select>
      </div>
      {loading ? <EmptyState icon={Sparkles} title="正在加载候选…" loading /> : error ? <EmptyState icon={AlertTriangle} title="候选加载失败" description="请检查服务状态，然后重新加载。"><button className="console-button" onClick={() => setReload(n => n + 1)}>重新加载</button></EmptyState>
        : !filtered.length ? <EmptyState icon={tab === 'failed' ? ShieldCheck : Sparkles} title={query || type !== 'ALL' ? '没有匹配的候选' : tab === 'failed' ? '暂无应用失败的候选' : '当前没有待审核候选'} description={query || type !== 'ALL' ? '换个关键词或清除筛选，查看其他候选。' : 'Hermes 会从执行记录中提炼经验，新的改进候选将在这里等待审核。'}>
          {(query || type !== 'ALL') && <button className="console-button" onClick={() => { setQuery(''); setType('ALL'); setPage(1) }}>清除筛选</button>}
        </EmptyState> : <div className="console-list">{filtered.slice((currentPage - 1) * size, currentPage * size).map(c => {
          const meta = TYPES[c.type as keyof typeof TYPES] || { icon: Sparkles, label: c.type, tone: '' }
          const Icon = meta.icon
          const open = expanded.includes(c.candidateId)
          return <article key={c.candidateId} className="console-card">
            <div className="console-card-head"><div className="console-card-title"><span className={'console-type-icon ' + meta.tone}><Icon size={21} /></span><div><h3>{c.title}</h3><div className="console-tags"><span className="console-tag">{meta.label}</span><StatusBadge status={c.status} /><span>置信度 {c.confidence == null ? '未提供' : Math.round(Math.max(0, Math.min(1, c.confidence)) * 100) + '%'}</span></div></div></div>
              <div className="console-actions">{tab === 'pending' ? <><button className="console-button" onClick={() => ask(c, 'reject')}><X size={15} />拒绝</button><button className="console-button success" onClick={() => ask(c, 'approve')}><Check size={15} />批准</button></> : <button className="console-button primary" onClick={() => ask(c, 'retry')}><RotateCw size={15} />重试应用</button>}</div>
            </div>
            <p className="console-content"><span className={open ? '' : 'console-clamped'}>{c.content}</span></p>
            <div className="console-card-foot"><div className="console-meta">
              <span>创建于 {formatDate(c.createdAt)}</span>
              {c.sourceTraceId && <Link to={'/traces?traceId=' + encodeURIComponent(c.sourceTraceId)} title={c.sourceTraceId}>查看来源 {c.sourceTraceId.slice(-8)} ↗</Link>}
              {c.chatId && <span title={c.chatId}>会话 {c.chatId.slice(-8)}</span>}
              {c.reviewedBy && <span>审核人 {c.reviewedBy}</span>}
            </div><button className="console-link" aria-expanded={open} onClick={() => setExpanded(prev => open ? prev.filter(id => id !== c.candidateId) : [...prev, c.candidateId])}>{open ? '收起内容' : '展开全文'}</button></div>
          </article>
        })}</div>}
      {!loading && !error && filtered.length > 0 && <ConsolePager page={currentPage} size={size} total={filtered.length} onPage={setPage} onSize={n => { setSize(n); setPage(1) }} />}
    </section>
    {confirmation && <ConsoleDialog title={ACTION_LABELS[confirmation.action] + '候选'} description={confirmation.action === 'approve' ? '批准后将触发候选应用，请确认内容准确、适合长期使用。' : confirmation.action === 'reject' ? '拒绝后，此候选将从待审核列表移除，请确认你的决定。' : '将重新尝试应用此候选，请确认此前的问题已处理。'} onClose={() => setConfirmation(null)} busy={busy}>
      <strong>{confirmation.candidate.title}</strong><p className="console-content">{confirmation.candidate.content}</p><ConsoleNotice message={actionError} error />
      <footer><button className="console-button" disabled={busy} onClick={() => setConfirmation(null)}>取消</button><button className={'console-button ' + (confirmation.action === 'reject' ? 'danger' : 'primary')} disabled={busy} onClick={act}>{busy ? '处理中…' : '确认' + ACTION_LABELS[confirmation.action]}</button></footer>
    </ConsoleDialog>}
  </div>
}
