import type { ResumeContent, ResumeLayout } from './types'

/** 限定链接协议；不将用户内容拼接为 HTML。 */
export function safeResumeLink(value?: string): string | undefined {
  if (!value || /[\u0000-\u0020\u007f]/.test(value.trim())) return undefined
  try {
    const url = new URL(value.trim())
    return ['https:', 'http:', 'mailto:', 'tel:'].includes(url.protocol) ? url.href : undefined
  } catch { return undefined }
}

/** 图片只接受受控资源路径或已解析的 HTTP(S) URL，不接受文件ID或任意 data URL。 */
export function safeResumeImage(value?: string): string | undefined {
  if (value?.startsWith('/') && !value.startsWith('//') && !value.includes('\\')) return value
  const url = safeResumeLink(value)
  return url && /^https?:/.test(url) ? url : undefined
}

export function dateRange(value: { startDate?: string; endDate?: string | null; ongoing?: boolean; dateRange?: string }): string {
  if (value.dateRange) return value.dateRange
  return [value.startDate, value.ongoing ? '至今' : value.endDate].filter(Boolean).join(' — ')
}

/** 按配置排列；新增自定义模块未出现在旧布局中时追加到主栏，避免切模板后丢展示。 */
export function arrangeSections(content: ResumeContent, layout: ResumeLayout) {
  const visible = content.sections.filter(section => section.visible)
  const byId = new Map(visible.map(section => [section.id, section]))
  const used = new Set<string>()
  const result: Record<'main' | 'sidebar', typeof visible> = { main: [], sidebar: [] }
  for (const column of layout.columns) {
    for (const id of column.sectionIds) {
      const section = byId.get(id)
      if (section && !used.has(id)) { result[column.id].push(section); used.add(id) }
    }
  }
  result.main.push(...visible.filter(section => !used.has(section.id)))
  return result
}
