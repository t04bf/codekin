// Editing and history actions migrated from Magic Resume; see THIRD_PARTY_NOTICES.md.
import { createStore } from "zustand/vanilla";
import {
  BasicInfo,
  Education,
  Experience,
  GlobalSettings,
  Project,
  CustomItem,
  ResumeData,
  MenuSection,
  Certificate,
} from "../types/resume";
import { DEFAULT_TEMPLATES } from "@/config";
import {
  initialResumeState,
  initialResumeStateEn,
  blankResumeState,
  blankResumeStateEn,
} from "@/config/initialResumeData";
import { generateUUID } from "@/utils/uuid";
import {
  HISTORY_LIMIT,
  type UpdateResumeOptions,
  cloneResume,
  getHistoryKey,
  shouldPushHistoryEntry,
  pushHistory,
  restoreResumeSnapshot,
  clearHistoryGroup,
} from "./resumeHistory";

export interface ResumeStore {
  resumes: Record<string, ResumeData>;
  activeResumeId: string | null;
  activeResume: ResumeData | null;
  history: Record<string, ResumeData[]>;
  future: Record<string, ResumeData[]>;

  createResume: (templateId: string | null, isBlank?: boolean, themeColor?: string) => string;
  deleteResume: (resume: ResumeData) => void;
  updateResume: (
    resumeId: string,
    data: Partial<ResumeData>,
    options?: UpdateResumeOptions
  ) => void;
  setActiveResume: (resumeId: string) => void;
  undo: () => void;
  redo: () => void;
  canUndo: () => boolean;
  canRedo: () => boolean;

  updateResumeTitle: (title: string) => void;
  updateBasicInfo: (data: Partial<BasicInfo>) => void;
  updateEducation: (data: Education) => void;
  updateEducationBatch: (educations: Education[]) => void;
  deleteEducation: (id: string) => void;
  updateExperience: (data: Experience) => void;
  updateExperienceBatch: (experiences: Experience[]) => void;
  deleteExperience: (id: string) => void;
  updateProjects: (project: Project) => void;
  updateProjectsBatch: (projects: Project[]) => void;
  deleteProject: (id: string) => void;
  setDraggingProjectId: (id: string | null) => void;
  updateSkillContent: (skillContent: string) => void;
  updateSelfEvaluationContent: (content: string) => void;
  reorderSections: (newOrder: ResumeData["menuSections"]) => void;
  toggleSectionVisibility: (sectionId: string) => void;
  setActiveSection: (sectionId: string) => void;
  updateMenuSections: (sections: ResumeData["menuSections"]) => void;
  createCustomSection: (section: MenuSection) => void;
  updateCustomData: (sectionId: string, items: CustomItem[]) => void;
  removeCustomData: (sectionId: string) => void;
  removeSection: (sectionId: string) => void;
  addCustomItem: (sectionId: string) => void;
  updateCustomItem: (
    sectionId: string,
    itemId: string,
    updates: Partial<CustomItem>
  ) => void;
  removeCustomItem: (sectionId: string, itemId: string) => void;
  updateGlobalSettings: (settings: Partial<GlobalSettings>) => void;
  setThemeColor: (color: string) => void;
  setTemplate: (templateId: string) => void;
  addResume: (resume: ResumeData) => string;
  addCertificate: (certificate: Certificate) => void;
  updateCertificate: (id: string, updates: Partial<Certificate>) => void;
  updateCertificatesBatch: (certificates: Certificate[]) => void;
  removeCertificate: (id: string) => void;
}

const createDefaultCustomItem = (): CustomItem => ({
  id: generateUUID(), title: "未命名模块", subtitle: "", dateRange: "", description: "", visible: true,
});

