import { useState, useEffect } from 'react'
import { evalApi, type EvalCase, type EvalRun } from '../api/eval'
import { Plus, Play, Trash2, TestTube, CheckCircle, XCircle } from 'lucide-react'
import Pagination from '../components/Pagination'

export default function Eval() {
  const [cases, setCases] = useState<EvalCase[]>([])
  const [runs, setRuns] = useState<EvalRun[]>([])
  const [loading, setLoading] = useState(true)
  const [showCreate, setShowCreate] = useState(false)
  const [newCase, setNewCase] = useState({ name: '', input: '', expectedOutput: '', tags: '' })
  const [casesPage, setCasesPage] = useState(1)
  const [casesPageSize, setCasesPageSize] = useState(20)
  const [runsPage, setRunsPage] = useState(1)
  const [runsPageSize, setRunsPageSize] = useState(20)
  const [casesTotalPages, setCasesTotalPages] = useState(1)
  const [runsTotalPages, setRunsTotalPages] = useState(1)

  useEffect(() => {
    const controller = new AbortController()
    loadData(controller.signal)
    return () => controller.abort()
  }, [casesPage, casesPageSize, runsPage, runsPageSize])

  const loadData = async (signal?: AbortSignal) => {
    try {
      const [casesRes, runsRes] = await Promise.all([
        evalApi.listCases({ signal }, casesPage, casesPageSize),
        evalApi.listRuns({ signal }, runsPage, runsPageSize),
      ])
      if (casesRes.code === 0) {
        setCases(casesRes.data)
        setCasesTotalPages(Math.max(1, Math.ceil(casesRes.data.length / casesPageSize) || 1))
      }
      if (runsRes.code === 0) {
        setRuns(runsRes.data)
        setRunsTotalPages(Math.max(1, Math.ceil(runsRes.data.length / runsPageSize) || 1))
      }
    } catch (err) {
      if (err instanceof DOMException && err.name === 'AbortError') return
      console.error('Failed to load eval data', err)
    } finally {
      setLoading(false)
    }
  }

  const handleCreateCase = async () => {
    if (!newCase.name.trim() || !newCase.input.trim()) return

    try {
      const res = await evalApi.createCase({
        name: newCase.name.trim(),
        input: newCase.input.trim(),
        expectedOutput: newCase.expectedOutput.trim() || undefined,
        tags: newCase.tags.split(',').map(t => t.trim()).filter(Boolean),
      })
      if (res.code === 0) {
        setCases(prev => [...prev, res.data])
        setNewCase({ name: '', input: '', expectedOutput: '', tags: '' })
        setShowCreate(false)
      }
    } catch (err) {
      console.error('Failed to create case', err)
    }
  }

  const handleDeleteCase = async (id: number) => {
    if (!confirm('确定要删除这个测试用例吗？')) return

    try {
      await evalApi.deleteCase(id)
      setCases(prev => prev.filter(c => c.id !== id))
    } catch (err) {
      console.error('Failed to delete case', err)
    }
  }

  const handleStartRun = async () => {
    const name = prompt('请输入评测运行名称：')
    if (!name) return

    try {
      const res = await evalApi.startRun(name)
      if (res.code === 0) {
        setRuns(prev => [res.data, ...prev])
        alert('评测运行已启动')
      }
    } catch (err) {
      console.error('Failed to start run', err)
      alert('启动失败')
    }
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-slate-100">评测管理</h1>
          <p className="text-slate-400 mt-2">管理测试用例和运行评测</p>
        </div>
        <button
          onClick={handleStartRun}
          className="flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-semibold text-slate-900 btn-gradient"
        >
          <Play size={18} />
          运行评测
        </button>
      </div>

      {/* Test Cases */}
      <div className="glass rounded-xl p-6">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-lg font-semibold text-slate-100">测试用例</h2>
          <button
            onClick={() => setShowCreate(!showCreate)}
            className="flex items-center gap-2 px-3 py-1.5 rounded-lg text-sm text-slate-300 hover:bg-white/5"
          >
            <Plus size={16} />
            新建用例
          </button>
        </div>

        {showCreate && (
          <div className="p-4 rounded-lg bg-white/5 border border-white/10 mb-4 space-y-3">
            <input
              type="text"
              value={newCase.name}
              onChange={e => setNewCase({ ...newCase, name: e.target.value })}
              placeholder="用例名称"
              className="w-full px-3 py-2 rounded-lg text-sm"
            />
            <textarea
              value={newCase.input}
              onChange={e => setNewCase({ ...newCase, input: e.target.value })}
              placeholder="输入内容"
              rows={3}
              className="w-full px-3 py-2 rounded-lg text-sm resize-none"
            />
            <textarea
              value={newCase.expectedOutput}
              onChange={e => setNewCase({ ...newCase, expectedOutput: e.target.value })}
              placeholder="期望输出（可选）"
              rows={2}
              className="w-full px-3 py-2 rounded-lg text-sm resize-none"
            />
            <input
              type="text"
              value={newCase.tags}
              onChange={e => setNewCase({ ...newCase, tags: e.target.value })}
              placeholder="标签（逗号分隔）"
              className="w-full px-3 py-2 rounded-lg text-sm"
            />
            <div className="flex gap-2">
              <button
                onClick={handleCreateCase}
                className="px-4 py-2 rounded-lg text-sm font-semibold text-slate-900 btn-gradient"
              >
                创建
              </button>
              <button
                onClick={() => setShowCreate(false)}
                className="px-4 py-2 rounded-lg text-sm text-slate-400 hover:bg-white/5"
              >
                取消
              </button>
            </div>
          </div>
        )}

        {loading ? (
          <div className="text-center py-8 text-slate-400">加载中...</div>
        ) : cases.length === 0 ? (
          <div className="text-center py-8">
            <div className="w-16 h-16 rounded-full bg-gradient-to-br from-emerald-500/20 to-teal-500/20 flex items-center justify-center mx-auto mb-3">
              <TestTube size={28} className="text-emerald-400" />
            </div>
            <p className="text-sm text-slate-400">暂无测试用例</p>
          </div>
        ) : (
          <div className="space-y-2">
            {cases.map(c => (
              <div key={c.id} className="p-4 rounded-lg bg-white/5 hover:bg-white/10 transition-colors">
                <div className="flex items-start justify-between mb-2">
                  <div className="flex-1">
                    <h3 className="text-sm font-semibold text-slate-200">{c.name}</h3>
                    <p className="text-xs text-slate-400 mt-1">{c.input}</p>
                  </div>
                  <button
                    onClick={() => handleDeleteCase(c.id)}
                    className="text-slate-500 hover:text-rose-400 transition-colors"
                  >
                    <Trash2 size={16} />
                  </button>
                </div>
                {c.tags && c.tags.length > 0 && (
                  <div className="flex gap-2 mt-2">
                    {c.tags.map(tag => (
                      <span key={tag} className="px-2 py-0.5 rounded-full bg-emerald-500/20 border border-emerald-500/30 text-xs text-emerald-300">
                        {tag}
                      </span>
                    ))}
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
        <Pagination
          currentPage={casesPage}
          totalPages={casesTotalPages}
          onPageChange={setCasesPage}
          pageSize={casesPageSize}
          onPageSizeChange={(size) => { setCasesPageSize(size); setCasesPage(1) }}
        />
      </div>

      {/* Runs */}
      <div className="glass rounded-xl p-6">
        <h2 className="text-lg font-semibold text-slate-100 mb-4">评测运行</h2>
        {runs.length === 0 ? (
          <div className="text-center py-8 text-slate-400 text-sm">暂无运行记录</div>
        ) : (
          <div className="space-y-2">
            {runs.map(run => (
              <div key={run.id} className="p-4 rounded-lg bg-white/5">
                <div className="flex items-center justify-between mb-2">
                  <h3 className="text-sm font-semibold text-slate-200">{run.name}</h3>
                  <span className={`px-3 py-1 rounded-full text-xs ${
                    run.status === 'passed'
                      ? 'bg-emerald-500/20 text-emerald-300'
                      : run.status === 'failed'
                        ? 'bg-rose-500/20 text-rose-300'
                        : 'bg-amber-500/20 text-amber-300'
                  }`}>
                    {run.status}
                  </span>
                </div>
                <div className="grid grid-cols-4 gap-4 text-xs text-slate-400">
                  <div>总计: {run.totalCases}</div>
                  <div className="flex items-center gap-1">
                    <CheckCircle size={12} className="text-emerald-400" />
                    通过: {run.passedCases}
                  </div>
                  <div className="flex items-center gap-1">
                    <XCircle size={12} className="text-rose-400" />
                    失败: {run.failedCases}
                  </div>
                  <div>通过率: {(run.passRate * 100).toFixed(1)}%</div>
                </div>
              </div>
            ))}
          </div>
        )}
        <Pagination
          currentPage={runsPage}
          totalPages={runsTotalPages}
          onPageChange={setRunsPage}
          pageSize={runsPageSize}
          onPageSizeChange={(size) => { setRunsPageSize(size); setRunsPage(1) }}
        />
      </div>
    </div>
  )
}
