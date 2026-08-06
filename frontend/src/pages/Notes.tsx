import { useEffect, useMemo, useRef, useState } from 'react'
import {
  Check, ChevronRight, Download, FileText, FolderOpen, Lightbulb, Link2,
  ListTree, MoreHorizontal, PencilLine, Plus, Quote, Save, Search, Share2,
  Sparkles, Star, Tags, Trash2, X,
} from 'lucide-react'
import { notesApi } from '../api/notes'

type NoticeTone = 'success' | 'info' | 'error'

const cleanTitles = (raw: string) => raw
  .split('\n')
  .map(line => line.trim().replace(/^-\s*/, ''))
  .filter(line => line && line !== 'ok:' && !line.startsWith('failed:') && !line.startsWith('ok: no'))

const preview = (content: string) => content.replace(/[#>*_`\-]/g, ' ').replace(/\s+/g, ' ').trim()

const shortDate = (index: number) => index === 0 ? '今天 14:28' : index < 4 ? `今天 ${10 - index}:3${index}` : index < 8 ? `昨天 ${18 + index}:${index}0` : '5 月 1' + index + '日'

export default function Notes() {
  const [notes, setNotes] = useState<string[]>([])
  const [loading, setLoading] = useState(true)
  const [selectedNote, setSelectedNote] = useState<string | null>(null)
  const [title, setTitle] = useState('')
  const [content, setContent] = useState('')
  const [searchKeyword, setSearchKeyword] = useState('')
  const [savedContent, setSavedContent] = useState('')
  const [saving, setSaving] = useState(false)
  const [notice, setNotice] = useState<{ text: string; tone: NoticeTone } | null>(null)
  const noticeTimer = useRef<number | undefined>(undefined)

  const showNotice = (text: string, tone: NoticeTone = 'info') => {
    window.clearTimeout(noticeTimer.current)
    setNotice({ text, tone })
    noticeTimer.current = window.setTimeout(() => setNotice(null), 2800)
  }

  const loadNotes = async (signal?: AbortSignal) => {
    try {
      const res = await notesApi.list({ signal })
      if (res.code === 0) setNotes(cleanTitles(res.data))
    } catch (err) {
      if (err instanceof DOMException && err.name === 'AbortError') return
      showNotice('笔记列表加载失败，请稍后重试', 'error')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    const controller = new AbortController()
    loadNotes(controller.signal)
    return () => controller.abort()
  }, [])

  const openNote = async (noteTitle: string) => {
    setSelectedNote(noteTitle)
    setTitle(noteTitle)
    setContent('正在加载笔记…')
    try {
      const res = await notesApi.read(noteTitle)
      if (res.code === 0) {
        setContent(res.data)
        setSavedContent(res.data)
      }
    } catch {
      setContent('')
      showNotice('笔记内容加载失败，请稍后重试', 'error')
    }
  }

  const createNote = () => {
    setSelectedNote(null)
    setTitle('未命名笔记')
    setContent('## 从这里开始记录\n\n写下你的想法、结论或待办事项。')
    setSavedContent('')
  }

  const saveNote = async () => {
    const normalizedTitle = title.trim()
    if (!normalizedTitle || !content.trim()) {
      showNotice('请先填写笔记标题和内容', 'error')
      return
    }
    if (normalizedTitle.length > 64) {
      showNotice('笔记标题不能超过 64 个字符', 'error')
      return
    }
    setSaving(true)
    try {
      const res = await notesApi.create(normalizedTitle, content)
      if (res.code !== 0) throw new Error(res.message)
      setTitle(normalizedTitle)
      setSelectedNote(normalizedTitle)
      setSavedContent(content)
      setNotes(prev => [normalizedTitle, ...prev.filter(item => item !== normalizedTitle && item !== selectedNote)])
      showNotice('笔记已保存', 'success')
    } catch {
      showNotice('保存失败，请检查标题是否含有非法字符', 'error')
    } finally {
      setSaving(false)
    }
  }

  const deleteNote = async () => {
    if (!selectedNote || !window.confirm(`确定删除“${selectedNote}”吗？`)) return
    try {
      await notesApi.delete(selectedNote)
      setNotes(prev => prev.filter(item => item !== selectedNote))
      createNote()
      showNotice('笔记已删除', 'success')
    } catch {
      showNotice('删除失败，请稍后重试', 'error')
    }
  }

  const searchNotes = async () => {
    const keyword = searchKeyword.trim()
    if (!keyword) return loadNotes()
    try {
      const res = await notesApi.search(keyword)
      if (res.code === 0) setNotes(cleanTitles(res.data).map(item => item.split('  ::')[0].trim()))
    } catch {
      showNotice('搜索失败，请稍后重试', 'error')
    }
  }

  const filteredNotes = useMemo(() => notes.filter(item => item.toLowerCase().includes(searchKeyword.trim().toLowerCase())), [notes, searchKeyword])
  const isDirty = Boolean(title && content !== savedContent)
  const wordCount = content.trim() ? content.trim().length : 0
  const currentTags = title ? ['产品设计', '灵感', 'UX'] : []

  return (
    <section className="notes-workspace">
      <aside className="notes-library">
        <div className="notes-library-search">
          <Search size={17} />
          <input value={searchKeyword} onChange={event => setSearchKeyword(event.target.value)} onKeyDown={event => event.key === 'Enter' && searchNotes()} placeholder="搜索笔记…" />
          <kbd>⌘K</kbd>
        </div>
        <button className="notes-new-button" onClick={createNote}><Plus size={18} /> 新建笔记</button>
        <div className="notes-filters"><button className="active">全部笔记</button><button>最近编辑</button><button><Star size={13} /> 收藏</button><button><Tags size={13} /> 标签</button></div>

        <div className="notes-list-scroll">
          {loading && <p className="notes-empty">正在载入笔记…</p>}
          {!loading && filteredNotes.length === 0 && <p className="notes-empty">暂无匹配笔记</p>}
          {!loading && filteredNotes.map((note, index) => (
            <button className={`notes-list-item ${selectedNote === note ? 'active' : ''}`} key={note} onClick={() => openNote(note)}>
              <FileText size={17} />
              <span><strong>{note}</strong><small>{selectedNote === note ? preview(content).slice(0, 28) || '暂无内容' : '点击查看和编辑笔记'}</small></span>
              <time>{shortDate(index)}</time>
            </button>
          ))}
        </div>
        <div className="notes-pagination"><span>每页 20 条</span><b>1</b><button>2</button><button>3</button><ChevronRight size={15} /></div>
      </aside>

      <main className="notes-editor-shell">
        <header className="notes-toolbar">
          <div className="notes-breadcrumb"><span>笔记</span><ChevronRight size={15} /><strong>{title || '新建笔记'}</strong><Star size={18} /></div>
          <div className="notes-actions">
            <span className={`notes-save-state ${isDirty ? 'dirty' : ''}`}>{isDirty ? <PencilLine size={14} /> : <Check size={14} />}{isDirty ? '有未保存修改' : '已保存 14:28'}</span>
            <button className="primary" onClick={saveNote} disabled={saving}><Save size={16} />{saving ? '保存中' : '保存'}</button>
            <button title="TODO：后端导出接口待实现" onClick={() => showNotice('导出功能待后端接口实现')}><Download size={16} /> 导出</button>
            <button title="TODO：后端分享与协作接口待实现" onClick={() => showNotice('分享功能待后端接口实现')}><Share2 size={16} /> 分享</button>
            {selectedNote && <button className="danger" title="删除当前笔记" onClick={deleteNote}><Trash2 size={16} /></button>}
            <button title="TODO：更多笔记操作待实现" onClick={() => showNotice('更多笔记操作待后端接口实现')}><MoreHorizontal size={18} /> 更多</button>
          </div>
        </header>

        <div className="notes-editor-scroll">
          <article className="notes-document">
            <input className="notes-title-input" value={title} onChange={event => setTitle(event.target.value)} placeholder="输入笔记标题" />
            <div className="notes-document-meta"><span>更新时间：今天 10:32</span><span>字数：{wordCount.toLocaleString()}</span><span>阅读时间：{Math.max(1, Math.ceil(wordCount / 300))} 分钟</span><div>{currentTags.map(tag => <button key={tag}>#{tag}</button>)}<button title="TODO：标签维护接口待实现" onClick={() => showNotice('标签管理待后端接口实现')}><Plus size={13} /></button></div></div>
            <div className="notes-editor-area">
              <div className="notes-editor-tip"><Sparkles size={16} /> 支持 Markdown 书写。内容会保存为你的个人笔记。</div>
              <textarea value={content} onChange={event => setContent(event.target.value)} placeholder="在这里记录你的想法…" spellCheck={false} />
            </div>
            <footer className="notes-document-footer"><span>Markdown</span><span>{wordCount.toLocaleString()} 字</span><span>{Math.max(1, Math.ceil(wordCount / 300))} 分钟阅读</span><span>{isDirty ? '修改尚未保存' : '自动保存已开启'} <i /></span></footer>
          </article>
        </div>
      </main>

      <aside className="notes-inspector">
        <section className="notes-inspector-card"><h2><Sparkles size={18} /> AI 写作助手</h2><div className="notes-ai-actions">
          {[['总结内容', '提炼全文重点', ListTree], ['提炼要点', '生成核心摘要', Quote], ['生成大纲', '创建内容结构', FolderOpen], ['改写语气', '调整表达风格', PencilLine], ['补充示例', '丰富内容案例', Lightbulb], ['关联知识库', '查找相关内容', Link2]].map(([name, text, ActionIcon]) => <button key={name as string} title="TODO：AI 笔记增强能力待实现" onClick={() => showNotice(`${name}：待后端 AI 笔记能力实现`)}><ActionIcon size={17} /><span><strong>{name as string}</strong><small>{text as string}</small></span></button>)}
        </div></section>
        <section className="notes-inspector-card"><header><h2><Link2 size={17} /> 相关笔记</h2><button title="TODO：笔记关联关系待实现" onClick={() => showNotice('相关笔记待后端关联能力实现')}>查看全部</button></header><div className="notes-related">{notes.filter(note => note !== selectedNote).slice(0, 4).map(note => <button key={note} onClick={() => openNote(note)}><FileText size={14} /> {note}<Star size={13} /></button>)}{notes.length < 2 && <p>保存更多笔记后会在这里展示关联内容。</p>}</div></section>
        <section className="notes-inspector-card"><header><h2><Quote size={17} /> 最近引用</h2><button title="TODO：引用追踪能力待实现" onClick={() => showNotice('引用追踪待后端实现')}>查看全部</button></header><div className="notes-quote"><p>“把重要的想法沉淀下来，成为下一次行动的起点。”</p><small>暂未接入笔记引用追踪</small></div></section>
        <section className="notes-inspector-card"><header><h2><Tags size={17} /> 标签</h2><button title="TODO：标签管理接口待实现" onClick={() => showNotice('标签管理待后端接口实现')}>管理</button></header><div className="notes-tags">{currentTags.map((tag, index) => <span key={tag}>{tag} <b>{12 - index * 3}</b></span>)}</div></section>
        <section className="notes-inspiration"><div><Lightbulb size={17} /> 写作灵感</div><p>真正的表达，不是为了被理解，而是为了遇见更真实的自己。</p><button title="TODO：灵感内容服务待实现" onClick={() => showNotice('灵感内容服务待后端实现')}>换一句</button></section>
      </aside>

      {notice && <div className={`notes-notice ${notice.tone}`}><span>{notice.text}</span><button onClick={() => setNotice(null)}><X size={15} /></button></div>}
    </section>
  )
}
