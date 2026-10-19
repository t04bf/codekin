import { createContext } from 'react'
import type { ResumeEditorStore } from './resumeEditorStore'

// Keep context identity stable when editor hooks are refreshed during development.
export const ResumeEditorContext = createContext<ResumeEditorStore | null>(null)
