import { useEffect, useMemo, useRef, useState } from 'react'
import {
  Check, ChevronRight, Download, FileText, FolderOpen, Lightbulb, Link2,
  ListTree, MoreHorizontal, PencilLine, Plus, Quote, Save, Search, Share2,
  Sparkles, Star, Tags, Trash2, X,
} from 'lucide-react'
import { notesApi, type NoteEntry } from '../api/notes'

type NoticeTone = 'success' | 'info' | 'error'
type Filter = 'all' | 'recent' | 'favorite'

const preview = (content: string) => content.replace(/[#>*_`-]/g, ' ').replace(/\s+/g, ' ').trim()

function displayDate(value: string) {
  const date = new Date(value)
  const today = new Date()
  if (date.toDateString() === today.toDateString()) {
    return `今天 ${date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })}`
  }
  return date.toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric' })
}

export default function Notes() {
  const [notes, setNotes] = useState<NoteEntry[]>([])
  const [related, setRelated] = useState<NoteEntry[]>([])
  const [loading, setLoading] = useState(true)
  const [selectedNote, setSelectedNote] = useState<NoteEntry | null>(null)
  const [title, setTitle] = useState('')
  const [content, setContent] = useState('')
  const [tags, setTags] = useState<string[]>([])
  const [favorite, setFavorite] = useState(false)
  const [searchKeyword, setSearchKeyword] = useState('')
  const [filter, setFilter] = useState<Filter>('all')
  const [savedContent, setSavedContent] = useState('')
  const [saving, setSaving] = useState(false)
  const [notice, setNotice] = useState<{ text: string; tone: NoticeTone } | null>(null)
  const noticeTimer = useRef<number | undefined>(undefined)

  const showNotice = (text: string, tone: NoticeTone = 'info') => {
    window.clearTimeout(noticeTimer.current)
    setNotice({ text, tone })
    noticeTimer.current = window.setTimeout(() => setNotice(null), 2800)
  }

  const loadNotes = async (signal?: AbortSignal, query = searchKeyword, activeFilter = filter) => {
    try {
      const res = await notesApi.list(query, activeFilter === 'favorite', { signal })
      if (res.code === 0) setNotes(res.data)
    } catch (err) {
      if (err instanceof DOMException && err.name === 'AbortError') return
      showNotice('笔记列表加载失败，请稍后重试', 'error')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    const controller = new AbortController()
    void loadNotes(controller.signal, '', 'all')
    return () => controller.abort()
  }, [])

  const applyNote = (note: NoteEntry) => {
    setSelectedNote(note)
    setTitle(note.title)
    setContent(note.content)
    setSavedContent(note.content)
    setTags(note.tags)
    setFavorite(note.favorite)
  }

  const openNote = async (note: NoteEntry) => {
    try {
      const detail = await notesApi.read(note.id)
      if (detail.code === 0) applyNote(detail.data)
      void notesApi.related(note.id).then(relatedResult => {
        if (relatedResult.code === 0) setRelated(relatedResult.data)
      }).catch(() => setRelated([]))
    } catch {
      showNotice('笔记内容加载失败，请稍后重试', 'error')
    }
  }

  const createNote = () => {
    setSelectedNote(null)
    setTitle('未命名笔记')
    setContent('## 从这里开始记录\n\n写下你的想法、结论或待办事项。')
    setSavedContent('')
    setTags([])
    setFavorite(false)
    setRelated([])
  }

  const saveNote = async () => {
    const normalizedTitle = title.trim()
    if (!normalizedTitle || !content.trim()) return showNotice('请先填写笔记标题和内容', 'error')
    if (normalizedTitle.length > 120) return showNotice('笔记标题不能超过 120 个字符', 'error')
    setSaving(true)
    try {
      const res = await notesApi.save({ id: selectedNote?.id, title: normalizedTitle, content, tags, favorite })
      if (res.code !== 0) throw new Error(res.message)
      applyNote(res.data)
      setNotes(previous => [res.data, ...previous.filter(item => item.id !== res.data.id)])
      showNotice('笔记已保存', 'success')
    } catch {
      showNotice('保存失败，请检查是否存在同名笔记', 'error')
    } finally {
      setSaving(false)
    }
  }

  const deleteNote = async () => {
    if (!selectedNote || !window.confirm(`确定删除“${selectedNote.title}”吗？`)) return
    try {
      await notesApi.delete(selectedNote.id)
      setNotes(previous => previous.filter(item => item.id !== selectedNote.id))
      createNote()
      showNotice('笔记已删除', 'success')
    } catch { showNotice('删除失败，请稍后重试', 'error') }
  }

  const toggleFavorite = async () => {
    if (!selectedNote) { setFavorite(value => !value); return }
    try {
      const res = await notesApi.favorite(selectedNote.id, !favorite)
      if (res.code === 0) {
        applyNote(res.data)
        setNotes(previous => previous.map(item => item.id === res.data.id ? res.data : item))
      }
    } catch { showNotice('收藏状态更新失败', 'error') }
  }

  const addTag = () => {
    const value = window.prompt('输入新标签（最多 32 字符）')?.trim()
    if (!value || tags.includes(value)) return
    if (tags.length >= 12) return showNotice('每篇笔记最多 12 个标签', 'error')
    setTags(previous => [...previous, value.slice(0, 32)])
  }

  const exportMarkdown = async () => {
    if (!selectedNote) return showNotice('请先保存笔记再导出', 'error')
    try {
      const res = await notesApi.exportMarkdown(selectedNote.id)
      if (res.code !== 0) throw new Error(res.message)
      const blob = new Blob([res.data.content], { type: res.data.mediaType })
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = res.data.filename
      anchor.click()
      URL.revokeObjectURL(url)
      showNotice('Markdown 已导出', 'success')
    } catch { showNotice('导出失败', 'error') }
  }

  const changeFilter = (next: Filter) => {
    setFilter(next)
    setLoading(true)
    void loadNotes(undefined, searchKeyword, next)
  }

  const tagCounts = useMemo(() => notes.flatMap(note => note.tags).reduce<Record<string, number>>((result, tag) => {
    result[tag] = (result[tag] ?? 0) + 1
    return result
  }, {}), [notes])
  const isDirty = Boolean(title && (content !== savedContent || title !== selectedNote?.title || tags.join() !== (selectedNote?.tags ?? []).join() || favorite !== (selectedNote?.favorite ?? false)))
  const wordCount = content.trim().length

  return <section className="notes-workspace">
    <aside className="notes-library">
      <div className="notes-library-search"><Search size={17} /><input value={searchKeyword} onChange={event => setSearchKeyword(event.target.value)} onKeyDown={event => event.key === 'Enter' && void loadNotes()} placeholder="搜索笔记…" /><kbd>⌘K</kbd></div>
      <button className="notes-new-button" onClick={createNote}><Plus size={18} /> 新建笔记</button>
      <div className="notes-filters"><button className={filter === 'all' ? 'active' : ''} onClick={() => changeFilter('all')}>全部笔记</button><button className={filter === 'recent' ? 'active' : ''} onClick={() => changeFilter('recent')}>最近编辑</button><button className={filter === 'favorite' ? 'active' : ''} onClick={() => changeFilter('favorite')}><Star size={13} /> 收藏</button></div>
      <div className="notes-list-scroll">{loading && <p className="notes-empty">正在载入笔记…</p>}{!loading && notes.length === 0 && <p className="notes-empty">暂无匹配笔记</p>}{!loading && notes.map(note => <button className={`notes-list-item ${selectedNote?.id === note.id ? 'active' : ''}`} key={note.id} onClick={() => void openNote(note)}><FileText size={17} /><span><strong>{note.title}</strong><small>{preview(note.content).slice(0, 28) || '暂无内容'}</small></span><time>{displayDate(note.updatedAt)}</time></button>)}</div>
      <div className="notes-pagination"><span>共 {notes.length} 篇</span><b>1</b></div>
    </aside>

    <main className="notes-editor-shell">
      <header className="notes-toolbar"><div className="notes-breadcrumb"><span>笔记</span><ChevronRight size={15} /><strong>{title || '新建笔记'}</strong><button onClick={() => void toggleFavorite()} title={favorite ? '取消收藏' : '收藏'}><Star size={18} fill={favorite ? 'currentColor' : 'none'} /></button></div><div className="notes-actions"><span className={`notes-save-state ${isDirty ? 'dirty' : ''}`}>{isDirty ? <PencilLine size={14} /> : <Check size={14} />}{isDirty ? '有未保存修改' : selectedNote ? `已保存 ${displayDate(selectedNote.updatedAt)}` : '尚未保存'}</span><button className="primary" onClick={() => void saveNote()} disabled={saving}><Save size={16} />{saving ? '保存中' : '保存'}</button><button onClick={() => void exportMarkdown()}><Download size={16} /> 导出</button><button title="分享涉及公开权限，本阶段未开启" onClick={() => showNotice('分享功能需先完善公开权限与撤回机制')}><Share2 size={16} /> 分享</button>{selectedNote && <button className="danger" title="删除当前笔记" onClick={() => void deleteNote()}><Trash2 size={16} /></button>}<button title="更多功能正在建设"><MoreHorizontal size={18} /> 更多</button></div></header>
      <div className="notes-editor-scroll"><article className="notes-document"><input className="notes-title-input" value={title} onChange={event => setTitle(event.target.value)} placeholder="输入笔记标题" /><div className="notes-document-meta"><span>更新时间：{selectedNote ? displayDate(selectedNote.updatedAt) : '未保存'}</span><span>字数：{wordCount.toLocaleString()}</span><span>阅读时间：{Math.max(1, Math.ceil(wordCount / 300))} 分钟</span><div>{tags.map(tag => <button key={tag} onClick={() => setTags(previous => previous.filter(item => item !== tag))}>#{tag} ×</button>)}<button onClick={addTag}><Plus size={13} /></button></div></div><div className="notes-editor-area"><div className="notes-editor-tip"><Sparkles size={16} /> 支持 Markdown 书写，内容仅对当前账户可见。</div><textarea value={content} onChange={event => setContent(event.target.value)} placeholder="在这里记录你的想法…" spellCheck={false} /></div><footer className="notes-document-footer"><span>Markdown</span><span>{wordCount.toLocaleString()} 字</span><span>{Math.max(1, Math.ceil(wordCount / 300))} 分钟阅读</span><span>{isDirty ? '修改尚未保存' : '数据已同步'} <i /></span></footer></article></div>
    </main>

    <aside className="notes-inspector">
      <section className="notes-inspector-card"><h2><Sparkles size={18} /> AI 写作助手</h2><div className="notes-ai-actions">{[['总结内容', '提炼全文重点', ListTree], ['提炼要点', '生成核心摘要', Quote], ['生成大纲', '创建内容结构', FolderOpen], ['改写语气', '调整表达风格', PencilLine], ['补充示例', '丰富内容案例', Lightbulb], ['关联知识库', '查找相关内容', Link2]].map(([name, text, Icon]) => <button key={name as string} title="需复用聊天 Runtime 实现" onClick={() => showNotice(`${name}：将在后续复用安全聊天 Runtime 接入`)}><Icon size={17} /><span><strong>{name as string}</strong><small>{text as string}</small></span></button>)}</div></section>
      <section className="notes-inspector-card"><header><h2><Link2 size={17} /> 相关笔记</h2><span>按标签关联</span></header><div className="notes-related">{related.map(note => <button key={note.id} onClick={() => void openNote(note)}><FileText size={14} /> {note.title}{note.favorite && <Star size={13} fill="currentColor" />}</button>)}{!related.length && <p>为笔记添加共同标签后，相关内容会出现在这里。</p>}</div></section>
      <section className="notes-inspector-card"><header><h2><Tags size={17} /> 标签</h2><button onClick={addTag}>管理</button></header><div className="notes-tags">{Object.entries(tagCounts).slice(0, 8).map(([tag, count]) => <span key={tag}>{tag} <b>{count}</b></span>)}</div></section>
      <section className="notes-inspiration"><div><Lightbulb size={17} /> 写作提示</div><p>为笔记添加精确标签，可以让相关笔记的召回更准确。</p></section>
    </aside>
    {notice && <div className={`notes-notice ${notice.tone}`}><span>{notice.text}</span><button onClick={() => setNotice(null)}><X size={15} /></button></div>}
  </section>
}
