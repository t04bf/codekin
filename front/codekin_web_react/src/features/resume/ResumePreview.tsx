import { useEffect, useId, useRef, useState } from 'react'
import { Minus, Plus } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Switch } from '@/components/ui/switch'
import { Label } from '@/components/ui/label'
import { A4_WIDTH_PX as WIDTH, A4_HEIGHT_PX as HEIGHT } from '@/lib/resumePaper'
import Template from '@/components/templates'
import { DEFAULT_TEMPLATES } from '@/config'
import { useResumeStore } from '@/store/useResumeStore'
import { normalizeFontFamily } from '@/utils/fonts'
import { useAutoOnePage } from '@/hooks/useAutoOnePage'

export function ResumePreview() {
  const resume = useResumeStore(state => state.activeResume)
  const setActiveSection = useResumeStore(state => state.setActiveSection)
  const updateGlobalSettings = useResumeStore(state => state.updateGlobalSettings)
  const onePageId = useId()
  const container = useRef<HTMLDivElement>(null)
  const content = useRef<HTMLDivElement>(null)
  const [width, setWidth] = useState(600)
  const [height, setHeight] = useState(HEIGHT)
  const [zoom, setZoom] = useState(100)
  useEffect(() => {
    if (!container.current || !content.current) return
    const observer = new ResizeObserver(entries => {
      for (const entry of entries) {
        if (entry.target === container.current) setWidth(entry.contentRect.width)
        else setHeight(entry.borderBoxSize[0]?.blockSize || (entry.target as HTMLElement).offsetHeight)
      }
    })
    observer.observe(container.current)
    observer.observe(content.current)
    return () => observer.disconnect()
  }, [])
  const padding = resume?.globalSettings.pagePadding ?? 32
  const { scaleFactor, isScaled, cannotFit } = useAutoOnePage({ contentHeight: height, pagePadding: padding, enabled: Boolean(resume?.globalSettings.autoOnePage) })
  if (!resume) return null
  const scale = Math.min(Math.max((width - 48) / WIDTH, 0.1), 1) * zoom / 100
  const template = DEFAULT_TEMPLATES.find(t => t.id === resume.templateId) ?? DEFAULT_TEMPLATES[0]
  const pageContentHeight = HEIGHT - padding * 2
  const pageBreakCount = (isScaled && !cannotFit) ? 0 : Math.max(0, Math.ceil((height - padding * 2) * scaleFactor / pageContentHeight) - 1)
  return <section aria-label="简历实时预览" className="flex h-full min-h-0 flex-col bg-gray-100">
    <header className="flex shrink-0 flex-wrap items-center justify-between gap-2 border-b bg-white px-4 py-2"><span className="text-sm font-medium">实时预览</span><div className="flex flex-wrap items-center gap-3">
      <div className="flex items-center gap-2"><Switch id={onePageId} checked={Boolean(resume.globalSettings.autoOnePage)} onCheckedChange={enabled => updateGlobalSettings({ autoOnePage: enabled })}/><Label htmlFor={onePageId}>一页纸</Label></div>
      <div className="flex items-center gap-2"><Button variant="ghost" size="icon" aria-label="缩小预览" disabled={zoom <= 50} onClick={() => setZoom(z => z - 10)}><Minus className="size-4"/></Button><button onClick={() => setZoom(100)} className="text-xs" aria-label="重置预览缩放">{zoom}%</button><Button variant="ghost" size="icon" aria-label="放大预览" disabled={zoom >= 150} onClick={() => setZoom(z => z + 10)}><Plus className="size-4"/></Button></div></div></header>
    {cannotFit && <p role="status" className="bg-amber-50 px-4 py-2 text-xs text-amber-800">内容较多，缩小后仍超过一页，可继续调整字号和间距。</p>}
    <div ref={container} className="min-h-0 flex-1 overflow-auto p-6">
      <div className="relative mx-auto overflow-hidden bg-white shadow-lg" style={{ width: WIDTH * scale, height: Math.max(HEIGHT, (height - padding * 2) * scaleFactor + padding * 2) * scale }}>
        <div className="absolute left-0 top-0" style={{ width: WIDTH, transform: `scale(${scale})`, transformOrigin: 'top left' }}>
          <div ref={content} id="resume-preview" className="resume-preview relative bg-white" style={{ width: WIDTH, padding, fontFamily: normalizeFontFamily(resume.globalSettings.fontFamily) }} onClickCapture={event => {
            if ((event.target as HTMLElement).closest('a, button, input')) return
            const section = (event.target as HTMLElement).closest('[data-resume-section-id]')?.getAttribute('data-resume-section-id')
            if (section) setActiveSection(section)
          }}>
            <div style={{ transform: `scale(${scaleFactor})`, transformOrigin: 'top left' }}><Template data={resume} template={template}/></div>
          </div>
          {resume.globalSettings.pageBreakLinesVisible && Array.from({ length: Math.min(pageBreakCount, 20) }, (_, i) => <div key={i} className="pointer-events-none absolute left-0 right-0 border-t border-dashed border-red-400" style={{ top: padding + (i + 1) * pageContentHeight }}><span className="absolute right-1 bottom-1 text-xs text-red-500">第 {i + 1} 页结束</span></div>)}
        </div>
      </div>
    </div>
  </section>
}
