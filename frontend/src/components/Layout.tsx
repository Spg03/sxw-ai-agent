import { useEffect, useState, type ReactNode } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../contexts/AuthContext'
import { agentApi } from '../api/agent'
import {
  Activity, BookOpen, BrainCircuit, ChevronRight, Grid2X2, Heart,
  History, LogOut, MessageSquarePlus, NotebookPen, Search, Sparkles,
  TestTube, Wrench,
} from 'lucide-react'

interface LayoutProps { children: ReactNode }
interface StoredSession { id: string; title: string; updatedAt: string; messages: unknown[] }
const HISTORY_KEY = 'agentforge-chat-sessions-v2'

function loadHistory(): StoredSession[] {
  try {
    const data = JSON.parse(localStorage.getItem(HISTORY_KEY) ?? '[]')
    return Array.isArray(data) ? data.filter(item => item?.id && item?.title).sort((a, b) => String(b.updatedAt).localeCompare(String(a.updatedAt))) : []
  } catch { return [] }
}

function relativeTime(value: string) {
  const diff = Date.now() - new Date(value).getTime()
  const minutes = Math.max(0, Math.floor(diff / 60000))
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes} 分钟前`
  if (minutes < 1440) return `${Math.floor(minutes / 60)} 小时前`
  return `${Math.floor(minutes / 1440)} 天前`
}

function SidebarMark() {
  return <div className="workspace-mark"><div /><Heart size={18} fill="currentColor" /><Sparkles size={10} /></div>
}

const toolboxItems = [
  { path: '/notes', icon: NotebookPen, label: '笔记' },
  { path: '/knowledge', icon: BookOpen, label: '知识库' },
  { path: '/hermes', icon: BrainCircuit, label: 'Hermes 复盘' },
  { path: '/eval', icon: TestTube, label: '评测' },
  { path: '/traces', icon: Activity, label: '追踪' },
]

export default function Layout({ children }: LayoutProps) {
  const location = useLocation()
  const navigate = useNavigate()
  const { user, logout } = useAuth()
  const [query, setQuery] = useState('')
  const [toolboxOpen, setToolboxOpen] = useState(false)
  const [history, setHistory] = useState<StoredSession[]>([])
  const name = user?.nickname || user?.username || '用户'

  useEffect(() => {
    let cancelled = false
    const refresh = async () => {
      try {
        const result = await agentApi.listConversations()
        if (!cancelled) setHistory(result.data.map(item => ({ id: item.conversationId, title: item.title, updatedAt: item.updatedAt, messages: [] })))
      } catch {
        if (!cancelled) setHistory(loadHistory())
      }
    }
    void refresh()
    window.addEventListener('agentforge-history-updated', refresh)
    return () => { cancelled = true; window.removeEventListener('agentforge-history-updated', refresh) }
  }, [location.pathname, location.search])

  const filteredHistory = history.filter(item => item.title.toLowerCase().includes(query.toLowerCase()))
  const isActive = (path: string) => location.pathname === path
  const chatWorkspace = location.pathname === '/chat'

  return <div className="workspace-shell">
    <aside className="workspace-sidebar">
      <div className="workspace-top">
        <Link to="/" className="workspace-brand"><SidebarMark /><span>AgentForge</span></Link>
        <label className="workspace-search"><Search size={17} /><input value={query} onChange={event => setQuery(event.target.value)} placeholder="搜索会话" /><kbd>Ctrl K</kbd></label>
        <nav className="workspace-nav">
          <Link to="/" className={isActive('/') ? 'active' : ''}><Grid2X2 size={19} />仪表盘</Link>
          <Link to="/chat?new=1" onClick={event => { event.preventDefault(); navigate(`/chat?new=${Date.now()}`) }}><MessageSquarePlus size={20} />新对话</Link>
          <Link to="/treehole" className={isActive('/treehole') ? 'active' : ''}><Heart size={20} />树洞</Link>
          <Link to="/skills" className={isActive('/skills') ? 'active' : ''}><Wrench size={20} />技能</Link>
          <div className="workspace-toolbox" onMouseEnter={() => setToolboxOpen(true)} onMouseLeave={() => setToolboxOpen(false)}>
            <button onClick={() => setToolboxOpen(value => !value)} className={toolboxOpen || toolboxItems.some(item => isActive(item.path)) ? 'active' : ''}><Sparkles size={20} />工具箱<ChevronRight size={16} className={toolboxOpen ? 'rotated' : ''} /></button>
            {toolboxOpen && <div className="workspace-toolbox-menu">{toolboxItems.map(item => { const Icon = item.icon; return <Link key={item.path} to={item.path} onClick={() => setToolboxOpen(false)} className={isActive(item.path) ? 'active' : ''}><Icon size={17} />{item.label}</Link> })}</div>}
          </div>
        </nav>
      </div>
      <section className="workspace-history"><div className="workspace-history-title"><span>历史对话</span><History size={15} /></div><div className="workspace-history-scroll">{filteredHistory.length ? filteredHistory.map(session => <button key={session.id} onClick={() => navigate(`/chat?session=${encodeURIComponent(session.id)}`)} className={new URLSearchParams(location.search).get('session') === session.id ? 'active' : ''}><span className="history-dot">◌</span><span className="history-content"><strong>{session.title}</strong><small>{session.messages?.length ?? 0} 条消息 · {relativeTime(session.updatedAt)}</small></span></button>) : <p className="workspace-history-empty">{query ? '没有匹配的会话' : '还没有历史对话'}</p>}</div></section>
      <div className="workspace-user"><div className="workspace-avatar">{name[0]?.toUpperCase() || 'U'}</div><div><strong>{name}</strong><span>{user?.username || 'AgentForge 用户'}</span></div><button onClick={logout} title="退出登录"><LogOut size={18} /></button></div>
    </aside>
    <main className="workspace-main"><div className={chatWorkspace ? 'chat-page-host animate-fade-in' : 'workspace-content animate-fade-in'}>{children}</div></main>
  </div>
}
