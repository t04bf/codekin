import { useEffect, useState } from 'react'
import { Link, useParams, useNavigate, Navigate } from 'react-router'
import { useQuery } from '@tanstack/react-query'
import { useAuthStore } from '@/features/auth/session'
import { useResumeEditorContext } from '@/store/useResumeStore'
import { resumeApi } from '@/api/resume'
import { resumeKeys, installCloudDetail } from './useCloudResumes'
import { useEditorSync, CloudSaveStatus } from './CloudSaveStatus'
import { ArrowLeft, Download, Undo2, Redo2, PanelLeftClose, PanelLeftOpen } from 'lucide-react'
import { useResumeStore } from '@/store/useResumeStore'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { ResizablePanel, ResizablePanelGroup, ResizableHandle } from '@/components/ui/resizable'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { SidePanel } from '@/components/editor/SidePanel'
import { EditPanel } from '@/components/editor/EditPanel'
import { DEFAULT_TEMPLATES } from '@/config'
import { useTranslations } from '@/i18n/compat/client'
import { ResumePreview } from './ResumePreview'
import { LoadingState } from '@/components/LoadingState'
import { downloadResumePdf } from './pdfExport'
import { toast } from 'sonner'
import { DropdownMenu, DropdownMenuTrigger, DropdownMenuContent, DropdownMenuItem } from '@/components/ui/dropdown-menu'

export default function ResumeEditorPage() {
  const { id = '' } = useParams(), owner = useAuthStore(state => state.userId), draft = useResumeEditorContext()
  const cloud = /^[1-9][0-9]{0,18}$/.test(id)
  const [installed, setInstalled] = useState('')
  const [installError, setInstallError] = useState('')
  const query = useQuery({ queryKey: resumeKeys.detail(owner, id), enabled: cloud && Boolean(owner), retry: false,
    queryFn: ({ signal }) => resumeApi.detail(id, signal), refetchOnMount: 'always', refetchOnWindowFocus: false })
  useEffect(() => {
    if (!query.data || useAuthStore.getState().userId !== owner) return
    try { installCloudDetail(draft, query.data); setInstalled(id); setInstallError('') }
    catch (error) { setInstallError(error instanceof Error ? error.message : '简历版本不支持') }
  }, [query.data, id, owner, draft, cloud])
  useEffect(() => {
    if (!query.data?.photoUrlExpiresAt) return
    const expiresAt = Date.parse(query.data.photoUrlExpiresAt)
    if (!Number.isFinite(expiresAt)) return
    const timer = setTimeout(() => { void query.refetch() }, Math.max(60_000, expiresAt - Date.now() - 60_000))
    return () => clearTimeout(timer)
  }, [query.data?.photoUrlExpiresAt])
  if (!cloud) return <Navigate to="/resume" replace/>
  if (query.isError || installError) return <main className="space-y-4 p-8 text-center"><p role="alert">{installError || query.error?.message || '简历读取失败'}</p><Button onClick={() => { void query.refetch() }}>重新读取</Button><Button asChild variant="outline"><Link to="/resume">返回我的简历</Link></Button></main>
  if (query.isPending || installed !== id) return <LoadingState/>
  return <LoadedEditor key={id + ':' + query.data?.version}/>
}

