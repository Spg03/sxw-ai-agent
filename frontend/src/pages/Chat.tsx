import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useLocation } from 'react-router-dom'
import { agentApi, type SavedMemory } from '../api/agent'
import { api } from '../api/client'
import { knowledgeApi, type KnowledgeDocument } from '../api/knowledge'
import {
  BookOpen, BrainCircuit, ChevronDown, ChevronRight, Clock3, FileText,
  Globe2, Heart, Link2, ListTodo, MessageCircleMore, MoreHorizontal,
  Paperclip, Pin, Send, Square, User, Wrench, Check, X, ShieldAlert, Archive, Trash2,
} from 'lucide-react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'

type Profile = 'GENERAL' | 'LOVE'
type TaskMode = 'CHAT' | 'PLAN' | 'EXECUTE'
interface Message { id: string; role: 'user' | 'assistant'; content: string; timestamp: string }
interface ChatSession { id: string; title: string; profile: Profile; createdAt: string; updatedAt: string; messages: Message[] }
interface MemoryStatus { messageCount: number; hasSummary: boolean; workingMemoryVersion: number }
interface MemoryCandidate { candidateId: string; title: string; content: string; status: string; sourceKind: string; createdAt: string }
interface PendingAttachment { attachmentId: string; name: string; sizeBytes: number }
interface ToolChoice { name: string; description: string; riskLevel: string; networkRequired: boolean; available: boolean }
interface PlanSummary { planId: string; goal: string; status: string }
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
  const [securityNotice, setSecurityNotice] = useState<string | null>(null)
  const [memoryOpen, setMemoryOpen] = useState(false)
  const [memoryReadEnabled, setMemoryReadEnabled] = useState(() => sessionStorage.getItem('agentforge-memory-read') !== 'false')
  const [memoryWriteEnabled, setMemoryWriteEnabled] = useState(() => sessionStorage.getItem('agentforge-memory-write') !== 'false')
  const [memoryStatus, setMemoryStatus] = useState<MemoryStatus | null>(null)
  const [memoryCandidates, setMemoryCandidates] = useState<MemoryCandidate[]>([])
  const [savedMemories, setSavedMemories] = useState<SavedMemory[]>([])
  const [archivedMemories, setArchivedMemories] = useState<SavedMemory[]>([])
  const [taskMode, setTaskMode] = useState<TaskMode>('CHAT')
  const [taskModeOpen, setTaskModeOpen] = useState(false)
  const [attachmentIds, setAttachmentIds] = useState<PendingAttachment[]>([])
  const [uploading, setUploading] = useState(false)
  const [planId, setPlanId] = useState('')
  const [knowledgeOpen, setKnowledgeOpen] = useState(false)
  const [knowledgeDocuments, setKnowledgeDocuments] = useState<KnowledgeDocument[]>([])
  const [knowledgeDocumentIds, setKnowledgeDocumentIds] = useState<string[]>([])
  const [webSearchEnabled, setWebSearchEnabled] = useState(false)
  const [toolsOpen, setToolsOpen] = useState(false)
  const [availableTools, setAvailableTools] = useState<ToolChoice[]>([])
  const [enabledTools, setEnabledTools] = useState<string[]>([])
  const [plans, setPlans] = useState<PlanSummary[]>([])
  const sourceRef = useRef<{ close: () => void } | null>(null)
  const requestRef = useRef(0)
  const streamRef = useRef('')
  const routeRef = useRef('')
  const endRef = useRef<HTMLDivElement>(null)
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

  const refreshMemoryPanel = useCallback(async (conversationId = activeId) => {
    if (!conversationId) return
    const [status, candidates, memories, archived] = await Promise.all([
      agentApi.memoryStatus(conversationId).then(result => result.data).catch(() => null),
      agentApi.memoryCandidates().then(result => result.data).catch(() => []),
      agentApi.memories().then(result => result.data).catch(() => []),
      agentApi.memories('ARCHIVED').then(result => result.data).catch(() => []),
    ])
    setMemoryStatus(status)
    setMemoryCandidates(candidates)
    setSavedMemories(memories)
    setArchivedMemories(archived)
  }, [activeId])

  useEffect(() => { agentApi.listConversations().then(async result => { const remote = result.data; const hydrated = await Promise.all(remote.map(async item => { const messages = await agentApi.conversationMessages(item.conversationId).then(r => r.data).catch(() => []); return { id: item.conversationId, title: item.title, profile: item.profile === 'LOVE' ? 'LOVE' : 'GENERAL', createdAt: item.createdAt, updatedAt: item.updatedAt, messages: messages.map(message => ({ id: String(message.id), role: message.role === 'USER' ? 'user' : 'assistant', content: message.content, timestamp: message.createdAt })) } })); setSessions(hydrated as ChatSession[]); setActiveId(hydrated[0]?.id ?? '') }).catch(() => setSessions(loadSessions())) }, [])
  useEffect(() => { if (!sessions.length || activeId) return; setActiveId(sessions[0].id) }, [sessions, activeId])
  useEffect(() => { window.dispatchEvent(new Event('agentforge-history-updated')) }, [sessions])
  useEffect(() => () => sourceRef.current?.close(), [])
  useEffect(() => { endRef.current?.scrollIntoView({ behavior: 'smooth' }) }, [activeId, streaming, sessions])
  useEffect(() => { sessionStorage.setItem('agentforge-memory-read', String(memoryReadEnabled)) }, [memoryReadEnabled])
  useEffect(() => { sessionStorage.setItem('agentforge-memory-write', String(memoryWriteEnabled)) }, [memoryWriteEnabled])
  useEffect(() => { setPlanId('') }, [activeId])
  useEffect(() => { if (activeId) void agentApi.plansForChat(activeId).then(result => setPlans(result.data)).catch(() => setPlans([])) }, [activeId])
  useEffect(() => { if (knowledgeOpen) void knowledgeApi.listDocuments().then(result => setKnowledgeDocuments(result.data)).catch(() => setKnowledgeDocuments([])) }, [knowledgeOpen])
  useEffect(() => { if (memoryOpen) void refreshMemoryPanel() }, [activeId, memoryOpen, refreshMemoryPanel])
  useEffect(() => {
    const params = new URLSearchParams(location.search); const requested = params.get('session')
    if (requested && sessions.some(session => session.id === requested)) { setActiveId(requested); return }
    if (params.has('new') && routeRef.current !== location.search) { routeRef.current = location.search; const profile = params.get('profile') === 'LOVE' ? 'LOVE' : 'GENERAL'; agentApi.createConversation(profile).then(result => { const item=result.data; const session: ChatSession={id:item.conversationId,title:item.title,profile:profile,createdAt:item.createdAt,updatedAt:item.updatedAt,messages:[]};setSessions(previous => [session, ...previous]);setActiveId(session.id) }) }
  }, [location.search, sessions])

  const active = useMemo(() => sessions.find(session => session.id === activeId) ?? sessions[0], [activeId, sessions])
  const activeProfile = active?.profile
  useEffect(() => { if (activeProfile) void agentApi.tools(activeProfile).then(result => setAvailableTools(result.data)).catch(() => setAvailableTools([])) }, [activeProfile])
  const update = useCallback((id: string, fn: (session: ChatSession) => ChatSession) => setSessions(previous => previous.map(session => session.id === id ? fn(session) : session)), [])
  const addMessage = useCallback((id: string, message: Message) => update(id, session => { const messages = [...session.messages, message]; const title = !session.messages.length && message.role === 'user' ? message.content.replace(/\s+/g, ' ').slice(0, 22) : session.title; return { ...session, messages, title, updatedAt: message.timestamp } }), [update])
  const send = () => {
    const text = input.trim(); if (!text || !active || loading) return
    const effectiveMode: TaskMode = taskMode === 'EXECUTE' && !planId ? 'PLAN' : taskMode
    if (effectiveMode !== taskMode) setToolStatus('执行模式需要已审批计划，已安全降级为规划模式')
    const sessionId = active.id; const requestId = ++requestRef.current; const selectedAttachmentIds = attachmentIds.map(item => item.attachmentId)
    addMessage(sessionId, { id: uid(), role: 'user', content: text, timestamp: new Date().toISOString() }); setInput(''); setLoading(true); setStreaming(''); setSecurityNotice(null); streamRef.current = ''
    const finish = (answer?: string) => { if (requestRef.current !== requestId) return; const content = (answer || streamRef.current).trim(); if (content) addMessage(sessionId, { id: uid(), role: 'assistant', content, timestamp: new Date().toISOString() }); setStreaming(''); streamRef.current = ''; setToolStatus(null); setLoading(false); sourceRef.current?.close(); sourceRef.current = null }
    sourceRef.current = agentApi.streamChat({ chatId: sessionId, message: text, profile: active.profile, memoryReadEnabled, memoryWriteEnabled, mode: effectiveMode, planId, attachmentIds: selectedAttachmentIds, knowledgeDocumentIds, enabledTools, webSearchEnabled, requestId: crypto.randomUUID() }, { onToken: token => { if (requestRef.current !== requestId) return; streamRef.current += token; setStreaming(streamRef.current) }, onToolCall: name => setToolStatus(`正在调用 ${name}`), onToolResult: name => setToolStatus(`${name} 已完成`), onDone: event => { finish(event.answer); void refreshMemoryPanel(sessionId); void agentApi.plansForChat(sessionId).then(result => setPlans(result.data)).catch(() => undefined) }, onSecurity: event => setSecurityNotice(event.message || `安全策略已处理该请求（${event.riskLevel}）`), onError: error => finish(streamRef.current || `请求失败：${error}`) }, api.getToken())
    setAttachmentIds([])
  }
  const stop = () => { requestRef.current++; sourceRef.current?.close(); sourceRef.current = null; if (streamRef.current && active) addMessage(active.id, { id: uid(), role: 'assistant', content: `${streamRef.current}\n\n_已停止生成_`, timestamp: new Date().toISOString() }); streamRef.current = ''; setStreaming(''); setLoading(false); setToolStatus(null) }
  const setStarter = (value: string) => { setInput(value); textareaRef.current?.focus() }
  const uploadAttachment = async (file?: File) => {
    if (!file || !active || uploading) return
    setUploading(true); setToolStatus(`正在上传 ${file.name}`)
    try { const uploaded = await agentApi.uploadAttachment(active.id, file); setAttachmentIds(current => [...current, uploaded]); setToolStatus(`${file.name} 已就绪`) }
    catch (error) { setToolStatus(error instanceof Error ? error.message : '附件上传失败') }
    finally { setUploading(false); if (fileInputRef.current) fileInputRef.current.value = '' }
  }
  const removeAttachment = async (item: PendingAttachment) => {
    if (!active) return
    setAttachmentIds(current => current.filter(value => value.attachmentId !== item.attachmentId))
    await agentApi.deleteAttachment(active.id, item.attachmentId).catch(() => undefined)
  }

  return <div className="chat-workspace chat-workspace-stage">
    <section className="chat-stage">
      <header className="chat-stage-header"><div className="chat-header-profile-wrap"><button className="chat-header-profile" onClick={() => setProfileOpen(open => !open)}><MessageCircleMore size={18} />{active?.profile === 'LOVE' ? '情感伙伴' : '通用助手'}<ChevronDown size={14} /></button>{profileOpen && <div className="chat-profile-menu"><button className={active?.profile === 'GENERAL' ? 'active' : ''} onClick={() => { if (active) update(active.id, session => ({ ...session, profile: 'GENERAL' })); setProfileOpen(false) }}><MessageCircleMore size={16} /><span><strong>通用助手</strong><small>工作、学习与知识处理</small></span></button><button className={active?.profile === 'LOVE' ? 'active love' : ''} onClick={() => { if (active) update(active.id, session => ({ ...session, profile: 'LOVE' })); setProfileOpen(false) }}><Heart size={16} /><span><strong>情感伙伴</strong><small>倾听、陪伴与情绪复盘</small></span></button></div>}</div><span /><button className={memoryOpen ? 'chat-memory-toggle active' : 'chat-memory-toggle'} title="记忆面板" onClick={() => setMemoryOpen(open => !open)}><BrainCircuit size={18} /></button><button title="历史记录"><Clock3 size={18} /></button><button title="固定会话"><Pin size={18} /></button><button title="更多"><MoreHorizontal size={19} /></button></header>
      <main className="chat-stream"><div className="chat-stream-inner">{securityNotice && <div className="chat-security-notice"><ShieldAlert size={17} /><span>{securityNotice}</span><button onClick={() => setSecurityNotice(null)}><X size={15} /></button></div>}{!active?.messages.length && !streaming && <div className="chat-welcome"><div className="chat-welcome-icon"><MessageCircleMore size={39} /></div><h1>有什么我能帮你的吗？</h1><p>{active?.profile === 'LOVE' ? '情感伙伴会倾听你的想法，陪你整理情绪与感受。' : '通用助手随时为你提供帮助，解答问题、激发灵感、提升效率。'}</p><div className="chat-starters">{starters.map(({ icon: Icon, title, text }) => <button key={title} onClick={() => setStarter(text)}><span><Icon size={23} /></span><div><strong>{title}</strong><small>{text.slice(0, 20)}…</small></div><ChevronRight size={18} /></button>)}</div></div>}{active?.messages.map(message => <MessageBubble key={message.id} message={message} />)}{streaming && <MessageBubble message={{ id: 'stream', role: 'assistant', content: streaming, timestamp: new Date().toISOString() }} streaming />}{toolStatus && <div className="chat-tool-status"><Wrench size={15} />{toolStatus}</div>}<div ref={endRef} /></div></main>
      {plans.filter(plan => plan.status === 'PENDING' || plan.status === 'APPROVED').slice(0, 1).map(plan => <aside className="chat-plan-card" key={plan.planId}><div><ListTodo size={16} /><strong>{plan.status === 'APPROVED' ? '已审批计划' : '待审批计划'}</strong><p>{plan.goal}</p></div>{plan.status === 'PENDING' ? <span><button onClick={() => void agentApi.rejectPlan(plan.planId).then(() => agentApi.plansForChat(activeId)).then(result => setPlans(result.data))}>拒绝</button><button onClick={() => void agentApi.approvePlan(plan.planId).then(() => agentApi.plansForChat(activeId)).then(result => { setPlans(result.data); setPlanId(plan.planId) })}>批准并执行</button></span> : <button onClick={() => { setPlanId(plan.planId); setTaskMode('EXECUTE') }}>进入执行模式</button>}</aside>)}
      <footer className="chat-composer"><div className="chat-composer-box">
        {attachmentIds.length > 0 && <div className="chat-attachments">{attachmentIds.map(item => <span key={item.attachmentId}><Paperclip size={13} />{item.name}<button onClick={() => void removeAttachment(item)}><X size={13} /></button></span>)}</div>}
        <textarea ref={textareaRef} value={input} onChange={event => setInput(event.target.value)} onKeyDown={event => { if (event.key === 'Enter' && !event.shiftKey) { event.preventDefault(); send() } }} placeholder="输入你的问题，/ 唤起快捷指令" rows={2} />
        <input ref={fileInputRef} className="chat-file-input" type="file" accept=".txt,.md,.pdf,.docx" onChange={event => void uploadAttachment(event.target.files?.[0])} />
        <div className="chat-composer-tools">
          <button title="上传 TXT、MD、PDF 或 DOCX（最大 10 MB）" disabled={uploading} onClick={() => fileInputRef.current?.click()}><Paperclip size={18} />{uploading ? '上传中' : '文件'}</button>
          <div className="chat-control-wrap"><button className={knowledgeDocumentIds.length ? 'active' : ''} aria-expanded={knowledgeOpen} aria-controls="chat-knowledge-menu" onClick={() => { setKnowledgeOpen(open => !open); setToolsOpen(false); setTaskModeOpen(false) }}><BookOpen size={18} /><span>知识库</span>{knowledgeDocumentIds.length > 0 && <span className="chat-control-count">{knowledgeDocumentIds.length}</span>}<ChevronDown size={13} /></button>{knowledgeOpen && <div id="chat-knowledge-menu" className="chat-control-menu">{knowledgeDocuments.length ? knowledgeDocuments.map(doc => <label key={doc.docId}><input type="checkbox" checked={knowledgeDocumentIds.includes(doc.docId)} onChange={() => setKnowledgeDocumentIds(ids => ids.includes(doc.docId) ? ids.filter(id => id !== doc.docId) : [...ids, doc.docId])} />{doc.title}</label>) : <small>暂无可选择的私有文档</small>}<button onClick={() => setKnowledgeDocumentIds([])}>清除选择</button></div>}</div>
          <button className={webSearchEnabled ? 'active' : ''} disabled={!availableTools.some(tool => tool.networkRequired && tool.available)} title={availableTools.some(tool => tool.networkRequired && tool.available) ? '允许本轮使用联网工具' : '联网搜索未配置'} onClick={() => setWebSearchEnabled(value => !value)}><Globe2 size={18} />联网搜索</button>
          <div className="chat-control-wrap"><button className={enabledTools.length ? 'active' : ''} aria-expanded={toolsOpen} aria-controls="chat-tools-menu" onClick={() => { setToolsOpen(open => !open); setKnowledgeOpen(false); setTaskModeOpen(false) }}><Wrench size={18} /><span>工具</span>{enabledTools.length > 0 && <span className="chat-control-count">{enabledTools.length}</span>}<ChevronDown size={13} /></button>{toolsOpen && <div id="chat-tools-menu" className="chat-control-menu">{!availableTools.length && <small>当前助手暂无可用工具</small>}{availableTools.map(tool => <label key={tool.name} className={!tool.available ? 'disabled' : ''}><input type="checkbox" disabled={!tool.available} checked={enabledTools.includes(tool.name)} onChange={() => setEnabledTools(items => items.includes(tool.name) ? items.filter(item => item !== tool.name) : [...items, tool.name])} />{tool.name}<small>{tool.riskLevel}{tool.networkRequired ? ' · 联网' : ''}</small></label>)}<button onClick={() => setEnabledTools([])}>恢复默认工具</button></div>}</div>
          <div className="chat-task-mode"><button onClick={() => setTaskModeOpen(open => !open)}><ListTodo size={18} />{taskMode === 'CHAT' ? '对话模式' : taskMode === 'PLAN' ? '规划模式' : '执行模式'}<ChevronDown size={13} /></button>{taskModeOpen && <div><button onClick={() => { setTaskMode('CHAT'); setTaskModeOpen(false) }}><strong>对话模式</strong><small>常规问答</small></button><button onClick={() => { setTaskMode('PLAN'); setTaskModeOpen(false) }}><strong>规划模式</strong><small>仅允许只读工具</small></button><button disabled={!plans.some(plan => plan.status === 'APPROVED')} onClick={() => { const approved = plans.find(plan => plan.status === 'APPROVED'); if (approved) { setPlanId(approved.planId); setTaskMode('EXECUTE') }; setTaskModeOpen(false) }}><strong>执行模式</strong><small>使用已审批计划</small></button></div>}</div>
          <span />{loading ? <button className="chat-send stop" onClick={stop}><Square size={15} fill="currentColor" />停止</button> : <button className="chat-send" onClick={send} disabled={!input.trim() || uploading}><Send size={17} />发送</button>}
        </div>
      </div></footer>
    </section>
    {memoryOpen && <MemoryPanel
      readEnabled={memoryReadEnabled} writeEnabled={memoryWriteEnabled} status={memoryStatus}
      candidates={memoryCandidates} memories={savedMemories} archivedMemories={archivedMemories}
      onReadChange={setMemoryReadEnabled} onWriteChange={setMemoryWriteEnabled}
      onClose={() => setMemoryOpen(false)}
      onDecision={async (candidateId, approve) => { await agentApi.decideMemoryCandidate(candidateId, approve); await refreshMemoryPanel() }}
      onMemoryAction={async (memoryId, action, alwaysOn) => {
        if (action === 'pin') await agentApi.pinMemory(memoryId, !alwaysOn)
        if (action === 'archive') await agentApi.archiveMemory(memoryId)
        if (action === 'restore') await agentApi.restoreMemory(memoryId)
        if (action === 'purge') await agentApi.purgeMemory(memoryId, 'deleted from chat memory panel')
        await refreshMemoryPanel()
      }}
    />}
  </div>
}

