import { useState, useEffect } from 'react'
import { treeholeApi, type TreeholeEntry } from '../api/treehole'
import { Plus, Trash2, Heart } from 'lucide-react'

export default function Treehole() {
  const [entries, setEntries] = useState<TreeholeEntry[]>([])
  const [loading, setLoading] = useState(true)
  const [showCreate, setShowCreate] = useState(false)
  const [title, setTitle] = useState('')
  const [content, setContent] = useState('')
  const [creating, setCreating] = useState(false)

  useEffect(() => {
    loadEntries()
  }, [])

  const loadEntries = async () => {
    try {
      const res = await treeholeApi.list()
      if (res.code === 0) {
        setEntries(res.data)
      }
    } catch (err) {
      console.error('Failed to load treeholes', err)
    } finally {
      setLoading(false)
    }
  }

  const handleCreate = async () => {
    if (!title.trim() || !content.trim()) return

    setCreating(true)
    try {
      const res = await treeholeApi.create({ title: title.trim(), content: content.trim() })
      if (res.code === 0) {
        setEntries(prev => [res.data, ...prev])
        setTitle('')
        setContent('')
        setShowCreate(false)
      }
    } catch (err) {
      console.error('Failed to create treehole', err)
    } finally {
      setCreating(false)
    }
  }

  const handleDelete = async (id: number) => {
    if (!confirm('确定要删除这条树洞吗？')) return

    try {
      await treeholeApi.delete(id)
      setEntries(prev => prev.filter(e => e.id !== id))
    } catch (err) {
      console.error('Failed to delete treehole', err)
    }
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-slate-100">树洞</h1>
          <p className="text-slate-400 mt-2">记录你的心情，AI 会给你温暖的回应</p>
        </div>
        <button
          onClick={() => setShowCreate(!showCreate)}
          className="flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-semibold text-slate-900 btn-gradient"
        >
          <Plus size={18} />
          写树洞
        </button>
      </div>

      {/* Create Form */}
      {showCreate && (
        <div className="glass rounded-xl p-6 animate-fade-in">
          <div className="space-y-4">
            <div>
              <label className="block text-sm text-slate-300 mb-2">标题</label>
              <input
                type="text"
                value={title}
                onChange={e => setTitle(e.target.value)}
                placeholder="给这条树洞起个标题"
                maxLength={120}
                className="w-full px-4 py-2.5 rounded-lg text-sm"
              />
            </div>
            <div>
              <label className="block text-sm text-slate-300 mb-2">内容</label>
              <textarea
                value={content}
                onChange={e => setContent(e.target.value)}
                placeholder="写下你的心情..."
                rows={6}
                maxLength={5000}
                className="w-full px-4 py-2.5 rounded-lg text-sm resize-none"
              />
              <div className="text-xs text-slate-500 mt-1 text-right">
                {content.length} / 5000
              </div>
            </div>
            <div className="flex gap-3">
              <button
                onClick={handleCreate}
                disabled={creating || !title.trim() || !content.trim()}
                className="px-6 py-2 rounded-lg text-sm font-semibold text-slate-900 btn-gradient disabled:opacity-50"
              >
                {creating ? '发布中...' : '发布'}
              </button>
              <button
                onClick={() => setShowCreate(false)}
                className="px-6 py-2 rounded-lg text-sm text-slate-400 hover:text-slate-200 hover:bg-white/5"
              >
                取消
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Entries */}
      {loading ? (
        <div className="text-center py-12 text-slate-400">加载中...</div>
      ) : entries.length === 0 ? (
        <div className="text-center py-12">
          <div className="w-20 h-20 rounded-full bg-gradient-to-br from-amber-500/20 to-orange-500/20 flex items-center justify-center mx-auto mb-4">
            <Heart size={32} className="text-amber-400" />
          </div>
          <h2 className="text-xl font-semibold text-slate-200 mb-2">还没有树洞</h2>
          <p className="text-sm text-slate-400">写下你的第一条树洞吧</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {entries.map(entry => (
            <div key={entry.id} className="glass rounded-xl p-6 hover:border-amber-500/30 transition-all">
              <div className="flex items-start justify-between mb-3">
                <h3 className="text-lg font-semibold text-slate-100">{entry.title}</h3>
                <button
                  onClick={() => handleDelete(entry.id)}
                  className="text-slate-500 hover:text-rose-400 transition-colors"
                >
                  <Trash2 size={16} />
                </button>
              </div>

              <p className="text-sm text-slate-300 mb-4 whitespace-pre-wrap">{entry.content}</p>

              {entry.emotionTag && (
                <div className="inline-block px-3 py-1 rounded-full bg-amber-500/20 border border-amber-500/30 text-xs text-amber-300 mb-3">
                  {entry.emotionTag}
                </div>
              )}

              {entry.hermesSummary && (
                <div className="p-4 rounded-lg bg-white/5 border border-white/10 mt-3">
                  <div className="text-xs text-slate-400 mb-2">AI 回应</div>
                  <p className="text-sm text-slate-200">{entry.hermesSummary}</p>
                </div>
              )}

              {entry.hermesReply && (
                <div className="p-4 rounded-lg bg-gradient-to-br from-rose-500/10 to-amber-500/10 border border-rose-500/20 mt-3">
                  <div className="text-xs text-rose-300 mb-2">💝 温暖建议</div>
                  <p className="text-sm text-slate-200">{entry.hermesReply}</p>
                </div>
              )}

              <div className="text-xs text-slate-500 mt-4">
                {new Date(entry.createdAt).toLocaleString('zh-CN')}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