/** 组装新建请求所需数据，不保存到浏览器或注册为简历记录。 */
export function createInitialResume(templateId: string | null = null, isBlank = false, themeColor?: string): ResumeData {
  const locale =
    typeof document !== "undefined"
      ? document.cookie
          .split("; ")
          .find((row) => row.startsWith("NEXT_LOCALE="))
          ?.split("=")[1] || "zh"
      : "zh";

  let initialResumeData: typeof initialResumeState;
  if (isBlank) {
    initialResumeData =
      locale === "en" ? blankResumeStateEn : blankResumeState;
  } else {
    initialResumeData =
      locale === "en" ? initialResumeStateEn : initialResumeState;
  }

  const id = generateUUID();
  const template = templateId
    ? DEFAULT_TEMPLATES.find((t) => t.id === templateId)
    : DEFAULT_TEMPLATES[0];

  const newResume: ResumeData = {
    ...structuredClone(initialResumeData),
    id,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    templateId: template?.id,
    globalSettings: { ...initialResumeData.globalSettings,
      themeColor: themeColor || template?.colorScheme.primary,
      sectionSpacing: template?.spacing.sectionGap,
      paragraphSpacing: template?.spacing.itemGap,
      pagePadding: template?.spacing.contentPadding },
    basic: { ...structuredClone(initialResumeData.basic), layout: template?.basic.layout },
    title: `${locale === "en" ? "New Resume" : "新建简历"} ${id.slice(
      0,
      6
    )}`,
  };

  return newResume;
}

