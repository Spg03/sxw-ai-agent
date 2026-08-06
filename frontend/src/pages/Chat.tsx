import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useLocation } from 'react-router-dom'
import { agentApi } from '../api/agent'
import { api } from '../api/client'
import {
  BookOpen, BrainCircuit, ChevronDown, ChevronRight, Clock3, FileText,
  Globe2, Heart, Link2, ListTodo, MessageCircleMore, MoreHorizontal,
  Paperclip, Pin, Send, Square, User, Wrench,
} from 'lucide-react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'

type Profile = 'GENERAL' | 'LOVE'
interface Message { id: string; role: 'user' | 'assistant'; content: string; timestamp: string }
interface ChatSession { id: string; title: string; profile: Profile; createdAt: string; updatedAt: string; messages: Message[] }
const STORAGE_KEY = 'agentforge-chat-sessions-v2'
const uid = () => globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`
const createSession = (profile: Profile = 'GENERAL'): ChatSession => { const now = new Date().toISOString(); return { id: `chat_${uid()}`, title: '新建对话', profile, createdAt: now, updatedAt: now, messages: [] } }

void createSession
function loadSessions(): ChatSession[] { try { const raw = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '[]'); return Array.isArray(raw) ? raw.filter(item => item?.id && Array.isArray(item.messages)) : [] } catch { return [] } }
function time(value: string) { const date = new Date(value); return date.toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric' }) === new Date().toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric' }) ? date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }) : date.toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric' }) }

const starters = [
  { icon: BrainCircuit, title: '分析项目代码', text: '请帮我分析当前项目的架构、关键模块和可优化的地方。' },
  { icon: FileText, title: '帮我写工作日报', text: '请根据我的工作内容，帮我写一份结构清晰的工作日报。' },
  { icon: BookOpen, title: '总结技术文档', text: '请帮我提炼技术文档的重点，并输出结构化总结。' },
  { icon: Link2, title: '设计 Agent 工作流', text: '请帮我设计一个可执行的 Agent 工作流，并说明每一步职责。' },
  { icon: ListTodo, title: '制定学习计划', text: '请根据我的目标，帮我制定一份可执行的学习计划。' },
  { icon: BookOpen, title: '知识库问答', text: '请基于已有知识库，回答我的问题并说明依据。' },
]

export default function Chat() {
  const location = useLocation()
  const [sessions, setSessions] = useState<ChatSession[]>([])
  const [activeId, setActiveId] = useState('')
  const [input, setInput] = useState('')
  const [profileOpen, setProfileOpen] = useState(false)
  const [loading, setLoading] = useState(false)
  const [streaming, setStreaming] = useState('')
  const [toolStatus, setToolStatus] = useState<string | null>(null)
  const sourceRef = useRef<EventSource | null>(null)
  const requestRef = useRef(0)
  const streamRef = useRef('')
  const routeRef = useRef('')
  const endRef = useRef<HTMLDivElement>(null)
  const textareaRef = useRef<HTMLTextAreaElement>(null)

  useEffect(() => { agentApi.listConversations().then(async result => { const remote = result.data; const hydrated = await Promise.all(remote.map(async item => { const messages = await agentApi.conversationMessages(item.conversationId).then(r => r.data).catch(() => []); return { id: item.conversationId, title: item.title, profile: item.profile === 'LOVE' ? 'LOVE' : 'GENERAL', createdAt: item.createdAt, updatedAt: item.updatedAt, messages: messages.map(message => ({ id: String(message.id), role: message.role === 'USER' ? 'user' : 'assistant', content: message.content, timestamp: message.createdAt })) } })); setSessions(hydrated as ChatSession[]); setActiveId(hydrated[0]?.id ?? '') }).catch(() => setSessions(loadSessions())) }, [])
  useEffect(() => { if (!sessions.length || activeId) return; setActiveId(sessions[0].id) }, [sessions, activeId])
  useEffect(() => { window.dispatchEvent(new Event('agentforge-history-updated')) }, [sessions])
  useEffect(() => () => sourceRef.current?.close(), [])
  useEffect(() => { endRef.current?.scrollIntoView({ behavior: 'smooth' }) }, [activeId, streaming, sessions])
  useEffect(() => {
    const params = new URLSearchParams(location.search); const requested = params.get('session')
    if (requested && sessions.some(session => session.id === requested)) { setActiveId(requested); return }
    if (params.has('new') && routeRef.current !== location.search) { routeRef.current = location.search; const profile = params.get('profile') === 'LOVE' ? 'LOVE' : 'GENERAL'; agentApi.createConversation(profile).then(result => { const item=result.data; const session: ChatSession={id:item.conversationId,title:item.title,profile:profile,createdAt:item.createdAt,updatedAt:item.updatedAt,messages:[]};setSessions(previous => [session, ...previous]);setActiveId(session.id) }) }
  }, [location.search, sessions])

  const active = useMemo(() => sessions.find(session => session.id === activeId) ?? sessions[0], [activeId, sessions])
  const update = useCallback((id: string, fn: (session: ChatSession) => ChatSession) => setSessions(previous => previous.map(session => session.id === id ? fn(session) : session)), [])
  const addMessage = useCallback((id: string, message: Message) => update(id, session => { const messages = [...session.messages, message]; const title = !session.messages.length && message.role === 'user' ? message.content.replace(/\s+/g, ' ').slice(0, 22) : session.title; return { ...session, messages, title, updatedAt: message.timestamp } }), [update])
  const send = () => {
    const text = input.trim(); if (!text || !active || loading) return
    const sessionId = active.id; const requestId = ++requestRef.current
    addMessage(sessionId, { id: uid(), role: 'user', content: text, timestamp: new Date().toISOString() }); setInput(''); setLoading(true); setStreaming(''); streamRef.current = ''
    const finish = (answer?: string) => { if (requestRef.current !== requestId) return; const content = (answer || streamRef.current).trim(); if (content) addMessage(sessionId, { id: uid(), role: 'assistant', content, timestamp: new Date().toISOString() }); setStreaming(''); streamRef.current = ''; setToolStatus(null); setLoading(false); sourceRef.current?.close(); sourceRef.current = null }
    sourceRef.current = agentApi.streamChat({ chatId: sessionId, message: text, profile: active.profile }, { onToken: token => { if (requestRef.current !== requestId) return; streamRef.current += token; setStreaming(streamRef.current) }, onToolCall: name => setToolStatus(`正在调用 ${name}`), onToolResult: name => setToolStatus(`${name} 已完成`), onDone: event => finish(event.answer), onError: error => finish(streamRef.current || `请求失败：${error}`) }, api.getToken())
  }
  const stop = () => { requestRef.current++; sourceRef.current?.close(); sourceRef.current = null; if (streamRef.current && active) addMessage(active.id, { id: uid(), role: 'assistant', content: `${streamRef.current}\n\n_已停止生成_`, timestamp: new Date().toISOString() }); streamRef.current = ''; setStreaming(''); setLoading(false); setToolStatus(null) }
  const setStarter = (value: string) => { setInput(value); textareaRef.current?.focus() }

  return <div className="chat-workspace chat-workspace-stage">
    <section className="chat-stage">
      <header className="chat-stage-header"><div className="chat-header-profile-wrap"><button className="chat-header-profile" onClick={() => setProfileOpen(open => !open)}><MessageCircleMore size={18} />{active?.profile === 'LOVE' ? '情感伙伴' : '通用助手'}<ChevronDown size={14} /></button>{profileOpen && <div className="chat-profile-menu"><button className={active?.profile === 'GENERAL' ? 'active' : ''} onClick={() => { if (active) update(active.id, session => ({ ...session, profile: 'GENERAL' })); setProfileOpen(false) }}><MessageCircleMore size={16} /><span><strong>通用助手</strong><small>工作、学习与知识处理</small></span></button><button className={active?.profile === 'LOVE' ? 'active love' : ''} onClick={() => { if (active) update(active.id, session => ({ ...session, profile: 'LOVE' })); setProfileOpen(false) }}><Heart size={16} /><span><strong>情感伙伴</strong><small>倾听、陪伴与情绪复盘</small></span></button></div>}</div><span /><button title="历史记录"><Clock3 size={18} /></button><button title="固定会话"><Pin size={18} /></button><button title="更多"><MoreHorizontal size={19} /></button></header>
      <main className="chat-stream"><div className="chat-stream-inner">{!active?.messages.length && !streaming && <div className="chat-welcome"><div className="chat-welcome-icon"><MessageCircleMore size={39} /></div><h1>有什么我能帮你的吗？</h1><p>{active?.profile === 'LOVE' ? '情感伙伴会倾听你的想法，陪你整理情绪与感受。' : '通用助手随时为你提供帮助，解答问题、激发灵感、提升效率。'}</p><div className="chat-starters">{starters.map(({ icon: Icon, title, text }) => <button key={title} onClick={() => setStarter(text)}><span><Icon size={23} /></span><div><strong>{title}</strong><small>{text.slice(0, 20)}…</small></div><ChevronRight size={18} /></button>)}</div></div>}{active?.messages.map(message => <MessageBubble key={message.id} message={message} />)}{streaming && <MessageBubble message={{ id: 'stream', role: 'assistant', content: streaming, timestamp: new Date().toISOString() }} streaming />}{toolStatus && <div className="chat-tool-status"><Wrench size={15} />{toolStatus}</div>}<div ref={endRef} /></div></main>
      <footer className="chat-composer"><div className="chat-composer-box"><textarea ref={textareaRef} value={input} onChange={event => setInput(event.target.value)} onKeyDown={event => { if (event.key === 'Enter' && !event.shiftKey) { event.preventDefault(); send() } }} placeholder="输入你的问题，/ 唤起快捷指令" rows={2} /><div className="chat-composer-tools"><button title="TODO: 后端尚未提供附件上传接口" onClick={() => setToolStatus('TODO：附件上传接口待实现')}><Paperclip size={18} />文件</button><button title="TODO: 知识库上下文选择待实现" onClick={() => setToolStatus('TODO：知识库上下文选择待实现')}><BookOpen size={18} />知识库</button><button title="TODO: 联网搜索服务待实现" onClick={() => setToolStatus('TODO：联网搜索服务待实现')}><Globe2 size={18} />联网搜索</button><button title="TODO: 工具选择器待实现" onClick={() => setToolStatus('TODO：工具选择器待实现')}><Wrench size={18} />工具<ChevronDown size={13} /></button><button title="TODO: 任务模式待实现" onClick={() => setToolStatus('TODO：任务模式待实现')}><ListTodo size={18} />任务模式</button><span />{loading ? <button className="chat-send stop" onClick={stop}><Square size={15} fill="currentColor" />停止</button> : <button className="chat-send" onClick={send} disabled={!input.trim()}><Send size={17} />发送</button>}</div></div></footer>
    </section>
  </div>
}

function MessageBubble({ message, streaming }: { message: Message; streaming?: boolean }) { const user = message.role === 'user'; return <article className={`chat-message ${user ? 'user' : 'assistant'}`}><div className="chat-message-avatar">{user ? <User size={17} /> : <MessageCircleMore size={18} />}</div><div className="chat-message-content">{user ? <p>{message.content}</p> : <ReactMarkdown remarkPlugins={[remarkGfm]}>{message.content}</ReactMarkdown>}{streaming && <i className="chat-caret" />}<time>{time(message.timestamp)}</time></div></article> }
