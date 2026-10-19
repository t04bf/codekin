/** 简历 schema v1；持久化 ID 使用字符串，预览访问链接单独传入。 */
export type TemplateKey = 'classic' | 'modern' | 'left-right' | 'timeline' | 'minimalist' | 'elegant' | 'creative' | 'editorial' | 'swiss'
export interface RichNode {
  type: string
  text?: string
  attrs?: { href?: string; level?: number; textAlign?: string; color?: string; start?: number }
  marks?: { type: string; attrs?: { href?: string; color?: string } }[]
  content?: RichNode[]
}
export interface CustomField {
  id: string
  label: string
  type: 'TEXT' | 'LINK' | 'DATE' | 'DATE_RANGE' | 'RICH_TEXT'
  value?: string | RichNode | { startDate?: string; endDate?: string; ongoing?: boolean }
  visible?: boolean
  showLabel?: boolean
}
export interface ResumeItem {
  id: string
  visible?: boolean
  name?: string
  company?: string
  school?: string
  title?: string
  position?: string
  role?: string
  major?: string
  degree?: string
  gpa?: string
  subtitle?: string
  startDate?: string
  endDate?: string | null
  ongoing?: boolean
  dateRange?: string
  description?: RichNode
  link?: string
  linkLabel?: string
  fileId?: string
  imageWidthPercent?: number
  customFields?: CustomField[]
  values?: Record<string, CustomField['value']>
}
export interface ResumeSection {
  id: string
  type: 'EDUCATION' | 'EXPERIENCE' | 'PROJECT' | 'RICH_TEXT' | 'CERTIFICATE' | 'CUSTOM'
  title: string
  visible: boolean
  items: ResumeItem[]
  content?: RichNode
  customFields?: CustomField[]
  fieldDefinitions?: Omit<CustomField, 'value'>[]
}
export interface ResumeContent {
  basic: {
    name: string
    title: string
    email: string
    phone: string
    location: string
    birthDate: string
    employmentStatus: string
    photoFileId: string | null
    visible: boolean
    fieldOrder: { key: 'name' | 'title' | 'email' | 'phone' | 'location' | 'birthDate' | 'employmentStatus'; visible: boolean }[]
    customFields: CustomField[]
  }
  sections: ResumeSection[]
}
export interface ResumeLayout {
  themeColor: string
  textColor: string
  secondaryColor: string
  backgroundColor: string
  fontKey: 'sans' | 'serif'
  baseFontSize: number
  lineHeight: number
  pagePadding: number
  sectionSpacing: number
  paragraphSpacing: number
  sectionTitleSpacing: number
  headerSize: number
  subheaderSize: number
  basicAlignment: 'left' | 'center' | 'right'
  photo: { visible: boolean; width: number; height: number; borderRadius: number }
  columns: { id: 'main' | 'sidebar'; sectionIds: string[] }[]
}
export interface ResumeTemplate {
  id: string
  templateKey: TemplateKey
  templateVersion: number
  schemaVersion: number
  name: string
  description: string
  status: 'PUBLISHED'
  sortOrder: number
  defaultLayout: ResumeLayout
}
