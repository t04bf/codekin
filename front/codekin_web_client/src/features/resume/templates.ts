// Adapted from Magic Resume template configs; React → Vue / schema v1, 2026-10-05.
// Original license: public/licenses/magic-resume.txt. See THIRD_PARTY_NOTICES.md.
import manifest from './data/templates.json'
import blank from './data/blank-content.json'
import preview from './data/preview-content.json'
import type { ResumeContent, ResumeTemplate } from './types'

export const resumeTemplates = manifest as ResumeTemplate[]

/** 未知模板或版本不能静默回退，否则可能改变用户已有简历的版式。 */
export function getResumeTemplate(key: string, version: number): ResumeTemplate {
  const template = resumeTemplates.find(item => item.templateKey === key && item.templateVersion === version)
  if (!template) throw new Error(`不支持的简历模板版本：${key}@${version}`)
  return template
}

export function createBlankResumeContent(): ResumeContent {
  return structuredClone(blank) as ResumeContent
}

/** 预览示例不会作为用户真实履历存入默认模板。 */
export function createPreviewResumeContent(): ResumeContent {
  return structuredClone(preview) as ResumeContent
}
