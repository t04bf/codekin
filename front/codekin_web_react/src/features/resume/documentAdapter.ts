import type { ResumeDocument } from '@/api/resume-document.generated'
import type { ResumeDetail } from '@/api/resume'
import type { ResumeData } from '@/types/resume'

/** 仅持久化业务数据；访问凭证、选中状态和拖拽状态不进入接口。 */
export function toResumeDocument(resume: ResumeData): ResumeDocument {
  const { photoConfig, icons, fieldOrder, layout, githubKey: _secret, photoFileId, ...basic } = resume.basic
  if (!photoFileId && /^(data:|blob:)/i.test(basic.photo)) {
    throw new Error('请重新上传证件照后保存')
  }
  return {
    schemaVersion: 1,
    content: {
      basic: { ...basic, photo: photoFileId ? '' : basic.photo, photoFileId: photoFileId || null },
      education: resume.education, experience: resume.experience, projects: resume.projects,
      certificates: resume.certificates, customData: resume.customData,
      skillContent: resume.skillContent, selfEvaluationContent: resume.selfEvaluationContent,
    },
    layout: {
      globalSettings: resume.globalSettings, menuSections: resume.menuSections,
      basic: {
        photoConfig, icons, ...(layout ? { layout } : {}),
        // 编辑器的 key 类型宽于可排序的资料字段；协议由生成的类型检查。
        ...(fieldOrder ? { fieldOrder: fieldOrder as NonNullable<ResumeDocument['layout']['basic']['fieldOrder']> } : {}),
      },
    },
  }
}

/** 上传文件的展示 URL 由文件读取流程提供，不把短期地址当成持久化字段。 */
export function fromResumeDetail(saved: ResumeDetail, photoUrl = saved.photoUrl || ''): ResumeData {
  if (saved.schemaVersion !== 1 || !saved.templateKey || saved.templateVersion !== 1) {
    throw new Error('简历版本暂不支持，请更新页面')
  }
  return {
    id: saved.id, title: saved.title, createdAt: saved.createdAt, updatedAt: saved.updatedAt,
    templateId: saved.templateKey,
    basic: { ...saved.content.basic, ...saved.layout.basic, githubKey: '',
      photo: saved.content.basic.photoFileId ? photoUrl : saved.content.basic.photo },
    education: saved.content.education, experience: saved.content.experience,
    projects: saved.content.projects.map(item => ({ ...item, visible: item.visible ?? true })),
    certificates: saved.content.certificates,
    customData: Object.fromEntries(Object.entries(saved.content.customData)
      .map(([id, items]) => [id, items.map(item => ({ ...item, visible: item.visible ?? true }))])),
    skillContent: saved.content.skillContent, selfEvaluationContent: saved.content.selfEvaluationContent,
    menuSections: saved.layout.menuSections, globalSettings: saved.layout.globalSettings,
    activeSection: 'basic', draggingProjectId: null,
  }
}