function LoadedEditor() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const editor = useEditorSync(id)
  const draft = useResumeEditorContext()
  const { resumes, activeResume, setActiveResume, updateResumeTitle, setTemplate, undo, redo, canUndo, canRedo } = useResumeStore()
  const [mobile, setMobile] = useState(false)
  const [tab, setTab] = useState('edit')
  const [showSide, setShowSide] = useState(true)
  const [exporting, setExporting] = useState(false)
  const t = useTranslations('dashboard.templates')
  useEffect(() => { if (id) setActiveResume(id) }, [id, setActiveResume])
  useEffect(() => {
    const media = window.matchMedia('(max-width: 1023px)')
    const update = () => setMobile(media.matches)
    update(); media.addEventListener('change', update)
    return () => media.removeEventListener('change', update)
  }, [])
  useEffect(() => {
    const handle = (event: KeyboardEvent) => {
      if (!(event.metaKey || event.ctrlKey) || (event.target as HTMLElement).closest('input, textarea, select, [contenteditable=true]')) return
      if (event.key.toLowerCase() === 'z') { event.preventDefault(); event.shiftKey ? redo() : undo() }
      else if (event.key.toLowerCase() === 'y') { event.preventDefault(); redo() }
    }
    window.addEventListener('keydown', handle)
    return () => window.removeEventListener('keydown', handle)
  }, [undo, redo])
  useEffect(() => { if (activeResume && activeResume.id === id) document.title = `${activeResume.title} · Codekin` }, [activeResume?.title, activeResume?.id, id])
  const resume = id ? resumes[id] : undefined
  if (!resume) return <main className="space-y-4 p-8 text-center"><h1>未找到这份简历</h1><p className="text-sm text-muted-foreground">请重新读取简历。</p><Button asChild><Link to="/resume">返回我的简历</Link></Button></main>
  if (activeResume?.id !== id) return <LoadingState/>
  const exportPdf = async () => {
    if (exporting) return
    setExporting(true)
    try {
      await editor.sync?.flush()
      if (!editor.sync || editor.sync.status.getState().phase !== 'saved') throw new Error('请先保存简历并解决版本冲突后再导出')
      const version = draft.store.getState().resumes[id]?.remote?.version
      if (!version) throw new Error('简历尚未保存，请重试')
      await downloadResumePdf(id, version)
      toast.success('PDF 已导出')
    } catch (error) { toast.error(error instanceof Error ? error.message : 'PDF 导出失败，请重试') }
    finally { setExporting(false) }
  }
  const exportResume = () => {
    const { remote: _remote, ...backup } = resume
    const url = URL.createObjectURL(new Blob([JSON.stringify({ ...backup, basic: { ...backup.basic, githubKey: '' } }, null, 2)], { type: 'application/json' }))
    const link = document.createElement('a'); link.href = url; link.download = `${resume.title || '简历'}.json`; link.click()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  }
  return <div className="fixed inset-0 flex h-dvh flex-col overflow-hidden bg-background">
    <header className="flex shrink-0 flex-wrap items-center gap-3 border-b bg-white px-4 py-3">
      <Button asChild variant="ghost" size="icon"><Link to="/resume" aria-label="返回我的简历" onClick={async event => {
        event.preventDefault(); await editor.sync?.flush()
        if (editor.sync?.status.getState().phase === 'saved') navigate('/resume')
      }}><ArrowLeft className="size-4"/></Link></Button>
      <Input aria-label="简历名称" value={resume.title} maxLength={100} onChange={e => updateResumeTitle(e.target.value)} onBlur={e => { if (!e.target.value.trim()) updateResumeTitle('未命名简历') }} className="w-40 border-transparent bg-muted/40 sm:w-52"/>
      <div className="hidden sm:block"><CloudSaveStatus id={id} editor={editor}/></div>
      <div className="ml-auto flex flex-wrap items-center gap-2"><Select value={resume.templateId || DEFAULT_TEMPLATES[0].id} onValueChange={setTemplate}><SelectTrigger aria-label="切换简历模板" className="w-36"><SelectValue/></SelectTrigger><SelectContent>{DEFAULT_TEMPLATES.map(template => <SelectItem key={template.id} value={template.id}>{t(`${template.id === 'left-right' ? 'leftRight' : template.id}.name`)}</SelectItem>)}</SelectContent></Select>
        <Button variant="ghost" size="icon" aria-label="撤销简历修改" disabled={!canUndo()} onClick={undo}><Undo2 className="size-4"/></Button><Button variant="ghost" size="icon" aria-label="重做简历修改" disabled={!canRedo()} onClick={redo}><Redo2 className="size-4"/></Button>
        <DropdownMenu><DropdownMenuTrigger asChild><Button variant="outline" disabled={exporting} aria-label="导出简历"><Download className={exporting ? 'size-4 animate-pulse' : 'size-4'}/><span className="hidden sm:inline">{exporting ? '正在导出…' : '导出'}</span></Button></DropdownMenuTrigger><DropdownMenuContent align="end"><DropdownMenuItem onSelect={() => { void exportPdf() }}>导出 PDF</DropdownMenuItem><DropdownMenuItem onSelect={exportResume}>导出 JSON</DropdownMenuItem></DropdownMenuContent></DropdownMenu>
      </div>
      <div className="w-full text-xs text-muted-foreground sm:hidden"><CloudSaveStatus id={id} editor={editor}/></div>

    </header>
    {mobile ? <><Tabs value={tab} onValueChange={setTab} className="border-b bg-white px-4 py-2"><TabsList className="grid w-full grid-cols-3"><TabsTrigger value="layout">模块与排版</TabsTrigger><TabsTrigger value="edit">编辑内容</TabsTrigger><TabsTrigger value="preview">实时预览</TabsTrigger></TabsList></Tabs><div className="min-h-0 flex-1 overflow-auto">{tab === 'layout' ? <SidePanel onSectionCreated={() => setTab('edit')}/> : tab === 'edit' ? <EditPanel/> : <ResumePreview/>}</div></> : <>
      <div className="flex shrink-0 items-center justify-between border-b px-4 py-1.5"><Button size="sm" variant="ghost" onClick={() => setShowSide(v => !v)}>{showSide ? <PanelLeftClose className="size-4"/> : <PanelLeftOpen className="size-4"/>}{showSide ? '收起模块与排版' : '展开模块与排版'}</Button></div>
      <ResizablePanelGroup key={String(showSide)} direction="horizontal" className="min-h-0 flex-1">{showSide && <><ResizablePanel defaultSize={22} minSize={17} maxSize={35}><SidePanel/></ResizablePanel><ResizableHandle withHandle/></>}<ResizablePanel defaultSize={showSide ? 33 : 44} minSize={26}><EditPanel/></ResizablePanel><ResizableHandle withHandle/><ResizablePanel defaultSize={showSide ? 45 : 56} minSize={30}><ResumePreview/></ResizablePanel></ResizablePanelGroup>
    </>}
  </div>
}