function MemoryPanel({ readEnabled, writeEnabled, status, candidates, memories, archivedMemories, onReadChange, onWriteChange, onClose, onDecision, onMemoryAction }: {
  readEnabled: boolean; writeEnabled: boolean; status: MemoryStatus | null; candidates: MemoryCandidate[]; memories: SavedMemory[]; archivedMemories: SavedMemory[]
  onReadChange: (value: boolean) => void; onWriteChange: (value: boolean) => void; onClose: () => void; onDecision: (id: string, approve: boolean) => Promise<void>
  onMemoryAction: (id: string, action: 'pin' | 'archive' | 'restore' | 'purge', alwaysOn: boolean) => Promise<void>
}) {
  const [busy, setBusy] = useState('')
  const decide = async (id: string, approve: boolean) => { setBusy(id); try { await onDecision(id, approve) } finally { setBusy('') } }
  const act = async (item: SavedMemory, action: 'pin' | 'archive' | 'restore' | 'purge') => {
    if (action === 'purge' && !window.confirm('永久忘记这条记忆？正文与向量将异步清除，且无法恢复。')) return
    setBusy(item.memoryId)
    try { await onMemoryAction(item.memoryId, action, item.alwaysOn) } finally { setBusy('') }
  }
  return <aside className="chat-memory-panel" aria-label="记忆面板">
    <header><div><BrainCircuit size={18} /><strong>对话记忆</strong></div><button onClick={onClose} title="关闭"><X size={18} /></button></header>
    <p className="memory-panel-copy">记忆只属于当前账号。关闭读取或学习后，将从下一条消息起生效。</p>
    <div className="memory-switches">
      <label><span><strong>使用已有记忆</strong><small>为本次回答注入相关偏好与事实</small></span><input type="checkbox" checked={readEnabled} onChange={event => onReadChange(event.target.checked)} /></label>
      <label><span><strong>允许形成新记忆</strong><small>将明确指令和候选记忆进入审核流程</small></span><input type="checkbox" checked={writeEnabled} onChange={event => onWriteChange(event.target.checked)} /></label>
    </div>
    <section className="memory-context-status"><h3>当前上下文</h3><div><b>{status?.messageCount ?? 0}</b><span>消息事件</span><b>{status?.hasSummary ? '已生成' : '待生成'}</b><span>滚动摘要</span><b>V{status?.workingMemoryVersion ?? 0}</b><span>工作记忆</span></div></section>
    <section className="memory-candidate-list"><h3>待确认记忆 <em>{candidates.length}</em></h3>{candidates.length ? candidates.map(item => <article key={item.candidateId}><strong>{item.title}</strong><p>{item.content}</p><footer><small>{item.sourceKind === 'EXPLICIT' ? '明确请求' : '模型建议'}</small><span><button disabled={busy === item.candidateId} onClick={() => decide(item.candidateId, false)} title="拒绝"><X size={15} /></button><button disabled={busy === item.candidateId} onClick={() => decide(item.candidateId, true)} title="确认"><Check size={15} /></button></span></footer></article>) : <p className="memory-empty">暂无需要你确认的记忆。</p>}</section>
    <section className="memory-saved-list"><h3>已生效记忆 <em>{memories.length}</em></h3>{memories.slice(0, 8).map(item => <article key={item.memoryId}><strong>{item.name}{item.alwaysOn && <i>常驻</i>}</strong><p>{item.description}</p><footer><small>{item.scopeType === 'AGENT' ? '当前助手' : '全局'} · {item.sourceKind === 'EXPLICIT' ? '明确记住' : '推断确认'}</small><span><button disabled={busy === item.memoryId} onClick={() => void act(item, 'pin')} title={item.alwaysOn ? '取消常驻' : '设为常驻'}><Pin size={14} fill={item.alwaysOn ? 'currentColor' : 'none'} /></button><button disabled={busy === item.memoryId} onClick={() => void act(item, 'archive')} title="归档，可恢复"><Archive size={14} /></button><button disabled={busy === item.memoryId} onClick={() => void act(item, 'purge')} title="永久忘记"><Trash2 size={14} /></button></span></footer></article>)}{!memories.length && <p className="memory-empty">还没有可用记忆。</p>}</section>
    {archivedMemories.length > 0 && <section className="memory-saved-list memory-archived-list"><h3>已归档 <em>{archivedMemories.length}</em></h3>{archivedMemories.slice(0, 5).map(item => <article key={item.memoryId}><strong>{item.name}</strong><p>{item.description}</p><footer><small>不参与检索，可恢复</small><span><button disabled={busy === item.memoryId} onClick={() => void act(item, 'restore')} title="恢复记忆"><Check size={14} /></button><button disabled={busy === item.memoryId} onClick={() => void act(item, 'purge')} title="永久忘记"><Trash2 size={14} /></button></span></footer></article>)}</section>}
  </aside>
}

function MessageBubble({ message, streaming }: { message: Message; streaming?: boolean }) { const user = message.role === 'user'; return <article className={`chat-message ${user ? 'user' : 'assistant'}`}><div className="chat-message-avatar">{user ? <User size={17} /> : <MessageCircleMore size={18} />}</div><div className="chat-message-content">{user ? <p>{message.content}</p> : <ReactMarkdown remarkPlugins={[remarkGfm]}>{message.content}</ReactMarkdown>}{streaming && <i className="chat-caret" />}<time>{time(message.timestamp)}</time></div></article> }
