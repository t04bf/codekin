import { useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { useStore } from 'zustand'
import { useAuthStore } from '@/features/auth/session'
import { createResumeEditorStore, type ResumeStore } from './resumeEditorStore'
import { ResumeEditorContext } from './resumeEditorContext'
import { clearHistoryGroup } from './resumeHistory'
import { LoadingState } from '@/components/LoadingState'

export function ResumeEditorProvider({ children }: { children: ReactNode }) {
  const userId = useAuthStore(state => state.userId)
  const [authReady, setAuthReady] = useState(false)
  // Account changes replace the entire in-memory editor state.
  const editor = useMemo(() => createResumeEditorStore(), [userId])
  useEffect(() => {
    let active = true
    const session = useAuthStore.getState()
    void (session.refreshToken && !session.userId ? session.ensureAccessToken() : Promise.resolve()).finally(() => {
      if (active) setAuthReady(true)
    })
    return () => { active = false }
  }, [])
  useEffect(() => () => { Object.keys(editor.store.getState().resumes).forEach(clearHistoryGroup) }, [editor])
  return <ResumeEditorContext.Provider value={editor}>{authReady ? children : <LoadingState/>}</ResumeEditorContext.Provider>
}

export function useResumeEditorContext() {
  const editor = useContext(ResumeEditorContext)
  if (!editor) throw new Error('ResumeEditorProvider is required')
  return editor
}

export function useResumeStore<T = ResumeStore>(selector: (state: ResumeStore) => T = state => state as T): T {
  return useStore(useResumeEditorContext().store, selector)
}
