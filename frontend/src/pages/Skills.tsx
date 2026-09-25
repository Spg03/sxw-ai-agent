import { useEffect, useMemo, useState } from 'react'
import { skillsApi, type Skill, type SkillSummary } from '../api/skills'
import { BookOpen, Braces, BrainCircuit, Code2, Database, FileText, Globe2, GraduationCap, Lightbulb, ListChecks, Plus, Search, Settings2, Sparkles, Workflow, X } from 'lucide-react'

type CatalogSkill = { name: string; description: string; icon: typeof Sparkles; color: string; implemented?: boolean }
type Group = { title: string; icon: typeof Sparkles; skills: CatalogSkill[] }

const groups: Group[] = [
  { title: '精选技能', icon: Sparkles, skills: [
    { name: '知识库检索', description: '从你的知识库中精准检索信息，支持语义理解与引用追溯。', icon: BookOpen, color: 'purple' },
    { name: '网页总结', description: '快速总结网页内容，提炼重点、支持多语言和长文处理。', icon: Globe2, color: 'emerald' },
    { name: '代码解释器', description: '解释代码逻辑，生成注释，支持多语言与调试排查。', icon: Code2, color: 'sky' },
    { name: 'RAG 问答', description: '基于检索增强生成的问答，提供可靠、可追溯的答案。', icon: BrainCircuit, color: 'amber' },
  ] },
  { title: '通用效率', icon: Sparkles, skills: [
    { name: '日报生成', description: '根据工作内容自动生成日报，结构清晰，重点突出。', icon: FileText, color: 'sky' },
    { name: '文档结构化提取', description: '从文档中提取结构化信息，支持表格、字段与关键要素。', icon: ListChecks, color: 'violet' },
    { name: '多文档对比', description: '对比多个文档内容差异，高亮变化并生成对比报告。', icon: Workflow, color: 'cyan' },
    { name: '数据分析助手', description: '帮助分析数据、生成图表，并给出业务洞察。', icon: Database, color: 'orange' },
  ] },
  { title: '编程开发', icon: Braces, skills: [
    { name: '代码生成', description: '根据需求生成代码片段，支持多种编程语言。', icon: Code2, color: 'indigo' },
    { name: '单元测试生成', description: '为代码自动生成单元测试，提升覆盖率与质量。', icon: ListChecks, color: 'green' },
    { name: 'SQL 助手', description: '生成优化 SQL 查询，支持数据解释和语法校验。', icon: Database, color: 'blue' },
    { name: 'Git 提交信息生成', description: '根据代码变更自动生成规范的 Commit 信息。', icon: Workflow, color: 'rose' },
  ] },
  { title: '知识处理', icon: BookOpen, skills: [
    { name: '思维导图生成', description: '将主题内容结构化为思维导图，帮助梳理思路。', icon: Workflow, color: 'cyan' },
    { name: '学习计划生成', description: '根据目标与时间生成个性化学习计划与路径。', icon: GraduationCap, color: 'amber' },
    { name: '知识卡片生成', description: '将知识点整理为卡片，便于记忆与复习。', icon: BookOpen, color: 'violet' },
    { name: '产品需求梳理', description: '梳理产品需求，提炼用户价值与功能清单。', icon: Lightbulb, color: 'green' },
  ] },
]
const categories = ['精选', '金融', '法律', '办公协作', '编程', '营销', '医疗', '学术', '创意', '数据分析']

