import { useState, useRef, useEffect } from 'react'
import { Send, Bot, User, Sparkles, Heart, Wrench, Square } from 'lucide-react'
import { agentApi } from '../api/agent'
import { api } from '../api/client'

interface Message {
  role: 'user' | 'assistant'
  content: string
  timestamp: Date
  toolCalls?: string[]
}

export default function Chat() {
  const [mode, setMode] = useState<'GENERAL' | 'LOVE'>('GENERAL')
  const [messages, setMessages] = useState<Message[]>([])
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const [streamingContent, setStreamingContent] = useState('')
  const [toolStatus, setToolStatus] = useState<string | null>(null)
  const [chatId, setChatId] = useState(() => `chat_${Date.now()}`)
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const eventSourceRef = useRef<EventSource | null>(null)

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }

  useEffect(() => {
    scrollToBottom()
  }, [messages, streamingContent])

  // Cleanup EventSource on unmount
  useEffect(() => {
    return () => {
      eventSourceRef.current?.close()
    }
  }, [])

  const handleSend = async () => {
    if (!input.trim() || loading) return

    const userMessage: Message = {
      role: 'user',
      content: input.trim(),
      timestamp: new Date(),
    }

    setMessages(prev => [...prev, userMessage])
    setInput('')
    setLoading(true)
    setStreamingContent('')
    setToolStatus(null)

    const token = api.getToken()

    try {
      const es = agentApi.streamChat(
        {
          chatId,
          message: userMessage.content,
          profile: mode,
        },
        {
          onToken: (content) => {
            setStreamingContent(prev => prev + content)
          },
          onToolCall: (toolName) => {
            setToolStatus(`正在调用: ${toolName}`)
          },
          onToolResult: (toolName) => {
            setToolStatus(`工具 ${toolName} 执行完成`)
            // 2 秒后清除工具状态
            setTimeout(() => setToolStatus(null), 2000)
          },
          onDone: () => {
            setStreamingContent(prev => {
              if (prev) {
                const assistantMessage: Message = {
                  role: 'assistant',
                  content: prev,
                  timestamp: new Date(),
                }
                setMessages(msgs => [...msgs, assistantMessage])
              }
              return ''
            })
            setToolStatus(null)
            setLoading(false)
          },
          onError: (message) => {
            const errorMessage: Message = {
              role: 'assistant',
              content: `错误：${message}`,
              timestamp: new Date(),
            }
            setMessages(msgs => [...msgs, errorMessage])
            setStreamingContent('')
            setToolStatus(null)
            setLoading(false)
          },
        },
        token
      )

      eventSourceRef.current = es
    } catch (err: any) {
      const errorMessage: Message = {
        role: 'assistant',
        content: `错误：${err.message || '请求失败'}`,
        timestamp: new Date(),
      }
      setMessages(prev => [...prev, errorMessage])
      setLoading(false)
    }
  }

  const handleStopGeneration = () => {
    eventSourceRef.current?.close()
    eventSourceRef.current = null
    // 将已流式内容保存为消息
    setStreamingContent(prev => {
      if (prev) {
        const assistantMessage: Message = {
          role: 'assistant',
          content: prev + '\n\n[已停止生成]',
          timestamp: new Date(),
        }
        setMessages(msgs => [...msgs, assistantMessage])
      }
      return ''
    })
    setToolStatus(null)
    setLoading(false)
  }

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSend()
    }
  }

  const clearChat = () => {
    eventSourceRef.current?.close()
    // 通知后端清除该会话的记忆（fire-and-forget）
    agentApi.clearMemory(chatId).catch(err =>
      console.warn('Failed to clear memory:', err)
    )
    setMessages([])
    setStreamingContent('')
    setToolStatus(null)
    setLoading(false)
    setChatId(`chat_${Date.now()}`)
  }

  return (
    <div className="h-[calc(100vh-4rem)] flex flex-col">
      {/* Header */}
      <div className="glass rounded-t-xl border-b border-white/10 p-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            {mode === 'LOVE' ? (
              <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-rose-500 to-pink-500 flex items-center justify-center shadow-lg shadow-rose-500/20">
                <Heart size={20} className="text-white" />
              </div>
            ) : (
              <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-sky-500 to-indigo-500 flex items-center justify-center shadow-lg shadow-sky-500/20">
                <Sparkles size={20} className="text-white" />
              </div>
            )}
            <div>
              <h1 className="text-lg font-semibold text-slate-100">
                {mode === 'LOVE' ? '恋爱大师 LoveApp' : '通用助手'}
              </h1>
              <p className="text-xs text-slate-400">
                {mode === 'LOVE' ? '情感咨询 · RAG 知识库' : '通用对话 · 工具调用'}
              </p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <select
              value={mode}
              onChange={e => setMode(e.target.value as any)}
              className="px-3 py-1.5 rounded-lg text-sm bg-white/5 border border-white/10"
            >
              <option value="GENERAL">通用助手</option>
              <option value="LOVE">恋爱大师</option>
            </select>
            <button
              onClick={clearChat}
              className="px-3 py-1.5 rounded-lg text-sm text-slate-400 hover:text-slate-200 hover:bg-white/5 transition-colors"
            >
              清空对话
            </button>
          </div>
        </div>
      </div>

      {/* Messages */}
      <div className="flex-1 overflow-y-auto p-6 space-y-4">
        {messages.length === 0 && !streamingContent && (
          <div className="flex items-center justify-center h-full">
            <div className="text-center">
              <div className="w-20 h-20 rounded-full bg-gradient-to-br from-rose-500/20 to-amber-500/20 flex items-center justify-center mx-auto mb-4">
                {mode === 'LOVE' ? <Heart size={32} className="text-rose-400" /> : <Bot size={32} className="text-sky-400" />}
              </div>
              <h2 className="text-xl font-semibold text-slate-200 mb-2">
                {mode === 'LOVE' ? '有什么感情问题想聊聊吗？' : '有什么我可以帮助你的吗？'}
              </h2>
              <p className="text-sm text-slate-400">
                {mode === 'LOVE' ? '我会用心倾听，给你温暖的建议' : '我可以回答问题、执行任务、调用工具'}
              </p>
            </div>
          </div>
        )}

        {messages.map((msg, idx) => (
          <div
            key={idx}
            className={`flex ${msg.role === 'user' ? 'justify-end' : 'justify-start'}`}
          >
            <div className={`flex gap-3 max-w-[80%] ${msg.role === 'user' ? 'flex-row-reverse' : ''}`}>
              <div className={`w-8 h-8 rounded-full flex items-center justify-center flex-shrink-0 ${
                msg.role === 'user'
                  ? 'bg-gradient-to-br from-sky-500 to-indigo-500'
                  : mode === 'LOVE'
                    ? 'bg-gradient-to-br from-rose-500 to-pink-500'
                    : 'bg-gradient-to-br from-sky-500 to-indigo-500'
              }`}>
                {msg.role === 'user' ? <User size={16} className="text-white" /> : <Bot size={16} className="text-white" />}
              </div>
              <div className={`rounded-2xl px-4 py-3 ${
                msg.role === 'user'
                  ? 'bg-gradient-to-br from-sky-500 to-indigo-500 text-white'
                  : 'glass text-slate-200'
              }`}>
                <div className="text-sm whitespace-pre-wrap">{msg.content}</div>
                <div className={`text-xs mt-2 ${msg.role === 'user' ? 'text-white/60' : 'text-slate-500'}`}>
                  {msg.timestamp.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })}
                </div>
              </div>
            </div>
          </div>
        ))}

        {/* Streaming content */}
        {streamingContent && (
          <div className="flex justify-start">
            <div className="flex gap-3 max-w-[80%]">
              <div className={`w-8 h-8 rounded-full flex items-center justify-center flex-shrink-0 ${
                mode === 'LOVE'
                  ? 'bg-gradient-to-br from-rose-500 to-pink-500'
                  : 'bg-gradient-to-br from-sky-500 to-indigo-500'
              }`}>
                <Bot size={16} className="text-white" />
              </div>
              <div className="glass rounded-2xl px-4 py-3">
                <div className="text-sm whitespace-pre-wrap">{streamingContent}<span className="animate-pulse">|</span></div>
              </div>
            </div>
          </div>
        )}

        {/* Tool status indicator */}
        {toolStatus && (
          <div className="flex justify-start">
            <div className="flex gap-3">
              <div className={`w-8 h-8 rounded-full flex items-center justify-center flex-shrink-0 bg-gradient-to-br from-amber-500 to-orange-500`}>
                <Wrench size={16} className="text-white" />
              </div>
              <div className="glass rounded-2xl px-4 py-3">
                <div className="text-sm text-amber-300 flex items-center gap-2">
                  <div className="w-3 h-3 rounded-full bg-amber-400 animate-pulse" />
                  {toolStatus}
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Loading indicator (before streaming starts) */}
        {loading && !streamingContent && !toolStatus && (
          <div className="flex justify-start">
            <div className="flex gap-3">
              <div className={`w-8 h-8 rounded-full flex items-center justify-center flex-shrink-0 ${
                mode === 'LOVE'
                  ? 'bg-gradient-to-br from-rose-500 to-pink-500'
                  : 'bg-gradient-to-br from-sky-500 to-indigo-500'
              }`}>
                <Bot size={16} className="text-white" />
              </div>
              <div className="glass rounded-2xl px-4 py-3">
                <div className="flex gap-1">
                  <div className="w-2 h-2 rounded-full bg-slate-400 animate-bounce" style={{ animationDelay: '0ms' }} />
                  <div className="w-2 h-2 rounded-full bg-slate-400 animate-bounce" style={{ animationDelay: '150ms' }} />
                  <div className="w-2 h-2 rounded-full bg-slate-400 animate-bounce" style={{ animationDelay: '300ms' }} />
                </div>
              </div>
            </div>
          </div>
        )}

        <div ref={messagesEndRef} />
      </div>

      {/* Input */}
      <div className="glass rounded-b-xl border-t border-white/10 p-4">
        <div className="flex gap-3">
          <textarea
            value={input}
            onChange={e => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="输入消息，按 Enter 发送..."
            rows={1}
            className="flex-1 px-4 py-3 rounded-xl text-sm resize-none"
            style={{ minHeight: '48px', maxHeight: '120px' }}
          />
          {loading ? (
            <button
              onClick={handleStopGeneration}
              className="px-6 py-3 rounded-xl text-sm font-semibold bg-rose-500 hover:bg-rose-600 text-white transition-colors flex items-center gap-2"
            >
              <Square size={14} className="fill-current" />
              停止
            </button>
          ) : (
            <button
              onClick={handleSend}
              disabled={!input.trim()}
              className="px-6 py-3 rounded-xl text-sm font-semibold text-slate-900 btn-gradient disabled:opacity-50 disabled:cursor-not-allowed"
            >
              <Send size={18} />
            </button>
          )}
        </div>
        <div className="flex items-center justify-between mt-2 text-xs text-slate-500">
          <span>按 Enter 发送，Shift+Enter 换行</span>
          <span>会话 ID: {chatId.slice(-8)}</span>
        </div>
      </div>
    </div>
  )
}
