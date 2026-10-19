import React from 'react'
import { createRoot } from 'react-dom/client'
import { flushSync } from 'react-dom'
import { MotionConfig } from 'framer-motion'
import Template from '@/components/templates'
import { DEFAULT_TEMPLATES } from '@/config'
import { fromResumeDetail } from '@/features/resume/documentAdapter'
import type { ResumeDetail } from '@/api/resume'
import { NextIntlClientProvider } from '@/i18n/compat/client'
import messages from '@/i18n/locales/zh.json'
import { normalizeFontFamily } from '@/utils/fonts'
import { A4_WIDTH_PX, A4_HEIGHT_PX } from '@/lib/resumePaper'
import '@/app/globals.css'
import '@/app/font.css'
import '@/styles/tiptap.scss'

// This entry is bundled into the Java service; it has no login state or network API.
window.renderResume = async (detail: ResumeDetail) => {
  document.title = detail.title
  const resume = fromResumeDetail(detail)
  const template = DEFAULT_TEMPLATES.find(item => item.id === resume.templateId)
  if (!template) throw new Error('不支持的简历模板')
  const padding = resume.globalSettings.pagePadding ?? 32
  const width = A4_WIDTH_PX - padding * 2
  const style = document.createElement('style')
  style.textContent = `
    @page { size: A4; margin: ${padding}px; }
    html, body { margin: 0 !important; padding: 0 !important; width: ${width}px; height: auto !important; background: white !important; }
    body { -webkit-print-color-adjust: exact; print-color-adjust: exact; }
    #pdf-content { width: ${width}px; font-family: ${normalizeFontFamily(resume.globalSettings.fontFamily)}; }
    #pdf-content *, #pdf-content *::before, #pdf-content *::after { animation: none !important; transition: none !important; box-shadow: none !important; }
    #pdf-content .min-h-screen, #pdf-content .min-h-full, #pdf-content .min-h-\\[297mm\\] { min-height: 0 !important; }
    #pdf-content h1, #pdf-content h2, #pdf-content h3, #pdf-content [data-section-title-spacing] { break-after: avoid; }
    #pdf-content p, #pdf-content li { orphans: 2; widows: 2; }
    #pdf-content img { break-inside: avoid; }
  `
  document.head.append(style)
  const container = document.getElementById('root')!
  flushSync(() => createRoot(container).render(
    <MotionConfig reducedMotion="always"><NextIntlClientProvider locale="zh" messages={messages}>
      <div id="pdf-content"><Template data={resume} template={template}/></div>
    </NextIntlClientProvider></MotionConfig>,
  ))
  const family = normalizeFontFamily(resume.globalSettings.fontFamily).split(',')[0]
  await Promise.all([document.fonts.load(`400 16px ${family}`), document.fonts.load(`700 16px ${family}`)])
  await document.fonts.ready
  await Promise.all([...container.querySelectorAll('img')].map(image => image.decode()))
  await new Promise<void>(resolve => requestAnimationFrame(() => requestAnimationFrame(() => resolve())))
  const content = document.getElementById('pdf-content')!
  const height = content.getBoundingClientRect().height
  const available = A4_HEIGHT_PX - padding * 2
  const scale = resume.globalSettings.autoOnePage && height > available ? Math.max(0.9, (available - 1) / height) : 1
  // Reserve one CSS pixel for Chromium page-size rounding to avoid an empty final page.
  // Zoom affects printed flow height. Fixed layout width keeps the same line breaks as preview.
  content.style.width = `${width}px`
  content.style.zoom = String(scale)
  return { height, scale, cannotFit: height * scale > available + 0.5 }
}

declare global {
  interface Window { renderResume: (detail: ResumeDetail) => Promise<{ height: number; scale: number; cannotFit: boolean }> }
}