export default function Skills() {
  const [installed, setInstalled] = useState<SkillSummary[]>([])
  const [query, setQuery] = useState('')
  const [category, setCategory] = useState('精选')
  const [selected, setSelected] = useState<Skill | null>(null)
  const [installedOnly, setInstalledOnly] = useState(false)
  useEffect(() => { const controller = new AbortController(); skillsApi.list({ signal: controller.signal }).then(res => { if (res.code === 0) setInstalled(res.data) }).catch(() => undefined); return () => controller.abort() }, [])
  const normalizedQuery = query.trim().toLowerCase()
  const filteredGroups = useMemo(() => installedOnly ? [] : groups.map(group => ({ ...group, skills: group.skills.filter(skill => !normalizedQuery || `${skill.name}${skill.description}`.toLowerCase().includes(normalizedQuery)) })).filter(group => group.skills.length), [installedOnly, normalizedQuery])
  const visibleInstalled = useMemo(() => installed.filter(skill => !normalizedQuery || `${skill.name}${skill.description}`.toLowerCase().includes(normalizedQuery)), [installed, normalizedQuery])
  const openSkill = async (skill: CatalogSkill) => {
    const backend = installed.find(item => item.name === skill.name)
    if (!backend) { setSelected({ name: skill.name, description: skill.description, body: '该能力仍在规划中，尚未注册到后端 SkillRegistry，因此不会展示为可执行技能。正式接入时需同时补齐权限声明、参数 Schema、风险等级与调用审计。' }); return }
    try { const result = await skillsApi.get(backend.name); if (result.code === 0) setSelected(result.data) } catch { setSelected({ name: skill.name, description: skill.description, body: '技能详情暂时不可用，请稍后再试。' }) }
  }
  return <div className="skills-center">
    <header className="skills-topbar"><label className="skills-search"><Search size={18} /><input value={query} onChange={event => setQuery(event.target.value)} placeholder="搜索技能，发现更多能力…" /></label><div><button className={`skills-manage ${installedOnly ? 'active' : ''}`} onClick={() => setInstalledOnly(value => !value)}><Settings2 size={16} />{installedOnly ? '查看全部' : '管理已接入'}</button><button className="skills-create" onClick={() => setSelected({ name: '创建技能', description: '安全边界说明', body: '自定义可执行技能需要签名、权限声明、风险审批和运行沙箱，当前不允许直接创建，避免绕过 Tool Registry 和审计链路。' })}><Plus size={17} />创建</button></div></header>
    <nav className="skills-categories">{categories.map(item => <button key={item} className={category === item ? 'active' : ''} onClick={() => setCategory(item)}>{item}</button>)}</nav>
    <section className="skills-installed"><span><Sparkles size={14} />已接入后端</span><strong>{installed.length} 个</strong></section>
    <main className="skills-groups">{visibleInstalled.length > 0 && <section className="skills-group"><header><span><Sparkles size={17} />已接入技能</span><small>来自后端 SkillRegistry，可被 Agent 真实加载</small></header><div className="skills-grid">{visibleInstalled.map(skill => <SkillCard key={skill.name} skill={{ ...skill, icon: Sparkles, color: 'purple', implemented: true }} installed onOpen={() => void skillsApi.get(skill.name).then(result => result.code === 0 && setSelected(result.data))} />)}</div></section>}{filteredGroups.map(group => <section key={group.title} className="skills-group"><header><span><group.icon size={17} />{group.title}</span><button onClick={() => setQuery('')}>查看全部 ›</button></header><div className="skills-grid">{group.skills.map(skill => <SkillCard key={skill.name} skill={skill} installed={false} onOpen={() => void openSkill(skill)} />)}</div></section>)}{!filteredGroups.length && !visibleInstalled.length && <div className="skills-empty">没有找到匹配的技能，换个关键词试试。</div>}</main>
    {selected && <aside className="skills-detail"><header><div><Sparkles size={18} /><strong>{selected.name}</strong></div><button onClick={() => setSelected(null)}><X size={18} /></button></header><p>{selected.description}</p><pre>{selected.body}</pre></aside>}
  </div>
}

function SkillCard({ skill, installed, onOpen }: { skill: CatalogSkill; installed: boolean; onOpen: () => void }) { const Icon = skill.icon; return <article className={`skill-card ${skill.color}`}><div className="skill-card-icon"><Icon size={25} /></div><div><h3>{skill.name}</h3><p>{skill.description}</p><small>{installed ? '已接入后端' : '规划中'}</small></div><button onClick={onOpen} title={installed ? '查看技能' : '查看规划说明'}><Plus size={16} /></button></article> }
