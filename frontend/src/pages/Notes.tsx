import { useState, useEffect } from 'react'
import { notesApi } from '../api/notes'
import { Plus, Search, Trash2, FileText } from 'lucide-react'

export default function Notes() {
  const [notes, setNotes] = useState<string[]>([])
  const [loading, setLoading] = useState(true)
  const [selectedNote, setSelectedNote] = useState<string | null>(null)
  const [noteContent, setNoteContent] = useState('')
  const [searchKeyword, setSearchKeyword] = useState('')
  const [showCreate, setShowCreate] = useState(false)
  const [newTitle, setNewTitle] = useState('')
  const [newContent, setNewContent] = useState('')

  useEffect(() => {
    loadNotes()
  }, [])

  const loadNotes = async () => {
    try {
      const res = await notesApi.list()
      if (res.code === 0) {
        const list = res.data.split('\n').filter(Boolean)
        setNotes(list)
      }
    } catch (err) {
      console.error('Failed to load notes', err)
    } finally {
      setLoading(false)
    }
  }

  const loadNoteContent = async (title: string) => {
    setSelectedNote(title)
    try {
      const res = await notesApi.read(title)
      if (res.code === 0) {
        setNoteContent(res.data)
      }
    } catch (err) {
      console.error('Failed to load note', err)
    }
  }

  const handleCreate = async () => {
    if (!newTitle.trim() || !newContent.trim()) return

    try {
      const res = await notesApi.create(newTitle.trim(), newContent.trim())
      if (res.code === 0) {
        setNotes(prev => [...prev, newTitle.trim()])
        setNewTitle('')
        setNewContent('')
        setShowCreate(false)
      }
    } catch (err) {
      console.error('Failed to create note', err)
    }
  }

  const handleDelete = async (title: string) => {
    if (!confirm(`确定要删除笔记「${title}」吗？`)) return

    try {
      await notesApi.delete(title)
      setNotes(prev => prev.filter(n => n !== title))
      if (selectedNote === title) {
        setSelectedNote(null)
        setNoteContent('')
      }
    } catch (err) {
      console.error('Failed to delete note', err)
    }
  }

  const handleSearch = async () => {
    if (!searchKeyword.trim()) {
      loadNotes()
      return
    }

    try {
      const res = await notesApi.search(searchKeyword.trim())
      if (res.code === 0) {
        const list = res.data.split('\n').filter(Boolean)
        setNotes(list)
      }
    } catch (err) {
      console.error('Failed to search notes', err)
    }
  }

  return (
    <div className="h-[calc(100vh-4rem)] flex gap-6">
      {/* Sidebar */}
      <div className="w-80 glass rounded-xl flex flex-col">
        <div className="p-4 border-b border-white/10 space-y-3">
          <div className="flex gap-2">
            <input
              type="text"
              value={searchKeyword}
              onChange={e => setSearchKeyword(e.target.value)}
              onKeyPress={e => e.key === 'Enter' && handleSearch()}
              placeholder="搜索笔记..."
              className="flex-1 px-3 py-2 rounded-lg text-sm"
            />
            <button
              onClick={handleSearch}
              className="px-3 py-2 rounded-lg bg-white/5 hover:bg-white/10 transition-colors"
            >
              <Search size={16} className="text-slate-400" />
            </button>
          </div>
          <button
            onClick={() => setShowCreate(!showCreate)}
            className="w-full flex items-center justify-center gap-2 px-4 py-2 rounded-lg text-sm font-semibold text-slate-900 btn-gradient"
          >
            <Plus size={16} />
            新建笔记
          </button>
        </div>

        {showCreate && (
          <div className="p-4 border-b border-white/10 space-y-3">
            <input
              type="text"
              value={newTitle}
              onChange={e => setNewTitle(e.target.value)}
              placeholder="笔记标题"
              className="w-full px-3 py-2 rounded-lg text-sm"
            />
            <textarea
              value={newContent}
              onChange={e => setNewContent(e.target.value)}
              placeholder="笔记内容..."
              rows={4}
              className="w-full px-3 py-2 rounded-lg text-sm resize-none"
            />
            <div className="flex gap-2">
              <button
                onClick={handleCreate}
                className="flex-1 px-4 py-2 rounded-lg text-sm font-semibold text-slate-900 btn-gradient"
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

        <div className="flex-1 overflow-y-auto p-2 space-y-1">
          {loading ? (
            <div className="text-center py-8 text-slate-400 text-sm">加载中...</div>
          ) : notes.length === 0 ? (
            <div className="text-center py-8 text-slate-400 text-sm">暂无笔记</div>
          ) : (
            notes.map(note => (
              <div
                key={note}
                onClick={() => loadNoteContent(note)}
                className={`flex items-center justify-between p-3 rounded-lg cursor-pointer transition-colors ${
                  selectedNote === note
                    ? 'bg-gradient-to-r from-sky-500/20 to-blue-500/20 border border-sky-500/30'
                    : 'hover:bg-white/5'
                }`}
              >
                <div className="flex items-center gap-2 flex-1 min-w-0">
                  <FileText size={16} className="text-sky-400 flex-shrink-0" />
                  <span className="text-sm text-slate-200 truncate">{note}</span>
                </div>
                <button
                  onClick={e => {
                    e.stopPropagation()
                    handleDelete(note)
                  }}
                  className="text-slate-500 hover:text-rose-400 transition-colors"
                >
                  <Trash2 size={14} />
                </button>
              </div>
            ))
          )}
        </div>
      </div>

      {/* Content */}
      <div className="flex-1 glass rounded-xl p-6 overflow-y-auto">
        {selectedNote ? (
          <div>
            <h2 className="text-2xl font-bold text-slate-100 mb-4">{selectedNote}</h2>
            <div className="prose prose-invert max-w-none">
              <pre className="whitespace-pre-wrap text-sm text-slate-300 font-sans">
                {noteContent}
              </pre>
            </div>
          </div>
        ) : (
          <div className="flex items-center justify-center h-full">
            <div className="text-center">
              <div className="w-20 h-20 rounded-full bg-gradient-to-br from-sky-500/20 to-blue-500/20 flex items-center justify-center mx-auto mb-4">
                <FileText size={32} className="text-sky-400" />
              </div>
              <h2 className="text-xl font-semibold text-slate-200 mb-2">选择一条笔记</h2>
              <p className="text-sm text-slate-400">从左侧列表选择笔记查看内容</p>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
