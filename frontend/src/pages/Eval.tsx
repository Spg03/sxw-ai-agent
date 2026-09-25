import { useEffect, useState } from 'react'
import { Plus, Play, Trash2, TestTube, RotateCw, Search, BarChart3, CheckCircle, AlertTriangle } from 'lucide-react'
import { evalApi, type EvalCase, type EvalRun } from '../api/eval'
import { ConsoleHeader, ConsoleDialog, ConsoleNotice, ConsolePager, EmptyState, StatusBadge } from '../components/ConsoleUI'
import { formatDate, resultData, errorMessage } from '../utils/console'

const PROFILES = { GENERAL: '通用助手', LOVE: '恋爱咨询', HERMES: '情感陪伴' }
const MODES = { KEYWORD_ONLY: '关键词校验', LLM_ONLY: '模型评判', ALL: '全部校验通过', ANY: '任一校验通过' }
const initialCase = { name: '', input: '', expectedOutput: '', profileCode: 'GENERAL', validationMode: 'KEYWORD_ONLY', judgeCriteria: '' }
type Dialog = 'create' | 'run' | null

export default function Eval() {
  const [cases, setCases] = useState<EvalCase[]>([])
  const [runs, setRuns] = useState<EvalRun[]>([])
  const [loading, setLoading] = useState(true)
  const [reload, setReload] = useState(0)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [dialog, setDialog] = useState<Dialog>(null)
  const [deleting, setDeleting] = useState<EvalCase | null>(null)
  const [busy, setBusy] = useState(false)
  const [formError, setFormError] = useState('')
  const [newCase, setNewCase] = useState(initialCase)
  const [runName, setRunName] = useState('')
  const [runProfile, setRunProfile] = useState('GENERAL')
  const [query, setQuery] = useState('')
  const [caseStatus, setCaseStatus] = useState('ALL')
  const [casePage, setCasePage] = useState(1)
  const [caseSize, setCaseSize] = useState(10)
  const [runPage, setRunPage] = useState(1)
  const [runSize, setRunSize] = useState(10)

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true); setError('')
    Promise.all([evalApi.listCases({ signal: controller.signal }), evalApi.listRuns({ signal: controller.signal })])
      .then(([c, r]) => { if (!controller.signal.aborted) { setCases(resultData(c) ?? []); setRuns(resultData(r) ?? []) } })
      .catch(err => { if (!controller.signal.aborted) setError(errorMessage(err)) })
      .finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [reload])

  const hasPendingRuns = runs.some(run => run.status === 'PENDING' || run.status === 'RUNNING')
  useEffect(() => {
    if (!hasPendingRuns) return
    const controller = new AbortController()
    let timer: ReturnType<typeof setTimeout>
    const poll = async () => {
      try {
        const response = await evalApi.listRuns({ signal: controller.signal })
        if (!controller.signal.aborted) setRuns(resultData(response) ?? [])
      } catch (err) {
        if (!controller.signal.aborted) setError(errorMessage(err))
      } finally {
        if (!controller.signal.aborted) timer = setTimeout(poll, 3000)
      }
    }
    timer = setTimeout(poll, 3000)
    return () => { controller.abort(); clearTimeout(timer) }
  }, [hasPendingRuns])

  const changeStatus = async (item: EvalCase) => {
    if (busy) return
    setBusy(true); setError('')
    try {
      const updated = resultData(await evalApi.setStatus(item.caseId, item.status === 'ACTIVE' ? 'disable' : 'activate'))
      setCases(prev => prev.map(c => c.caseId === updated.caseId ? updated : c))
      setNotice(updated.status === 'ACTIVE' ? '用例已启用，可参与评测。' : '用例已停用。')
    } catch (err) { setError(errorMessage(err)) } finally { setBusy(false) }
  }

  const filtered = cases.filter(c => (caseStatus === 'ALL' || c.status === caseStatus) && [c.caseName, c.inputPrompt].join(' ').toLowerCase().includes(query.trim().toLowerCase()))
  const cp = Math.min(casePage, Math.max(1, Math.ceil(filtered.length / caseSize)))
  const rp = Math.min(runPage, Math.max(1, Math.ceil(runs.length / runSize)))
  const active = cases.filter(c => c.status === 'ACTIVE')
  const runnable = active.filter(c => c.profileCode === runProfile)
  const completed = runs.find(r => r.status === 'COMPLETED')
  const needsJudge = newCase.validationMode !== 'KEYWORD_ONLY'
  const needsKeyword = newCase.validationMode !== 'LLM_ONLY'
  const validCase = newCase.name.trim() && newCase.input.trim() && (!needsJudge || newCase.judgeCriteria.trim()) && (!needsKeyword || newCase.expectedOutput.trim())
  const open = (value: Dialog) => { setFormError(''); setDialog(value) }

  const createCase = async () => {
    if (!validCase || busy) return
    setBusy(true); setFormError('')
    try {
      resultData(await evalApi.createCase({ ...newCase, name: newCase.name.trim(), input: newCase.input.trim(), expectedOutput: newCase.expectedOutput.trim() || undefined, judgeCriteria: newCase.judgeCriteria.trim() || undefined }))
      setNotice('用例已保存为草稿。确认内容后，点击用例卡片上的“启用”即可参与评测。')
      setNewCase(initialCase); setDialog(null); setReload(n => n + 1)
    } catch (err) { setFormError(errorMessage(err)) } finally { setBusy(false) }
  }
  const deleteCase = async () => {
    if (!deleting || busy) return
    setBusy(true); setFormError('')
    try {
      resultData(await evalApi.deleteCase(deleting.caseId))
      setCases(prev => prev.filter(c => c.caseId !== deleting.caseId)); setDeleting(null); setNotice('测试用例已删除。')
    } catch (err) { setFormError(errorMessage(err)) } finally { setBusy(false) }
  }
  const startRun = async () => {
    if (!runName.trim() || !runnable.length || busy) return
    setBusy(true); setFormError('')
    try {
      const run = resultData(await evalApi.startRun(runName.trim(), runProfile, runnable.map(c => c.caseId)))
      setRuns(prev => [run, ...prev.filter(r => r.runId !== run.runId)]); setRunPage(1)
      setDialog(null); setRunName(''); setNotice(run.status === 'FAILED' ? '评测运行失败，请查看运行记录中的错误信息。' : '评测已加入后台队列，运行状态会自动更新，可以离开此页面。')
    } catch (err) { setFormError(errorMessage(err) + '。如请求中断，请先刷新运行记录，确认状态后再重试。') } finally { setBusy(false) }
  }

  return <div className="console-page">
    <ConsoleHeader icon={TestTube} eyebrow="EVALUATION · QUALITY" title="评测管理" description="用可重复的测试，验证 Agent 的回答质量与能力变化。">
      <button className="console-button" disabled={loading} onClick={() => setReload(n => n + 1)}><RotateCw size={16} className={loading ? 'console-spin' : ''} />刷新</button>
      <button className="console-button primary" disabled={loading || !!error} onClick={() => open('run')}><Play size={16} />运行评测</button>
    </ConsoleHeader>
    <div className="console-stats">
      <div className="console-stat"><TestTube /><div><strong>{loading ? '—' : cases.length}</strong><span>测试用例 · {active.length} 个已启用</span></div></div>
      <div className="console-stat"><BarChart3 /><div><strong>{loading ? '—' : runs.length}</strong><span>评测运行记录</span></div></div>
      <div className="console-stat"><CheckCircle /><div><strong>{loading || !completed ? '—' : (completed.passRate ?? 0).toFixed(1) + '%'}</strong><span>最近完成评测通过率</span></div></div>
    </div>
    <ConsoleNotice message={error} error /><ConsoleNotice message={notice} />
    <section className="console-panel" aria-label="测试用例">
      <div className="console-panel-header"><div><h2><TestTube size={19} />测试用例</h2><p>定义输入与期望输出，建立可复用的质量基线。</p></div><button className="console-button" onClick={() => open('create')}><Plus size={16} />新建用例</button></div>
      <div className="console-toolbar"><label className="console-search"><Search size={17} /><input aria-label="搜索测试用例" placeholder="搜索用例名称或输入内容" value={query} onChange={e => { setQuery(e.target.value); setCasePage(1) }} /></label>
        <select className="console-select" aria-label="用例状态" value={caseStatus} onChange={e => { setCaseStatus(e.target.value); setCasePage(1) }}><option value="ALL">全部状态</option><option value="ACTIVE">已启用</option><option value="DRAFT">草稿</option><option value="PENDING">待审核</option><option value="DISABLED">已停用</option></select>
      </div>
      {loading ? <EmptyState icon={TestTube} title="正在加载用例…" loading /> : error ? <EmptyState icon={AlertTriangle} title="用例加载失败" description="请使用页头的刷新按钮重新加载。" /> : !filtered.length ? <EmptyState icon={TestTube} title={cases.length ? '没有匹配的用例' : '从第一个测试用例开始'} description={cases.length ? '尝试其他关键词或切换状态筛选。' : '设置一个问题与期望回答，后续即可持续验证 Agent 的表现。'}>
        {cases.length ? <button className="console-button" onClick={() => { setQuery(''); setCaseStatus('ALL') }}>清除筛选</button> : <button className="console-button primary" onClick={() => open('create')}><Plus size={16} />创建测试用例</button>}
      </EmptyState> : <div className="console-list">{filtered.slice((cp - 1) * caseSize, cp * caseSize).map(c => <article className="console-card" key={c.caseId}>
        <div className="console-card-head"><div className="console-card-title"><span className="console-type-icon eval"><TestTube size={20} /></span><div><h3>{c.caseName}</h3><div className="console-tags"><StatusBadge status={c.status} /><span>{PROFILES[c.profileCode as keyof typeof PROFILES] || c.profileCode}</span><span className="console-tag">{MODES[c.validationMode as keyof typeof MODES] || c.validationMode}</span></div></div></div>
          <div className="console-actions"><button className="console-button" disabled={busy} onClick={() => changeStatus(c)}>{c.status === 'ACTIVE' ? '停用' : '启用'}</button><button className="console-icon-button" aria-label={'删除用例：' + c.caseName} title="删除用例" onClick={() => { setFormError(''); setDeleting(c) }}><Trash2 size={16} /></button></div></div>
        <p className="console-content">{c.inputPrompt}</p>
        {(c.expectedOutput || c.judgeCriteria) && <details><summary className="console-link">查看评判标准</summary>{c.expectedOutput && <p className="console-content">期望输出：{c.expectedOutput}</p>}{c.judgeCriteria && <p className="console-content">评判标准：{c.judgeCriteria}</p>}</details>}
        {c.tags?.length ? <div className="console-tags">{c.tags.map(tag => <span className="console-tag" key={tag}>{tag}</span>)}</div> : null}
      </article>)}</div>}
      {!loading && !error && filtered.length > 0 && <ConsolePager page={cp} size={caseSize} total={filtered.length} onPage={setCasePage} onSize={n => { setCaseSize(n); setCasePage(1) }} />}
    </section>
    <section className="console-panel" aria-label="评测运行记录">
      <div className="console-panel-header"><div><h2><BarChart3 size={19} />运行记录</h2><p>查看每次评测的完成情况与通过率。</p></div><span className="console-tag">{runs.length} 次运行</span></div>
      {loading ? <EmptyState icon={BarChart3} title="正在加载运行记录…" loading /> : error ? <EmptyState icon={AlertTriangle} title="运行记录加载失败" description="请使用页头的刷新按钮重新加载。" /> : !runs.length ? <EmptyState icon={BarChart3} title="还没有评测记录" description="准备好已启用的用例后，运行一次评测，结果会展示在这里。"><button className="console-button" onClick={() => open('run')}><Play size={15} />配置评测</button></EmptyState>
        : <div className="console-list">{runs.slice((rp - 1) * runSize, rp * runSize).map(run => <article className="console-card" key={run.runId}>
          <div className="console-card-head"><div><h3>{run.runName}</h3><div className="console-tags"><span>{formatDate(run.startedAt)}</span><span>{PROFILES[run.profileCode as keyof typeof PROFILES] || run.profileCode}</span><span>{run.runId}</span></div></div><StatusBadge status={run.status} label={run.status === 'PENDING' ? '待运行' : undefined} /></div>
          <div className="console-run-metrics"><div><strong>{run.totalCases ?? 0}</strong><span>总用例</span></div><div><strong>{run.passedCases ?? 0}</strong><span>通过</span></div><div><strong>{run.failedCases ?? 0}</strong><span>失败</span></div><div><strong>{run.status === 'COMPLETED' ? (run.passRate ?? 0).toFixed(1) + '%' : '—'}</strong><span>通过率</span></div></div>
          <div className="console-progress" aria-hidden="true"><span style={{ width: Math.max(0, Math.min(100, run.passRate || 0)) + '%' }} /></div>
          <div className="console-meta"><span>跳过 {run.skippedCases ?? 0} 个用例</span><span>耗时 {((run.durationMs ?? 0) / 1000).toFixed(1)} 秒</span></div>
          {run.errorMessage && <p className="console-content">{run.errorMessage}</p>}
        </article>)}</div>}
      {!loading && !error && runs.length > 0 && <ConsolePager page={rp} size={runSize} total={runs.length} onPage={setRunPage} onSize={n => { setRunSize(n); setRunPage(1) }} />}
    </section>
    {dialog === 'create' && <ConsoleDialog title="新建测试用例" description="明确的输入与评判标准，让每次回归更有参考价值。" busy={busy} onClose={() => setDialog(null)}>
      <form className="console-form" onSubmit={e => { e.preventDefault(); createCase() }}>
        <label>用例名称 *<input required maxLength={200} value={newCase.name} onChange={e => setNewCase({ ...newCase, name: e.target.value })} placeholder="例如：简历分析 · 结构完整性" /></label>
        <div className="console-form-row"><label>目标助手<select value={newCase.profileCode} onChange={e => setNewCase({ ...newCase, profileCode: e.target.value })}>{Object.entries(PROFILES).map(([key, value]) => <option key={key} value={key}>{value}</option>)}</select></label><label>校验方式<select value={newCase.validationMode} onChange={e => setNewCase({ ...newCase, validationMode: e.target.value })}>{Object.entries(MODES).map(([key, value]) => <option key={key} value={key}>{value}</option>)}</select></label></div>
        <label>输入内容 *<textarea required rows={3} value={newCase.input} onChange={e => setNewCase({ ...newCase, input: e.target.value })} placeholder="输入你希望 Agent 回答的问题" /></label>
        <label>期望输出{needsKeyword ? ' *' : '（可选）'}<textarea required={needsKeyword} rows={2} value={newCase.expectedOutput} onChange={e => setNewCase({ ...newCase, expectedOutput: e.target.value })} placeholder="填写答案应包含的关键内容" /></label>
        {needsJudge && <label>模型评判标准 *<textarea required rows={2} value={newCase.judgeCriteria} onChange={e => setNewCase({ ...newCase, judgeCriteria: e.target.value })} placeholder="例如：包含亮点、改进建议与岗位匹配分析" /></label>}
        <small>新用例保存为草稿；核对内容后可在用例卡片上启用。</small><ConsoleNotice message={formError} error />
        <footer><button type="button" className="console-button" disabled={busy} onClick={() => setDialog(null)}>取消</button><button className="console-button primary" disabled={busy || !validCase}>{busy ? '保存中…' : '保存用例'}</button></footer>
      </form>
    </ConsoleDialog>}
    {dialog === 'run' && <ConsoleDialog title="运行评测" description="选择目标助手，对该助手的全部已启用用例执行评测。" busy={busy} onClose={() => setDialog(null)}>
      <form className="console-form" onSubmit={e => { e.preventDefault(); startRun() }}>
        <label>运行名称 *<input required maxLength={200} value={runName} onChange={e => setRunName(e.target.value)} placeholder="例如：通用助手回归 · v1.2" /></label>
        <label>目标助手<select value={runProfile} onChange={e => setRunProfile(e.target.value)}>{Object.entries(PROFILES).map(([key, value]) => <option key={key} value={key}>{value}</option>)}</select></label>
        <ConsoleNotice message={runnable.length ? '将运行 ' + runnable.length + ' 个已启用用例。评测会调用模型，可能产生费用，请确认后启动。' : '该助手暂无已启用用例，暂时无法运行。草稿与已停用用例不会参与评测。'} />
        {busy && <ConsoleNotice message="正在提交评测，请勿重复点击。" />}<ConsoleNotice message={formError} error />
        <footer><button type="button" className="console-button" disabled={busy} onClick={() => setDialog(null)}>取消</button><button className="console-button primary" disabled={busy || !runName.trim() || !runnable.length}><Play size={15} />{busy ? '运行中…' : '开始评测'}</button></footer>
      </form>
    </ConsoleDialog>}
    {deleting && <ConsoleDialog title="删除测试用例" description="此操作不可撤销，请确认不再需要这个用例。" busy={busy} onClose={() => setDeleting(null)}><p className="console-content">{deleting.caseName}</p><ConsoleNotice message={formError} error /><footer><button className="console-button" disabled={busy} onClick={() => setDeleting(null)}>取消</button><button className="console-button danger" disabled={busy} onClick={deleteCase}>{busy ? '删除中…' : '确认删除'}</button></footer></ConsoleDialog>}
  </div>
}
