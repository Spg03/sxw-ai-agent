import { useState, useEffect } from 'react'
import { skillsApi, type SkillSummary, type Skill } from '../api/skills'
import { Wrench, Code } from 'lucide-react'

export default function Skills() {
  const [skills, setSkills] = useState<SkillSummary[]>([])
  const [selectedSkill, setSelectedSkill] = useState<Skill | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const controller = new AbortController()
    loadSkills(controller.signal)
    return () => controller.abort()
  }, [])

  const loadSkills = async (signal?: AbortSignal) => {
    try {
      const res = await skillsApi.list({ signal })
      if (res.code === 0) {
        setSkills(res.data)
      }
    } catch (err) {
      if (err instanceof DOMException && err.name === 'AbortError') return
      console.error('Failed to load skills', err)
    } finally {
      setLoading(false)
    }
  }

  const loadSkillDetail = async (name: string) => {
    try {
      const res = await skillsApi.get(name)
      if (res.code === 0) {
        setSelectedSkill(res.data)
      }
    } catch (err) {
      console.error('Failed to load skill', err)
    }
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-3xl font-bold text-slate-100">技能库</h1>
        <p className="text-slate-400 mt-2">Agent 可用的技能和工作流</p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Skills List */}
        <div className="lg:col-span-1 glass rounded-xl p-6">
          <h2 className="text-lg font-semibold text-slate-100 mb-4">技能列表</h2>
          {loading ? (
            <div className="text-center py-8 text-slate-400">加载中...</div>
          ) : skills.length === 0 ? (
            <div className="text-center py-8">
              <div className="w-16 h-16 rounded-full bg-gradient-to-br from-purple-500/20 to-indigo-500/20 flex items-center justify-center mx-auto mb-3">
                <Wrench size={28} className="text-purple-400" />
              </div>
              <p className="text-sm text-slate-400">暂无技能</p>
            </div>
          ) : (
            <div className="space-y-2">
              {skills.map(skill => (
                <div
                  key={skill.name}
                  onClick={() => loadSkillDetail(skill.name)}
                  className={`p-4 rounded-lg cursor-pointer transition-all ${
                    selectedSkill?.name === skill.name
                      ? 'bg-gradient-to-r from-purple-500/20 to-indigo-500/20 border border-purple-500/30'
                      : 'bg-white/5 hover:bg-white/10'
                  }`}
                >
                  <div className="flex items-start gap-3">
                    <div className="w-10 h-10 rounded-lg bg-gradient-to-br from-purple-500 to-indigo-500 flex items-center justify-center flex-shrink-0">
                      <Code size={18} className="text-white" />
                    </div>
                    <div className="flex-1 min-w-0">
                      <h3 className="text-sm font-semibold text-slate-200 mb-1">{skill.name}</h3>
                      <p className="text-xs text-slate-400 line-clamp-2">{skill.description}</p>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Skill Detail */}
        <div className="lg:col-span-2 glass rounded-xl p-6">
          {selectedSkill ? (
            <div>
              <div className="flex items-start gap-4 mb-6">
                <div className="w-14 h-14 rounded-xl bg-gradient-to-br from-purple-500 to-indigo-500 flex items-center justify-center flex-shrink-0 shadow-lg shadow-purple-500/20">
                  <Code size={24} className="text-white" />
                </div>
                <div>
                  <h2 className="text-2xl font-bold text-slate-100">{selectedSkill.name}</h2>
                  <p className="text-sm text-slate-400 mt-1">{selectedSkill.description}</p>
                </div>
              </div>

              <div className="border-t border-white/10 pt-6">
                <h3 className="text-sm font-semibold text-slate-300 mb-3">技能内容</h3>
                <pre className="p-4 rounded-lg bg-black/30 border border-white/10 text-xs text-slate-300 overflow-x-auto whitespace-pre-wrap font-mono">
                  {selectedSkill.body}
                </pre>
              </div>
            </div>
          ) : (
            <div className="flex items-center justify-center h-full">
              <div className="text-center">
                <div className="w-20 h-20 rounded-full bg-gradient-to-br from-purple-500/20 to-indigo-500/20 flex items-center justify-center mx-auto mb-4">
                  <Wrench size={32} className="text-purple-400" />
                </div>
                <h2 className="text-xl font-semibold text-slate-200 mb-2">选择一个技能</h2>
                <p className="text-sm text-slate-400">从左侧列表选择技能查看详情</p>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
