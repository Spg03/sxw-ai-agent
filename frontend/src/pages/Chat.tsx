import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  Bot, ChevronDown, Heart, MessageSquare, Plus, Search,
  Send, Sparkles, Square, Trash2, User, Wrench,
} from 'lucide-react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { agentApi } from '../api/agent'
import { api } from '../api/client'

type Profile = 'GENERAL' | 'LOVE'

interface Message {
  id: string
  role: 'user' | 'assistant'
  content: string
  timestamp: string
}

interface ChatSession {
  id: string
  title: string
  profile: Profile
  createdAt: string
  updatedAt: string
  messages: Message[]
}

const STORAGE_KEY = 'agentforge-chat-sessions-v2'
const ACTIVE_SESSION_KEY = 'agentforge-active-chat-v2'

const uid = () => globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`

function createSession(profile: Profile = 'GENERAL'): ChatSession {
  const now = new Date().toISOString()
  return { id: `chat_${uid()}`, title: '新建对话', profile, createdAt: now, updatedAt: now, messages: [] }
}

function loadSessions(): ChatSession[] {
  try {
    const parsed = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '[]')
    if (!Array.isArray(parsed)) return []
    return parsed.filter((item): item is ChatSession => item && typeof item.id === 'string' && Array.isArray(item.messages))
      .map(session => ({ ...session, messages: session.messages.filter(message => message?.id && message?.content) }))
  } catch {
    return []
  }
}

function formatDate(date: string) {
  const value = new Date(date)
  const today = new Date()
  if (value.toDateString() === today.toDateString()) return value.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  return value.toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric' })
}

export default function Chat() {
  const [sessions, setSessions] = useState<ChatSession[]>(loadSessions)
  const [activeId, setActiveId] = useState(() => localStorage.getItem(ACTIVE_SESSION_KEY) ?? '')
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const [streamingContent, setStreamingContent] = useState('')
  const [toolStatus, setToolStatus] = useState<string | null>(null)
  const [search, setSearch] = useState('')
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const eventSourceRef = useRef<EventSource | null>(null)
  const requestRef = useRef(0)
  const streamedTextRef = useRef('')

  useEffect(() => {
    if (!sessions.length) {
      const session = createSession()
      setSessions([session])
      setActiveId(session.id)
    }
  }, [sessions.length])

  useEffect(() => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(sessions.slice(0, 60)))
  }, [sessions])

  useEffect(() => {
    if (activeId) localStorage.setItem(ACTIVE_SESSION_KEY, activeId)
  }, [activeId])

  const activeSession = useMemo(
    () => sessions.find(session => session.id === activeId) ?? sessions[0],
    [sessions, activeId],
  )

  useEffect(() => {
    if (activeSession && activeSession.id !== activeId) setActiveId(activeSession.id)
  }, [activeId, activeSession])

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [activeSession?.messages.length, streamingContent])

  useEffect(() => () => eventSourceRef.current?.close(), [])

  const updateSession = useCallback((id: string, updater: (session: ChatSession) => ChatSession) => {
    setSessions(previous => previous.map(session => session.id === id ? updater(session) : session))
  }, [])

  const appendMessage = useCallback((sessionId: string, message: Message) => {
    updateSession(sessionId, session => {
      if (session.messages.some(item => item.id === message.id)) return session
      const messages = [...session.messages, message].slice(-200)
      const title = session.messages.length === 0 && message.role === 'user'
        ? message.content.replace(/\s+/g, ' ').slice(0, 22) || '新建对话'
        : session.title
      return { ...session, messages, title, updatedAt: message.timestamp }
    })
  }, [updateSession])

  const createNewChat = (profile: Profile = activeSession?.profile ?? 'GENERAL') => {
    if (loading) return
    const session = createSession(profile)
    setSessions(previous => [session, ...previous])
    setActiveId(session.id)
    setInput('')
    setStreamingContent('')
  }

  const deleteSession = (id: string, event: React.MouseEvent) => {
    event.stopPropagation()
    if (loading) return
    const session = sessions.find(item => item.id === id)
    if (session) agentApi.clearMemory(session.id).catch(() => undefined)
    const remaining = sessions.filter(item => item.id !== id)
    if (!remaining.length) {
      const fresh = createSession()
      setSessions([fresh])
      setActiveId(fresh.id)
    } else {
      setSessions(remaining)
      if (id === activeId) setActiveId(remaining[0].id)
    }
  }

  const autoResize = () => {
    const textarea = textareaRef.current
    if (!textarea) return
    textarea.style.height = 'auto'
    textarea.style.height = `${Math.min(textarea.scrollHeight, 150)}px`
  }

  const handleSend = () => {
    const message = input.trim()
    if (!message || loading || !activeSession) return

    const sessionId = activeSession.id
    const requestId = ++requestRef.current
    const now = new Date().toISOString()
    appendMessage(sessionId, { id: uid(), role: 'user', content: message, timestamp: now })
    setInput('')
    setLoading(true)
    setStreamingContent('')
    streamedTextRef.current = ''
    setToolStatus(null)

    const complete = (answer?: string) => {
      if (requestRef.current !== requestId) return
      const content = (answer || streamedTextRef.current).trim()
      if (content) appendMessage(sessionId, { id: uid(), role: 'assistant', content, timestamp: new Date().toISOString() })
      setStreamingContent('')
      streamedTextRef.current = ''
      setToolStatus(null)
      setLoading(false)
      eventSourceRef.current?.close()
      eventSourceRef.current = null
    }

    eventSourceRef.current = agentApi.streamChat(
      { chatId: sessionId, message, profile: activeSession.profile },
      {
        onToken: token => {
          if (requestRef.current !== requestId) return
          streamedTextRef.current += token
          setStreamingContent(streamedTextRef.current)
        },
        onToolCall: toolName => requestRef.current === requestId && setToolStatus(`正在调用 ${toolName}`),
        onToolResult: toolName => requestRef.current === requestId && setToolStatus(`${toolName} 已完成`),
        onDone: event => complete(event.answer),
        onError: error => {
          if (requestRef.current !== requestId) return
          complete(streamedTextRef.current || `请求失败：${error}`)
        },
      },
      api.getToken(),
    )
  }

  const stopGeneration = () => {
    if (!loading) return
    requestRef.current += 1
    eventSourceRef.current?.close()
    eventSourceRef.current = null
    const content = streamedTextRef.current.trim()
    if (content && activeSession) appendMessage(activeSession.id, { id: uid(), role: 'assistant', content: `${content}\n\n_已停止生成_`, timestamp: new Date().toISOString() })
    streamedTextRef.current = ''
    setStreamingContent('')
    setToolStatus(null)
    setLoading(false)
  }

  const changeProfile = (profile: Profile) => {
    if (!activeSession || loading) return
    updateSession(activeSession.id, session => ({ ...session, profile, updatedAt: new Date().toISOString() }))
  }

  const visibleSessions = sessions
    .filter(session => session.title.toLowerCase().includes(search.toLowerCase()))
    .sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))

  return (
    <div className="flex h-screen min-h-[640px] overflow-hidden bg-[#090b14] text-slate-100">
      <aside className="flex w-[292px] shrink-0 flex-col border-r border-white/[0.08] bg-[#0e1120]">
        <div className="p-4">
          <button onClick={() => createNewChat()} className="flex w-full items-center justify-center gap-2 rounded-xl bg-gradient-to-r from-violet-500 to-blue-500 px-4 py-3 text-sm font-semibold shadow-lg shadow-violet-500/20 hover:brightness-110">
            <Plus size={18} /> 新建会话
          </button>
          <div className="relative mt-4">
            <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-500" />
            <input value={search} onChange={event => setSearch(event.target.value)} placeholder="搜索会话" className="w-full rounded-lg py-2 pl-9 pr-3 text-xs" />
          </div>
        </div>
        <div className="flex-1 overflow-y-auto px-2 pb-3">
          <p className="px-3 pb-2 text-[11px] font-medium uppercase tracking-wider text-slate-500">会话历史</p>
          {visibleSessions.map(session => (
            <button key={session.id} onClick={() => !loading && setActiveId(session.id)} className={`group mb-1 flex w-full items-center gap-3 rounded-xl px-3 py-3 text-left transition ${session.id === activeSession?.id ? 'bg-white/[0.09] text-white' : 'text-slate-400 hover:bg-white/[0.05] hover:text-slate-200'}`}>
              <MessageSquare size={16} className={session.id === activeSession?.id ? 'text-violet-300' : 'text-slate-500'} />
              <span className="min-w-0 flex-1">
                <span className="block truncate text-sm">{session.title}</span>
                <span className="mt-1 block text-xs text-slate-500">{session.messages.length ? `${session.messages.length} 条消息 · ${formatDate(session.updatedAt)}` : '等待你的第一条消息'}</span>
              </span>
              <span onClick={event => deleteSession(session.id, event)} className="invisible rounded-md p-1 text-slate-500 hover:bg-rose-500/15 hover:text-rose-300 group-hover:visible" title="删除会话"><Trash2 size={14} /></span>
            </button>
          ))}
        </div>
        <div className="border-t border-white/[0.08] p-4 text-xs leading-5 text-slate-500">会话会保存在当前浏览器；服务端同步保存最近 10 轮上下文。</div>
      </aside>

      <section className="flex min-w-0 flex-1 flex-col bg-[radial-gradient(circle_at_65%_-20%,rgba(79,70,229,.16),transparent_35%)]">
        <header className="flex h-[76px] shrink-0 items-center justify-between border-b border-white/[0.08] px-7">
          <div className="flex items-center gap-3">
            <div className={`grid h-10 w-10 place-items-center rounded-xl ${activeSession?.profile === 'LOVE' ? 'bg-rose-500/20 text-rose-300' : 'bg-violet-500/20 text-violet-300'}`}>{activeSession?.profile === 'LOVE' ? <Heart size={20} /> : <Sparkles size={20} />}</div>
            <div><h1 className="font-semibold">{activeSession?.title || '新建会话'}</h1><p className="mt-0.5 text-xs text-slate-500">记忆已启用 · 最近 10 轮对话会自动带入</p></div>
          </div>
          <label className="relative"><select value={activeSession?.profile ?? 'GENERAL'} onChange={event => changeProfile(event.target.value as Profile)} className="appearance-none rounded-lg py-2 pl-3 pr-8 text-sm"><option value="GENERAL">通用助手</option><option value="LOVE">情感伙伴</option></select><ChevronDown size={14} className="pointer-events-none absolute right-2 top-1/2 -translate-y-1/2 text-slate-400" /></label>
        </header>

        <main className="flex-1 overflow-y-auto px-6 py-8 sm:px-10">
          <div className="mx-auto max-w-4xl space-y-6">
            {!activeSession?.messages.length && !streamingContent && <div className="py-24 text-center"><div className="mx-auto mb-5 grid h-16 w-16 place-items-center rounded-2xl bg-violet-500/15 text-violet-300"><Sparkles size={30} /></div><h2 className="text-xl font-semibold">从一个问题开始</h2><p className="mx-auto mt-3 max-w-md text-sm leading-6 text-slate-400">新会话拥有独立上下文。告诉我你的目标、偏好或需要完成的工作，我会在本次对话中持续记住。</p></div>}
            {activeSession?.messages.map(message => <MessageBubble key={message.id} message={message} />)}
            {streamingContent && <MessageBubble message={{ id: 'streaming', role: 'assistant', content: streamingContent, timestamp: new Date().toISOString() }} streaming />}
            {loading && !streamingContent && <div className="flex items-center gap-3 text-sm text-slate-400"><Bot size={18} className="text-violet-300" /><span className="flex gap-1"><i className="h-1.5 w-1.5 animate-bounce rounded-full bg-violet-300" /><i className="h-1.5 w-1.5 animate-bounce rounded-full bg-violet-300 [animation-delay:150ms]" /><i className="h-1.5 w-1.5 animate-bounce rounded-full bg-violet-300 [animation-delay:300ms]" /></span></div>}
            {toolStatus && <div className="flex items-center gap-2 rounded-lg border border-amber-400/15 bg-amber-400/5 px-3 py-2 text-xs text-amber-200"><Wrench size={14} />{toolStatus}</div>}
            <div ref={messagesEndRef} />
          </div>
        </main>

        <footer className="shrink-0 px-6 pb-6 pt-3 sm:px-10"><div className="mx-auto max-w-4xl rounded-2xl border border-white/[0.1] bg-[#111526] p-2 shadow-2xl shadow-black/20"><textarea ref={textareaRef} value={input} onChange={event => { setInput(event.target.value); autoResize() }} onKeyDown={event => { if (event.key === 'Enter' && !event.shiftKey) { event.preventDefault(); handleSend() } }} placeholder="输入消息，Enter 发送，Shift + Enter 换行" rows={1} className="block min-h-12 w-full resize-none border-0 bg-transparent px-3 py-3 text-sm outline-none" /> <div className="flex items-center justify-between border-t border-white/[0.06] px-2 pt-2"><span className="text-xs text-slate-500">{activeSession?.profile === 'LOVE' ? '情感陪伴模式' : '通用任务模式'}</span>{loading ? <button onClick={stopGeneration} className="flex items-center gap-2 rounded-lg bg-rose-500 px-3 py-2 text-xs font-medium"><Square size={13} fill="currentColor" />停止</button> : <button onClick={handleSend} disabled={!input.trim()} className="grid h-9 w-9 place-items-center rounded-lg bg-violet-500 text-white disabled:opacity-40 hover:bg-violet-400"><Send size={16} /></button>}</div></div></footer>
      </section>
    </div>
  )
}

function MessageBubble({ message, streaming = false }: { message: Message; streaming?: boolean }) {
  const user = message.role === 'user'
  return <div className={`flex gap-3 ${user ? 'justify-end' : 'justify-start'}`}><div className={`flex max-w-[85%] gap-3 ${user ? 'flex-row-reverse' : ''}`}><div className={`grid h-8 w-8 shrink-0 place-items-center rounded-lg ${user ? 'bg-blue-500/20 text-blue-200' : 'bg-violet-500/20 text-violet-200'}`}>{user ? <User size={16} /> : <Bot size={16} />}</div><div className={`rounded-2xl px-4 py-3 text-sm leading-6 ${user ? 'rounded-tr-sm bg-blue-600 text-white' : 'rounded-tl-sm border border-white/[0.08] bg-white/[0.035] text-slate-200'}`}>{user ? <p className="whitespace-pre-wrap">{message.content}</p> : <div className="prose prose-invert prose-sm max-w-none [&_p]:my-0 [&_pre]:bg-black/30 [&_pre]:p-3"><ReactMarkdown remarkPlugins={[remarkGfm]}>{message.content}</ReactMarkdown>{streaming && <span className="ml-1 inline-block h-4 w-1.5 animate-pulse bg-violet-300 align-middle" />}</div>}<div className={`mt-1.5 text-[11px] ${user ? 'text-blue-100/65' : 'text-slate-500'}`}>{formatDate(message.timestamp)}</div></div></div></div>
}
