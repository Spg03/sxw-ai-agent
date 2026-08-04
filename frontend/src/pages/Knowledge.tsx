import { useState, useEffect } from 'react'
import { knowledgeApi, type KnowledgeDocument, type KnowledgeSearchResult } from '../api/knowledge'
import { BookOpen, Search, Trash2, Plus, FileText, Database } from 'lucide-react'

export default function Knowledge() {
  const [documents, setDocuments] = useState<KnowledgeDocument[]>([])
  const [loading, setLoading] = useState(true)
  const [searchQuery, setSearchQuery] = useState('')
  const [searchResults, setSearchResults] = useState<KnowledgeSearchResult[] | null>(null)
  const [searching, setSearching] = useState(false)
  const [showIngest, setShowIngest] = useState(false)
  const [ingestTitle, setIngestTitle] = useState('')
  const [ingestContent, setIngestContent] = useState('')
  const [ingesting, setIngesting] = useState(false)

  useEffect(() => {
    const controller = new AbortController()
    loadDocuments(controller.signal)
    return () => controller.abort()
  }, [])

  const loadDocuments = async (signal?: AbortSignal) => {
    try {
      const res = await knowledgeApi.listDocuments({ signal })
      if (res.code === 0) {
        setDocuments(res.data ?? [])
      }
    } catch (err) {
      if (err instanceof DOMException && err.name === 'AbortError') return
      console.error('Failed to load documents', err)
    } finally {
      setLoading(false)
    }
  }

  const handleSearch = async () => {
    if (!searchQuery.trim()) {
      setSearchResults(null)
      return
    }
    setSearching(true)
    try {
      const res = await knowledgeApi.search(searchQuery.trim())
      if (res.code === 0) {
        setSearchResults(res.data ?? [])
      }
    } catch (err) {
      console.error('Failed to search', err)
    } finally {
      setSearching(false)
    }
  }

  const handleIngest = async () => {
    if (!ingestTitle.trim() || !ingestContent.trim()) return
    setIngesting(true)
    try {
      const res = await knowledgeApi.ingestText(ingestTitle.trim(), ingestContent.trim())
      if (res.code === 0) {
        setIngestTitle('')
        setIngestContent('')
        setShowIngest(false)
        loadDocuments()
      }
    } catch (err) {
      console.error('Failed to ingest', err)
    } finally {
      setIngesting(false)
    }
  }

  const handleDelete = async (docId: string) => {
    if (!confirm('确定要删除这个文档吗？将同时删除所有分块和向量数据。')) return
    try {
      await knowledgeApi.deleteDocument(docId)
      setDocuments(prev => prev.filter(d => d.docId !== docId))
    } catch (err) {
      console.error('Failed to delete', err)
    }
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-slate-100">知识库</h1>
          <p className="text-slate-400 mt-2">管理向量知识库文档，支持语义检索</p>
        </div>
        <button
          onClick={() => setShowIngest(!showIngest)}
          className="flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-semibold text-slate-900 btn-gradient"
        >
          <Plus size={18} />
          入库文档
        </button>
      </div>

      {/* Search */}
      <div className="glass rounded-xl p-6">
        <h2 className="text-lg font-semibold text-slate-100 mb-4 flex items-center gap-2">
          <Search size={18} className="text-sky-400" />
          语义搜索
        </h2>
        <div className="flex gap-3">
          <input
            type="text"
            value={searchQuery}
            onChange={e => setSearchQuery(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && handleSearch()}
            placeholder="输入查询内容，测试知识库检索效果..."
            className="flex-1 px-4 py-2.5 rounded-lg text-sm"
          />
          <button
            onClick={handleSearch}
            disabled={searching}
            className="px-6 py-2.5 rounded-lg text-sm font-semibold text-slate-900 btn-gradient disabled:opacity-50"
          >
            {searching ? '搜索中...' : '搜索'}
          </button>
        </div>

        {searchResults && (
          <div className="mt-4 space-y-3">
            {searchResults.length === 0 ? (
              <div className="text-center py-6 text-slate-400 text-sm">未找到相关内容</div>
            ) : (
              searchResults.map((r, idx) => (
                <div key={idx} className="p-4 rounded-lg bg-white/5 border border-white/10">
                  <div className="flex items-center justify-between mb-2">
                    <span className="text-sm font-semibold text-slate-200">{r.title}</span>
                    <span className="px-2 py-0.5 rounded-full bg-sky-500/20 text-xs text-sky-300">
                      相关度 {(r.score * 100).toFixed(1)}%
                    </span>
                  </div>
                  <p className="text-xs text-slate-400 line-clamp-2">{r.content}</p>
                </div>
              ))
            )}
          </div>
        )}
      </div>

      {/* Ingest Form */}
      {showIngest && (
        <div className="glass rounded-xl p-6 animate-fade-in">
          <h2 className="text-lg font-semibold text-slate-100 mb-4">入库新文档</h2>
          <div className="space-y-4">
            <input
              type="text"
              value={ingestTitle}
              onChange={e => setIngestTitle(e.target.value)}
              placeholder="文档标题"
              className="w-full px-4 py-2.5 rounded-lg text-sm"
            />
            <textarea
              value={ingestContent}
              onChange={e => setIngestContent(e.target.value)}
              placeholder="文档内容（Markdown 格式，将自动分块并向量化）..."
              rows={8}
              className="w-full px-4 py-2.5 rounded-lg text-sm resize-none"
            />
            <div className="flex gap-3">
              <button
                onClick={handleIngest}
                disabled={ingesting || !ingestTitle.trim() || !ingestContent.trim()}
                className="px-6 py-2 rounded-lg text-sm font-semibold text-slate-900 btn-gradient disabled:opacity-50"
              >
                {ingesting ? '入库中...' : '入库'}
              </button>
              <button
                onClick={() => setShowIngest(false)}
                className="px-6 py-2 rounded-lg text-sm text-slate-400 hover:text-slate-200 hover:bg-white/5"
              >
                取消
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Documents */}
      <div className="glass rounded-xl p-6">
        <h2 className="text-lg font-semibold text-slate-100 mb-4 flex items-center gap-2">
          <Database size={18} className="text-emerald-400" />
          文档列表
          <span className="text-sm font-normal text-slate-400">({documents.length})</span>
        </h2>
        {loading ? (
          <div className="text-center py-8 text-slate-400">加载中...</div>
        ) : documents.length === 0 ? (
          <div className="text-center py-8">
            <div className="w-16 h-16 rounded-full bg-gradient-to-br from-emerald-500/20 to-teal-500/20 flex items-center justify-center mx-auto mb-3">
              <BookOpen size={28} className="text-emerald-400" />
            </div>
            <p className="text-sm text-slate-400">知识库暂无文档</p>
          </div>
        ) : (
          <div className="space-y-2">
            {documents.map(doc => (
              <div key={doc.docId} className="p-4 rounded-lg bg-white/5 hover:bg-white/10 transition-colors">
                <div className="flex items-start justify-between">
                  <div className="flex items-start gap-3 flex-1 min-w-0">
                    <div className="w-10 h-10 rounded-lg bg-gradient-to-br from-emerald-500 to-teal-500 flex items-center justify-center flex-shrink-0">
                      <FileText size={18} className="text-white" />
                    </div>
                    <div className="flex-1 min-w-0">
                      <h3 className="text-sm font-semibold text-slate-200 truncate">{doc.title}</h3>
                      <div className="flex items-center gap-4 mt-1 text-xs text-slate-500">
                        <span>{doc.chunkCount} 个分块</span>
                        <span className="truncate max-w-[200px]">{doc.sourcePath}</span>
                        <span>{new Date(doc.updatedAt).toLocaleDateString('zh-CN')}</span>
                      </div>
                    </div>
                  </div>
                  <button
                    onClick={() => handleDelete(doc.docId)}
                    className="text-slate-500 hover:text-rose-400 transition-colors flex-shrink-0"
                  >
                    <Trash2 size={16} />
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