export function createResumeEditorStore() {
  const store = createStore<ResumeStore>()(
    (set, get) => ({
      resumes: {},
      activeResumeId: null,
      activeResume: null,
      history: {},
      future: {},

      createResume: (templateId = null, isBlank = false, themeColor) => {
        return get().addResume(createInitialResume(templateId, isBlank, themeColor));
      },

      updateResume: (resumeId, data, options) => {
        set((state) => {
          const resume = state.resumes[resumeId];
          if (!resume) return state;

          const historyKey = getHistoryKey(data, options);
          const shouldPushHistory =
            !!historyKey && shouldPushHistoryEntry(resumeId, historyKey);
          const shouldClearFuture = !!historyKey;
          const updatedResume = {
            ...resume,
            ...data,
            updatedAt: new Date().toISOString(),
          };


          return {
            resumes: {
              ...state.resumes,
              [resumeId]: updatedResume,
            },
            activeResume:
              state.activeResumeId === resumeId
                ? updatedResume
                : state.activeResume,
            history: shouldPushHistory
              ? pushHistory(state.history, resumeId, resume)
              : state.history,
            future: shouldClearFuture
              ? {
                  ...state.future,
                  [resumeId]: [],
                }
              : state.future,
          };
        });
      },

      undo: () => {
        const { activeResumeId } = get();
        if (!activeResumeId) return;

        set((state) => {
          const currentResume = state.resumes[activeResumeId];
          const resumeHistory = state.history[activeResumeId] ?? [];
          const previousResume = resumeHistory[resumeHistory.length - 1];
          if (!currentResume || !previousResume) return state;

          const restoredResume = restoreResumeSnapshot(
            previousResume,
            currentResume
          );
          clearHistoryGroup(activeResumeId);


          return {
            resumes: {
              ...state.resumes,
              [activeResumeId]: restoredResume,
            },
            activeResume: restoredResume,
            history: {
              ...state.history,
              [activeResumeId]: resumeHistory.slice(0, -1),
            },
            future: {
              ...state.future,
              [activeResumeId]: [
                cloneResume(currentResume),
                ...(state.future[activeResumeId] ?? []),
              ].slice(0, HISTORY_LIMIT),
            },
          };
        });
      },

      redo: () => {
        const { activeResumeId } = get();
        if (!activeResumeId) return;

        set((state) => {
          const currentResume = state.resumes[activeResumeId];
          const resumeFuture = state.future[activeResumeId] ?? [];
          const nextResume = resumeFuture[0];
          if (!currentResume || !nextResume) return state;

          const restoredResume = restoreResumeSnapshot(
            nextResume,
            currentResume
          );
          clearHistoryGroup(activeResumeId);


          return {
            resumes: {
              ...state.resumes,
              [activeResumeId]: restoredResume,
            },
            activeResume: restoredResume,
            history: pushHistory(state.history, activeResumeId, currentResume),
            future: {
              ...state.future,
              [activeResumeId]: resumeFuture.slice(1),
            },
          };
        });
      },

      canUndo: () => {
        const { activeResumeId, history } = get();
        return !!activeResumeId && (history[activeResumeId]?.length ?? 0) > 0;
      },

      canRedo: () => {
        const { activeResumeId, future } = get();
        return !!activeResumeId && (future[activeResumeId]?.length ?? 0) > 0;
      },

      updateResumeTitle: (title) => {
        const { activeResumeId } = get();
        if (activeResumeId) {
          get().updateResume(activeResumeId, { title });
        }
      },

      deleteResume: (resume) => {
        const resumeId = resume.id;
        clearHistoryGroup(resumeId);
        set((state) => {
          const { [resumeId]: _, ...rest } = state.resumes;
          const { [resumeId]: __, ...historyRest } = state.history;
          const { [resumeId]: ___, ...futureRest } = state.future;
          return {
            resumes: rest,
            activeResumeId: state.activeResumeId === resumeId ? null : state.activeResumeId,
            activeResume: state.activeResumeId === resumeId ? null : state.activeResume,
            history: historyRest,
            future: futureRest,
          };
        });

      },

      setActiveResume: (resumeId) => {
        const { resumes, activeResume, activeResumeId } = get();
        const nextResume = resumes[resumeId] ?? null;

        if (activeResumeId === resumeId && activeResume === nextResume) {
          return;
        }

        set({ activeResume: nextResume, activeResumeId: resumeId });
      },

      updateBasicInfo: (data) => {
        const { activeResumeId, activeResume } = get();
        if (activeResumeId && activeResume) {
          get().updateResume(activeResumeId, {
            basic: {
              ...activeResume.basic,
              ...data,
            },
          });
        }
      },

      updateEducation: (education) => {
        const { activeResumeId, resumes } = get();
        if (!activeResumeId) return;

        const currentResume = resumes[activeResumeId];
        const newEducation = currentResume.education.some(
          (e) => e.id === education.id
        )
          ? currentResume.education.map((e) =>
              e.id === education.id ? education : e
            )
          : [...currentResume.education, education];

        get().updateResume(activeResumeId, { education: newEducation });
      },

      updateEducationBatch: (educations) => {
        const { activeResumeId } = get();
        if (activeResumeId) {
          get().updateResume(activeResumeId, { education: educations });
        }
      },

      deleteEducation: (id) => {
        const { activeResumeId } = get();
        if (activeResumeId) {
          const resume = get().resumes[activeResumeId];
          const updatedEducation = resume.education.filter((e) => e.id !== id);
          get().updateResume(activeResumeId, { education: updatedEducation });
        }
      },

      updateExperience: (experience) => {
        const { activeResumeId, resumes } = get();
        if (!activeResumeId) return;

        const currentResume = resumes[activeResumeId];
        const newExperience = currentResume.experience.find(
          (e) => e.id === experience.id
        )
          ? currentResume.experience.map((e) =>
              e.id === experience.id ? experience : e
            )
          : [...currentResume.experience, experience];

        get().updateResume(activeResumeId, { experience: newExperience });
      },

      updateExperienceBatch: (experiences: Experience[]) => {
        const { activeResumeId } = get();
        if (activeResumeId) {
          const updateData = { experience: experiences };
          get().updateResume(activeResumeId, updateData);
        }
      },
      deleteExperience: (id) => {
        const { activeResumeId, resumes } = get();
        if (!activeResumeId) return;

        const currentResume = resumes[activeResumeId];
        const updatedExperience = currentResume.experience.filter(
          (e) => e.id !== id
        );

        get().updateResume(activeResumeId, { experience: updatedExperience });
      },

      updateProjects: (project) => {
        const { activeResumeId, resumes } = get();
        if (!activeResumeId) return;
        const currentResume = resumes[activeResumeId];
        const newProjects = currentResume.projects.some(
          (p) => p.id === project.id
        )
          ? currentResume.projects.map((p) =>
              p.id === project.id ? project : p
            )
          : [...currentResume.projects, project];

        get().updateResume(activeResumeId, { projects: newProjects });
      },

      updateProjectsBatch: (projects: Project[]) => {
        const { activeResumeId } = get();
        if (activeResumeId) {
          const updateData = { projects };
          get().updateResume(activeResumeId, updateData);
        }
      },

      deleteProject: (id) => {
        const { activeResumeId } = get();
        if (!activeResumeId) return;
        const currentResume = get().resumes[activeResumeId];
        const updatedProjects = currentResume.projects.filter(
          (p) => p.id !== id
        );
        get().updateResume(activeResumeId, { projects: updatedProjects });
      },

      setDraggingProjectId: (id: string | null) => {
        const { activeResumeId } = get();
        if (activeResumeId) {
          get().updateResume(
            activeResumeId,
            { draggingProjectId: id },
            { recordHistory: false }
          );
        }
      },

      updateSkillContent: (skillContent) => {
        const { activeResumeId } = get();
        if (activeResumeId) {
          get().updateResume(activeResumeId, { skillContent });
        }
      },

      updateSelfEvaluationContent: (selfEvaluationContent) => {
        const { activeResumeId } = get();
        if (activeResumeId) {
          get().updateResume(activeResumeId, { selfEvaluationContent });
        }
      },

      reorderSections: (newOrder) => {
        const { activeResumeId, resumes } = get();
        if (activeResumeId) {
          const currentResume = resumes[activeResumeId];
          const basicInfoSection = currentResume.menuSections.find(
            (section) => section.id === "basic"
          );
          const reorderedSections = [
            basicInfoSection,
            ...newOrder.filter((section) => section.id !== "basic"),
          ].map((section, index) => ({
            ...section,
            order: index,
          }));
          get().updateResume(activeResumeId, {
            menuSections: reorderedSections as MenuSection[],
          });
        }
      },

      toggleSectionVisibility: (sectionId) => {
        const { activeResumeId, resumes } = get();
        if (activeResumeId) {
          const currentResume = resumes[activeResumeId];
          const updatedSections = currentResume.menuSections.map((section) =>
            section.id === sectionId
              ? { ...section, enabled: !section.enabled }
              : section
          );
          get().updateResume(activeResumeId, { menuSections: updatedSections });
        }
      },

      setActiveSection: (sectionId) => {
        const { activeResumeId } = get();
        if (activeResumeId) {
          get().updateResume(
            activeResumeId,
            { activeSection: sectionId },
            { recordHistory: false }
          );
        }
      },

      updateMenuSections: (sections) => {
        const { activeResumeId } = get();
        if (activeResumeId) {
          get().updateResume(activeResumeId, { menuSections: sections });
        }
      },

      removeSection: (sectionId) => {
        const resume = get().activeResume;
        if (!resume || sectionId === 'basic') return;
        clearHistoryGroup(resume.id);
        const sections = resume.menuSections.filter(section => section.id !== sectionId);
        const index = resume.menuSections.findIndex(section => section.id === sectionId);
        const customData = { ...resume.customData };
        if (sectionId.startsWith('custom')) delete customData[sectionId];
        get().updateResume(resume.id, {
          menuSections: sections, customData,
          activeSection: resume.activeSection === sectionId ? (sections[Math.max(0, index - 1)]?.id || 'basic') : resume.activeSection,
        });
      },

      createCustomSection: (section) => {
        const { activeResumeId } = get();
        if (!activeResumeId) return;

        const currentResume = get().resumes[activeResumeId];
        get().updateResume(activeResumeId, {
          menuSections: [...currentResume.menuSections, section],
          customData: {
            ...currentResume.customData,
            [section.id]: [createDefaultCustomItem()],
          },
          activeSection: section.id,
        });
      },

      updateCustomData: (sectionId, items) => {
        const { activeResumeId } = get();
        if (activeResumeId) {
          const currentResume = get().resumes[activeResumeId];
          const updatedCustomData = {
            ...currentResume.customData,
            [sectionId]: items,
          };
          get().updateResume(activeResumeId, { customData: updatedCustomData });
        }
      },

      removeCustomData: (sectionId) => {
        const { activeResumeId } = get();
        if (activeResumeId) {
          const currentResume = get().resumes[activeResumeId];
          const { [sectionId]: _, ...rest } = currentResume.customData;
          get().updateResume(activeResumeId, { customData: rest });
        }
      },

      addCustomItem: (sectionId) => {
        const { activeResumeId } = get();
        if (activeResumeId) {
          const currentResume = get().resumes[activeResumeId];
          const updatedCustomData = {
            ...currentResume.customData,
            [sectionId]: [
              ...(currentResume.customData[sectionId] || []),
              createDefaultCustomItem(),
            ],
          };
          get().updateResume(activeResumeId, { customData: updatedCustomData });
        }
      },

      updateCustomItem: (sectionId, itemId, updates) => {
        const { activeResumeId } = get();
        if (activeResumeId) {
          const currentResume = get().resumes[activeResumeId];
          const updatedCustomData = {
            ...currentResume.customData,
            [sectionId]: currentResume.customData[sectionId].map((item) =>
              item.id === itemId ? { ...item, ...updates } : item
            ),
          };
          get().updateResume(activeResumeId, { customData: updatedCustomData });
        }
      },

      removeCustomItem: (sectionId, itemId) => {
        const { activeResumeId } = get();
        if (activeResumeId) {
          const currentResume = get().resumes[activeResumeId];
          const updatedCustomData = {
            ...currentResume.customData,
            [sectionId]: currentResume.customData[sectionId].filter(
              (item) => item.id !== itemId
            ),
          };
          get().updateResume(activeResumeId, { customData: updatedCustomData });
        }
      },

      addCertificate: (certificate) => {
        const { activeResumeId, resumes } = get();
        if (!activeResumeId) return;

        const currentResume = resumes[activeResumeId];
        const newCertificates = currentResume.certificates.some(
          (c) => c.id === certificate.id
        )
          ? currentResume.certificates.map((c) =>
              c.id === certificate.id ? certificate : c
            )
          : [...currentResume.certificates, certificate];

        get().updateResume(activeResumeId, { certificates: newCertificates });
      },

      updateCertificate: (id, updates) => {
        const { activeResumeId, resumes } = get();
        if (!activeResumeId) return;

        const currentResume = resumes[activeResumeId];
        const newCertificates = currentResume.certificates.map((c) =>
          c.id === id ? { ...c, ...updates } : c
        );

        get().updateResume(activeResumeId, { certificates: newCertificates });
      },

      updateCertificatesBatch: (certificates) => {
        const { activeResumeId } = get();
        if (activeResumeId) {
          get().updateResume(activeResumeId, { certificates });
        }
      },

      removeCertificate: (id) => {
        const { activeResumeId, resumes } = get();
        if (!activeResumeId) return;

        const currentResume = resumes[activeResumeId];
        const updatedCertificates = currentResume.certificates.filter(
          (c) => c.id !== id
        );

        get().updateResume(activeResumeId, { certificates: updatedCertificates });
      },

      updateGlobalSettings: (settings: Partial<GlobalSettings>) => {
        const { activeResumeId, updateResume, activeResume } = get();
        if (activeResumeId) {
          updateResume(activeResumeId, {
            globalSettings: {
              ...activeResume?.globalSettings,
              ...settings,
            },
          });
        }
      },

      setThemeColor: (color) => {
        const { activeResumeId, updateResume } = get();
        if (activeResumeId) {
          updateResume(activeResumeId, {
            globalSettings: {
              ...get().activeResume?.globalSettings,
              themeColor: color,
            },
          });
        }
      },

      setTemplate: (templateId) => {
        const { activeResumeId, resumes } = get();
        if (!activeResumeId) return;

        const template = DEFAULT_TEMPLATES.find((t) => t.id === templateId);
        if (!template) return;

        get().updateResume(activeResumeId, {
          templateId,
          globalSettings: {
            ...resumes[activeResumeId].globalSettings,
            themeColor: template.colorScheme.primary,
            sectionSpacing: template.spacing.sectionGap,
            paragraphSpacing: template.spacing.itemGap,
            pagePadding: template.spacing.contentPadding,
          },
          basic: {
            ...resumes[activeResumeId].basic,
            layout: template.basic.layout,
          },
        });
      },
      addResume: (resume: ResumeData) => {
        clearHistoryGroup(resume.id);
        set((state) => ({
          resumes: {
            ...state.resumes,
            [resume.id]: resume,
          },
          activeResumeId: resume.id,
          activeResume: resume,
          history: {
            ...state.history,
            [resume.id]: [],
          },
          future: {
            ...state.future,
            [resume.id]: [],
          },
        }));

        return resume.id;
      },
    }),
  );
  return { store };
}
export type ResumeEditorStore = ReturnType<typeof createResumeEditorStore>;
