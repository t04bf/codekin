import { useEffect, useState, useRef } from 'react'
import { useStore } from 'zustand'
import { createStore } from 'zustand/vanilla'
import { useQueryClient } from '@tanstack/react-query'
import { Button } from '@/components/ui/button'
import { AlertDialog, AlertDialogTrigger, AlertDialogContent, AlertDialogHeader, AlertDialogTitle, AlertDialogDescription, AlertDialogFooter, AlertDialogCancel, AlertDialogAction } from '@/components/ui/alert-dialog'
import { useResumeEditorContext } from '@/store/useResumeStore'
import { useAuthStore } from '@/features/auth/session'
import { resumeApi } from '@/api/resume'
import { createResumeSync, type SyncStatus } from './resumeSync'
import { resumeKeys, installCloudDetail, useResumeTemplates } from './useCloudResumes'

export function useEditorSync(id: string) {
  const draft = useResumeEditorContext(), owner = useAuthStore(state => state.userId), client = useQueryClient()
  const templates = useResumeTemplates()
  const templatesRef = useRef(templates.data)
  templatesRef.current = templates.data
  const [sync, setSync] = useState<ReturnType<typeof createResumeSync>>()
  useEffect(() => {
    const controller = createResumeSync(draft, id, owner, () => templatesRef.current || [], saved => {
      if (useAuthStore.getState().userId === owner) void client.invalidateQueries({ queryKey: resumeKeys.lists(owner) })
    })
    setSync(controller)
    return () => controller.stop()
  }, [draft, id, owner, client])
  const [fallback] = useState(() => createStore<SyncStatus>(() => ({ phase: 'saved', message: '' })))
  const state = useStore(sync?.status || fallback)
  useEffect(() => {
    if (state.phase === 'saved') return
    const warn = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = '' }
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [state.phase])
  return { sync, state }
}

export function CloudSaveStatus({ id, editor }: { id: string; editor: ReturnType<typeof useEditorSync> }) {
  const { state, sync } = editor
  const draft = useResumeEditorContext(), owner = useAuthStore(state => state.userId), client = useQueryClient()
  const [reading, setReading] = useState(false), [error, setError] = useState('')
  const [dialog, setDialog] = useState(false)
  const reload = async () => {
    setReading(true); setError('')
    try {
      const latest = await resumeApi.detail(id)
      if (useAuthStore.getState().userId !== owner) return
      sync?.stop()
      installCloudDetail(draft, latest, true)
      client.setQueryData(resumeKeys.detail(owner, id), latest)
      setDialog(false)
    } catch (exception) { setError(exception instanceof Error ? exception.message : '读取失败，请重试') }
    finally { setReading(false) }
  }
  return <div className="flex flex-wrap items-center gap-2 text-xs">
    <span role={state.phase === 'error' || state.phase === 'conflict' ? 'alert' : 'status'} className={state.phase === 'error' || state.phase === 'conflict' ? 'text-destructive' : 'text-muted-foreground'}>
      {state.message || ({ saved: '已保存', dirty: '待保存', saving: '正在保存…', error: '保存失败', conflict: '简历已在其他页面更新' } as const)[state.phase]}
    </span>
    {state.phase === 'error' && <Button variant="ghost" size="sm" onClick={() => { void sync?.flush() }}>重试保存</Button>}
    {state.phase === 'conflict' && <AlertDialog open={dialog} onOpenChange={setDialog}><AlertDialogTrigger asChild><Button variant="ghost" size="sm">读取最新版本</Button></AlertDialogTrigger><AlertDialogContent><AlertDialogHeader><AlertDialogTitle>读取最新版本？</AlertDialogTitle><AlertDialogDescription>将替换当前未保存的修改，建议先导出简历。</AlertDialogDescription></AlertDialogHeader>{error && <p role="alert" className="text-sm text-destructive">{error}</p>}<AlertDialogFooter><AlertDialogCancel disabled={reading}>取消</AlertDialogCancel><AlertDialogAction disabled={reading} onClick={event => { event.preventDefault(); void reload() }}>{reading ? '正在读取…' : '确认读取'}</AlertDialogAction></AlertDialogFooter></AlertDialogContent></AlertDialog>}
  </div>
}
