/* Generated from contracts/resume/resume-document.schema.json. Do not edit. */

export interface ResumeDocument {
  schemaVersion: 1;
  content: Content;
  layout: Layout;
}
export interface Content {
  basic: Basic;
  /**
   * @maxItems 100
   */
  education: Education[];
  /**
   * @maxItems 100
   */
  experience: Experience[];
  /**
   * @maxItems 100
   */
  projects: Project[];
  /**
   * @maxItems 20
   */
  certificates: Certificate[];
  customData: {
    /**
     * @maxItems 100
     */
    [k: string]: CustomItem[];
  };
  skillContent: string;
  selfEvaluationContent: string;
}
export interface Basic {
  name: string;
  title: string;
  birthDate: string;
  email: string;
  phone: string;
  location: string;
  employementStatus: string;
  photo: string;
  photoFileId: string | null;
  /**
   * @maxItems 50
   */
  customFields: CustomField[];
  githubUseName: string;
  githubContributionsVisible: boolean;
}
export interface CustomField {
  id: string;
  label: string;
  value: string;
  icon?: string;
  custom?: boolean;
  displayLabel?: boolean;
  valueType?: "text" | "link";
  rightSpacing?: number;
  visible?: boolean;
}
export interface Education {
  id: string;
  school: string;
  major: string;
  degree: string;
  startDate: string;
  endDate: string;
  gpa?: string;
  description?: string;
  visible?: boolean;
}
export interface Experience {
  id: string;
  company: string;
  position: string;
  date: string;
  details: string;
  visible?: boolean;
}
export interface Project {
  id: string;
  name: string;
  role: string;
  date: string;
  description: string;
  link?: string;
  linkLabel?: string;
  visible?: boolean;
}
export interface Certificate {
  id: string;
  url: string;
  width: number;
}
export interface CustomItem {
  id: string;
  title: string;
  subtitle: string;
  dateRange: string;
  description: string;
  visible?: boolean;
}
export interface Layout {
  globalSettings: {
    themeColor?: string;
    fontFamily?: string;
    baseFontSize?: number;
    pagePadding?: number;
    paragraphSpacing?: number;
    lineHeight?: number;
    sectionSpacing?: number;
    sectionTitleSpacing?: number;
    headerSize?: number;
    subheaderSize?: number;
    useIconMode?: boolean;
    centerSubtitle?: boolean;
    flexibleHeaderLayout?: boolean;
    autoOnePage?: boolean;
    pageBreakLinesVisible?: boolean;
  };
  /**
   * @maxItems 50
   */
  menuSections: MenuSection[];
  basic: {
    photoConfig: PhotoConfig;
    layout?: "left" | "center" | "right";
    icons: {
      [k: string]: string;
    };
    /**
     * @maxItems 20
     */
    fieldOrder?: FieldOrder[];
  };
}
export interface MenuSection {
  id: string;
  title: string;
  icon: string;
  enabled: boolean;
  order: number;
}
export interface PhotoConfig {
  width: number;
  height: number;
  aspectRatio: "1:1" | "4:3" | "3:4" | "16:9" | "custom";
  borderRadius: "none" | "medium" | "full" | "custom";
  customBorderRadius: number;
  visible?: boolean;
}
export interface FieldOrder {
  id: string;
  key: "name" | "title" | "birthDate" | "email" | "phone" | "location" | "employementStatus";
  label: string;
  type?: "date" | "textarea" | "text" | "editor";
  visible: boolean;
  custom?: boolean;
}
