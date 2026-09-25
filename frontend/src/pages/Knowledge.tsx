import { useEffect, useMemo, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { knowledgeApi, type KnowledgeDocument, type KnowledgeSearchResult } from '../api/knowledge'
import {
  ArrowRight, BookOpen, CheckCircle2, Clock3, Database, FileText, FolderOpen,
  Layers3, Plus, RefreshCw, Search, Sparkles, Trash2, UploadCloud, X,
} from 'lucide-react'

type Notice = { tone: 'success' | 'error'; text: string }
type IngestMode = 'paste' | 'file'

function formatDate(value?: string) {
  if (!value) return '时间未知'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '时间未知'
  return date.toLocaleDateString('zh-CN', { year: 'numeric', month: 'short', day: 'numeric' })
}

function errorMessage(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback
}

function sourceName(path: string) {
  if (!path || path === 'text-input') return '手动录入'
  const parts = path.split(/[\\/]/)
  return parts.at(-1) || path
}

export default function Knowledge() {
  const [documents, setDocuments] = useState<KnowledgeDocument[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [documentFilter, setDocumentFilter] = useState('')
  const [searchQuery, setSearchQuery] = useState('')
  const [searchResults, setSearchResults] = useState<KnowledgeSearchResult | null>(null)
  const [searching, setSearching] = useState(false)
  const [showIngest, setShowIngest] = useState(false)
  const [ingestMode, setIngestMode] = useState<IngestMode>('paste')
  const [ingestTitle, setIngestTitle] = useState('')
  const [ingestContent, setIngestContent] = useState('')
  const [ingestFile, setIngestFile] = useState<File | null>(null)
  const [dragActive, setDragActive] = useState(false)
  const [ingesting, setIngesting] = useState(false)
  const [deleteTarget, setDeleteTarget] = useState<KnowledgeDocument | null>(null)
  const [deleting, setDeleting] = useState(false)
  const [notice, setNotice] = useState<Notice | null>(null)
  const uploadInputRef = useRef<HTMLInputElement>(null)

  const loadDocuments = async (signal?: AbortSignal) => {
    setLoadError('')
    try {
      const response = await knowledgeApi.listDocuments({ signal })
      if (response.code !== 0) throw new Error(response.message || '文档列表加载失败')
      setDocuments(response.data ?? [])
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') return
      setLoadError(errorMessage(error, '知识库暂时无法连接'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    const controller = new AbortController()
    void loadDocuments(controller.signal)
    return () => controller.abort()
  }, [])

  useEffect(() => {
    if (!showIngest && !deleteTarget) return
    const close = (event: KeyboardEvent) => {
      if (event.key !== 'Escape' || ingesting || deleting) return
      setShowIngest(false)
      setDeleteTarget(null)
    }
    window.addEventListener('keydown', close)
    return () => window.removeEventListener('keydown', close)
  }, [showIngest, deleteTarget, ingesting, deleting])

  const totalChunks = useMemo(() => documents.reduce((sum, document) => sum + document.chunkCount, 0), [documents])
  const readyDocuments = useMemo(() => documents.filter(document => document.status === 'ACTIVE' || document.status === 'READY').length, [documents])
  const filteredDocuments = useMemo(() => {
    const query = documentFilter.trim().toLocaleLowerCase()
    if (!query) return documents
    return documents.filter(document => `${document.title} ${document.sourcePath}`.toLocaleLowerCase().includes(query))
  }, [documents, documentFilter])

  const notify = (next: Notice) => {
    setNotice(next)
    window.setTimeout(() => setNotice(current => current === next ? null : current), 4200)
  }

  const handleSearch = async (requestedQuery?: string) => {
    const query = (requestedQuery ?? searchQuery).trim()
    if (!query || !documents.length) return
    setSearchQuery(query)
    setSearching(true)
    try {
      const response = await knowledgeApi.search(query, 6)
      if (response.code !== 0) throw new Error(response.message || '语义检索失败')
      setSearchResults(response.data ?? { chunks: [], totalHits: 0, query, retrievalTimeMs: 0 })
    } catch (error) {
      notify({ tone: 'error', text: errorMessage(error, '语义检索失败，请稍后重试') })
    } finally {
      setSearching(false)
    }
  }

  const resetIngest = () => {
    setIngestTitle('')
    setIngestContent('')
    setIngestFile(null)
    setIngestMode('paste')
    setDragActive(false)
  }

  const closeIngest = () => {
    if (ingesting) return
    setShowIngest(false)
    resetIngest()
  }

  const handleIngest = async () => {
    const pasteReady = ingestMode === 'paste' && ingestTitle.trim() && ingestContent.trim()
    const fileReady = ingestMode === 'file' && ingestFile
    if (!pasteReady && !fileReady) return
    const documentName = ingestMode === 'file' && ingestFile ? ingestFile.name : ingestTitle.trim()
    setIngesting(true)
    try {
      const response = ingestMode === 'file' && ingestFile
        ? await knowledgeApi.uploadDocument(ingestFile)
        : await knowledgeApi.ingestText(ingestTitle.trim(), ingestContent.trim())
      if (response.code !== 0) throw new Error(response.message || '文档入库失败')
      setShowIngest(false)
      resetIngest()
      setLoading(true)
      await loadDocuments()
      notify({ tone: 'success', text: `“${documentName}”已完成分块并加入知识库` })
    } catch (error) {
      notify({ tone: 'error', text: errorMessage(error, '文档入库失败，请检查内容后重试') })
    } finally {
      setIngesting(false)
    }
  }

  const handleDelete = async () => {
    if (!deleteTarget) return
    setDeleting(true)
    try {
      const response = await knowledgeApi.deleteDocument(deleteTarget.docId)
      if (response.code !== 0) throw new Error(response.message || '删除失败')
      setDocuments(current => current.filter(document => document.docId !== deleteTarget.docId))
      notify({ tone: 'success', text: `“${deleteTarget.title}”已从知识库删除` })
      setDeleteTarget(null)
    } catch (error) {
      notify({ tone: 'error', text: errorMessage(error, '删除失败，请稍后重试') })
    } finally {
      setDeleting(false)
    }
  }

  const selectFile = (file?: File) => {
    if (!file) return
    if (!/\.(md|markdown|txt)$/i.test(file.name)) {
      notify({ tone: 'error', text: '当前仅支持 Markdown 或 TXT 文本文件' })
      return
    }
    setIngestFile(file)
  }

  const canIngest = ingestMode === 'paste'
    ? Boolean(ingestTitle.trim() && ingestContent.trim())
    : Boolean(ingestFile)

  return (
    <div className="knowledge-page">
      <header className="knowledge-hero">
        <div className="knowledge-hero-copy">
          <span className="knowledge-eyebrow"><Sparkles size={14} /> Knowledge workspace</span>
          <h1>知识库</h1>
          <p>沉淀可靠资料，让 Agent 在每次对话中获得更准确、更有依据的上下文。</p>
        </div>
        <button className="knowledge-primary" onClick={() => setShowIngest(true)}>
          <Plus size={18} /> 入库新文档
        </button>
      </header>

      <section className="knowledge-stats" aria-label="知识库概览">
        <article><span className="violet"><BookOpen size={19} /></span><div><strong>{documents.length}</strong><small>知识文档</small></div><em>已归档资料</em></article>
        <article><span className="sky"><Layers3 size={19} /></span><div><strong>{totalChunks}</strong><small>内容分块</small></div><em>可检索片段</em></article>
        <article><span className="emerald"><CheckCircle2 size={19} /></span><div><strong>{readyDocuments}</strong><small>索引就绪</small></div><em>{documents.length ? `${Math.round((readyDocuments / documents.length) * 100)}% 可用` : '等待首份资料'}</em></article>
      </section>

      {notice && <div className={`knowledge-notice ${notice.tone}`} role="status"><span>{notice.tone === 'success' ? <CheckCircle2 size={17} /> : <X size={17} />}{notice.text}</span><button aria-label="关闭提示" onClick={() => setNotice(null)}><X size={15} /></button></div>}

      <div className="knowledge-grid">
        <section className="knowledge-panel knowledge-documents">
          <header className="knowledge-panel-header">
            <div><span className="knowledge-panel-icon"><Database size={18} /></span><div><h2>文档空间</h2><p>集中管理已向量化的知识来源</p></div></div>
            <button className="knowledge-icon-button" title="刷新文档列表" aria-label="刷新文档列表" onClick={() => { setLoading(true); void loadDocuments() }}><RefreshCw size={16} className={loading ? 'spinning' : ''} /></button>
          </header>

          {documents.length > 0 && <label className="knowledge-filter"><Search size={16} /><input value={documentFilter} onChange={event => setDocumentFilter(event.target.value)} placeholder="按标题或来源筛选文档" />{documentFilter && <button aria-label="清除筛选" onClick={() => setDocumentFilter('')}><X size={14} /></button>}</label>}

          <div className="knowledge-document-body">
            {loading ? (
              <div className="knowledge-skeleton-list" aria-label="正在加载文档"><i /><i /><i /></div>
            ) : loadError ? (
              <div className="knowledge-empty error"><span><Database size={28} /></span><h3>文档列表加载失败</h3><p>{loadError}</p><button onClick={() => { setLoading(true); void loadDocuments() }}><RefreshCw size={15} />重新加载</button></div>
            ) : documents.length === 0 ? (
              <div className="knowledge-empty"><span><BookOpen size={30} /></span><h3>建立你的第一个知识来源</h3><p>粘贴 Markdown 内容或上传文本文件，系统会自动分块并生成向量索引。</p><button onClick={() => setShowIngest(true)}>开始入库 <ArrowRight size={15} /></button></div>
            ) : filteredDocuments.length === 0 ? (
              <div className="knowledge-empty compact"><span><Search size={25} /></span><h3>没有匹配的文档</h3><p>尝试缩短关键词，或清除当前筛选条件。</p><button onClick={() => setDocumentFilter('')}>清除筛选</button></div>
            ) : (
              <div className="knowledge-document-list">
                {filteredDocuments.map(document => (
                  <article className="knowledge-document-card" key={document.docId}>
                    <span className="knowledge-file-icon"><FileText size={21} /></span>
                    <div className="knowledge-document-copy">
                      <div><h3 title={document.title}>{document.title}</h3><span className={document.status === 'ACTIVE' || document.status === 'READY' ? 'ready' : ''}>{document.status === 'ACTIVE' || document.status === 'READY' ? '索引就绪' : document.status}</span></div>
                      <p><span><Layers3 size={13} />{document.chunkCount} 个分块</span><span title={document.sourcePath}><FolderOpen size={13} />{sourceName(document.sourcePath)}</span><span><Clock3 size={13} />{formatDate(document.createdAt)}</span></p>
                    </div>
                    <button className="knowledge-delete" title={`删除 ${document.title}`} aria-label={`删除 ${document.title}`} onClick={() => setDeleteTarget(document)}><Trash2 size={16} /></button>
                  </article>
                ))}
              </div>
            )}
          </div>
        </section>

        <aside className="knowledge-panel knowledge-search-panel">
          <header className="knowledge-panel-header">
            <div><span className="knowledge-panel-icon search"><Search size={18} /></span><div><h2>语义检索</h2><p>验证 Agent 能否找到正确内容</p></div></div>
          </header>

          <form className="knowledge-search-form" onSubmit={event => { event.preventDefault(); void handleSearch() }}>
            <label><Search size={17} /><input value={searchQuery} onChange={event => setSearchQuery(event.target.value)} placeholder="描述你想查找的信息…" />{searchQuery && <button type="button" aria-label="清除搜索" onClick={() => { setSearchQuery(''); setSearchResults(null) }}><X size={14} /></button>}</label>
            <button type="submit" disabled={searching || !searchQuery.trim() || !documents.length}>{searching ? <RefreshCw size={16} className="spinning" /> : <Sparkles size={16} />}{searching ? '检索中' : '开始检索'}</button>
          </form>

          {!documents.length && !loading && <p className="knowledge-search-hint">入库文档后即可体验语义检索</p>}
          {documents.length > 0 && !searchResults && <div className="knowledge-quick-search"><span>试试这样问</span><div>{['项目架构说明', '部署与启动方式', '接口使用规范'].map(query => <button key={query} onClick={() => void handleSearch(query)}>{query}</button>)}</div></div>}

          <div className="knowledge-search-results">
            {searchResults && <div className="knowledge-result-summary"><span>找到 <strong>{searchResults.totalHits}</strong> 条相关内容</span><small>{searchResults.retrievalTimeMs} ms</small></div>}
            {searchResults?.chunks.length === 0 && <div className="knowledge-empty compact"><span><Search size={24} /></span><h3>没有找到相关内容</h3><p>换一种表达方式，或确认对应资料已经入库。</p></div>}
            {searchResults?.chunks.map((chunk, index) => {
              const score = Math.max(0, Math.min(100, chunk.similarity * 100))
              return <article className="knowledge-result-card" key={chunk.chunkId || `${chunk.documentId}-${index}`}>
                <header><span><FileText size={14} />{chunk.documentName || '知识片段'}</span><em>{score.toFixed(0)}% 匹配</em></header>
                <p>{chunk.content}</p>
                <footer><span>{chunk.source || '知识库'}</span><i><b style={{ width: `${score}%` }} /></i></footer>
              </article>
            })}
          </div>
        </aside>
      </div>

      {showIngest && createPortal(<div className="knowledge-modal-backdrop" role="presentation" onMouseDown={event => { if (event.target === event.currentTarget) closeIngest() }}>
        <section className="knowledge-modal" role="dialog" aria-modal="true" aria-labelledby="knowledge-ingest-title">
          <header><div><span><Plus size={19} /></span><div><h2 id="knowledge-ingest-title">入库新文档</h2><p>系统将自动分块、向量化并建立检索索引</p></div></div><button aria-label="关闭" onClick={closeIngest} disabled={ingesting}><X size={18} /></button></header>
          <div className="knowledge-ingest-tabs" role="tablist"><button className={ingestMode === 'paste' ? 'active' : ''} onClick={() => setIngestMode('paste')}><FileText size={16} />粘贴内容</button><button className={ingestMode === 'file' ? 'active' : ''} onClick={() => setIngestMode('file')}><UploadCloud size={16} />上传文件</button></div>
          {ingestMode === 'paste' ? <div className="knowledge-ingest-fields">
            <label><span>文档标题 <em>必填</em></span><input autoFocus value={ingestTitle} onChange={event => setIngestTitle(event.target.value)} maxLength={160} placeholder="例如：Agent Harness 架构设计" /></label>
            <label><span>文档内容 <em>支持 Markdown</em></span><textarea value={ingestContent} onChange={event => setIngestContent(event.target.value)} rows={11} placeholder={'# 标题\n\n粘贴需要沉淀为知识的正文内容…'} /><small>{ingestContent.length.toLocaleString('zh-CN')} 字</small></label>
          </div> : <div className={`knowledge-dropzone ${dragActive ? 'active' : ''}`} onDragEnter={event => { event.preventDefault(); setDragActive(true) }} onDragOver={event => event.preventDefault()} onDragLeave={() => setDragActive(false)} onDrop={event => { event.preventDefault(); setDragActive(false); selectFile(event.dataTransfer.files[0]) }}>
            <input ref={uploadInputRef} type="file" accept=".md,.markdown,.txt,text/markdown,text/plain" onChange={event => selectFile(event.target.files?.[0])} />
            <span><UploadCloud size={28} /></span>
            {ingestFile ? <><h3>{ingestFile.name}</h3><p>{(ingestFile.size / 1024).toFixed(1)} KB · 已准备上传</p><button onClick={() => setIngestFile(null)}>重新选择</button></> : <><h3>拖放文档到这里</h3><p>支持 Markdown 与 TXT 文本文件</p><button onClick={() => uploadInputRef.current?.click()}>选择文件</button></>}
          </div>}
          <div className="knowledge-ingest-tip"><Sparkles size={15} /><span><strong>获得更好的检索效果</strong>建议使用清晰的标题层级，并让每个段落聚焦一个主题。</span></div>
          <footer><button onClick={closeIngest} disabled={ingesting}>取消</button><button className="primary" onClick={() => void handleIngest()} disabled={ingesting || !canIngest}>{ingesting ? <RefreshCw size={16} className="spinning" /> : <Database size={16} />}{ingesting ? '正在建立索引…' : '确认入库'}</button></footer>
        </section>
      </div>, document.body)}

      {deleteTarget && createPortal(<div className="knowledge-modal-backdrop" role="presentation" onMouseDown={event => { if (event.target === event.currentTarget && !deleting) setDeleteTarget(null) }}>
        <section className="knowledge-delete-dialog" role="dialog" aria-modal="true" aria-labelledby="knowledge-delete-title">
          <span><Trash2 size={22} /></span><h2 id="knowledge-delete-title">删除这份知识文档？</h2><p>“{deleteTarget.title}”及其 <strong>{deleteTarget.chunkCount}</strong> 个向量分块会一并删除，此操作无法撤销。</p><footer><button onClick={() => setDeleteTarget(null)} disabled={deleting}>取消</button><button onClick={() => void handleDelete()} disabled={deleting}>{deleting ? '删除中…' : '确认删除'}</button></footer>
        </section>
      </div>, document.body)}
    </div>
  )
}
