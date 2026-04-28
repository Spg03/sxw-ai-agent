---
name: note-workflow
description: 整理、归档、检索 Markdown 笔记的标准工作流；调用 createNote/listNotes/searchNotes 等工具
---

# Note Workflow 操作手册

## 何时使用本 skill
当用户的请求涉及笔记的写入、查找、汇总、归档时启用，例如：
- "把这段会议要点存成笔记"
- "找一下我之前写过的关于 XX 的笔记"
- "把这周所有日报总结成一份周报存档"

## 可用工具
本 skill 依赖以下已注册工具（属于 NoteSkill）：
- `createNote(title, content)` — 新建或覆盖笔记
- `appendNote(title, content)` — 追加内容到已有笔记
- `readNote(title)` — 读全文
- `listNotes()` — 列出全部笔记标题
- `searchNotes(keyword)` — 关键词检索（标题 + 正文）
- `deleteNote(title)` — 删除

## 标准流程

### 写入类任务
1. 用 `listNotes()` 查重，避免无意覆盖
2. 标题命名规范：`[类型]-[主题]-[日期]`，例如 `meeting-llm调研-20260427`
3. 正文使用标准 Markdown，至少包含：标题、要点列表、必要时附结论
4. 调用 `createNote` 写入；若已存在同名且用户未要求覆盖，改用 `appendNote` 追加

### 检索类任务
1. 先 `searchNotes(keyword)` 拿到候选列表
2. 对最相关的 1-3 条调 `readNote` 取全文
3. 引用时**必须**带上原笔记标题，让用户能追溯来源

### 汇总类任务（如生成周报）
1. `listNotes()` 拿全清单 → 按日期前缀过滤本周
2. 对筛出的每篇 `readNote` 取正文
3. 用 LLM 自身能力做合并 / 提炼
4. 用 `createNote` 写入新文件，标题形如 `weekly-202617`

## 风格约束
- 写入笔记前，**先把要写的内容用 Markdown 在回复中预览给用户**，让其确认 / 修改后再落盘
- 删除操作必须二次确认
- 笔记标题禁用特殊字符（工具内部已限制为字母数字中文 `._- 空格`）

## 失败处理
若工具返回以 `failed:` 开头的字符串，直接把原因转述给用户并停止该步骤，不要重试超过 1 次。
