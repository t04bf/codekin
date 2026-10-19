import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { Plus, FileText } from 'lucide-react'
import { toast } from 'sonner'
import { motion } from 'framer-motion'
import { Button } from '@/components/ui/button'
import { useTranslations } from '@/i18n/compat/client'
import type { ResumeData } from '@/types/resume'
import { ResumeCardItem } from './ResumeCardItem'
import { useCloudResumes, useCreateCloudResume, useDeleteCloudResume } from './useCloudResumes'
import { LoadingState } from '@/components/LoadingState'
import { fromResumeDetail } from './documentAdapter'

export default function ResumesPage() {
  const [page, setPage] = useState(1)
  const list = useCloudResumes(page), create = useCreateCloudResume(), remove = useDeleteCloudResume()
  const navigate = useNavigate(), t = useTranslations()
  const busy = create.isPending || remove.isPending
  const createBlank = async () => {
    try { const saved = await create.mutateAsync({ blank: true }); navigate(`/resume/${saved.id}/edit`) }
    catch (error) { toast.error(error instanceof Error ? error.message : '创建失败，请重试') }
  }
  const copy = async (resume: ResumeData) => {
    try { await create.mutateAsync({ sourceId: resume.id }); toast.success('已复制简历') }
    catch (error) { toast.error(error instanceof Error ? error.message : '复制失败，请重试') }
  }
  return <main className="mx-auto max-w-[1600px] space-y-8 p-5 sm:p-8">
    <header className="flex flex-wrap items-center justify-between gap-4">
      <h1 className="text-3xl font-bold">我的简历</h1>
      <div className="flex flex-wrap gap-3"><Button asChild variant="outline"><Link to="/profile">个人主页</Link></Button><Button variant="outline" disabled={busy} onClick={() => { void createBlank() }}>空白简历</Button><Button asChild><Link to="/resume/templates"><Plus className="size-4"/>选择模板</Link></Button></div>
    </header>
    {list.isPending ? <LoadingState variant="section" className="min-h-80"/> : list.isError ? <div role="alert" className="space-y-3"><p>{list.error.message || '简历读取失败'}</p><Button variant="outline" onClick={() => { void list.refetch() }}>重新读取</Button></div> : list.data?.items.length ? <>
      <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 2xl:grid-cols-5">{list.data.items.map((item, index) => {
        const resume = fromResumeDetail(item)
        return <ResumeCardItem key={item.id} id={item.id} resume={resume} index={index} locale="zh" t={t} busy={busy}
          onEdit={id => navigate(`/resume/${id}/edit`)} deleteResume={() => remove.mutateAsync({ id: item.id, version: item.version })} duplicateResume={copy}/>
      })}</div>
    </> : <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="flex min-h-80 flex-col items-center justify-center gap-5 rounded-xl border border-dashed p-8 text-center"><FileText className="size-12 text-muted-foreground"/><h2 className="text-lg font-medium">创建你的第一份简历</h2><Button asChild><Link to="/resume/templates">浏览模板</Link></Button></motion.div>}
    {list.data && (page > 1 || list.data.hasMore) && <nav aria-label="简历分页" className="flex items-center justify-center gap-4"><Button variant="outline" disabled={page === 1 || list.isFetching} onClick={() => setPage(value => value - 1)}>上一页</Button><span>第 {page} 页</span><Button variant="outline" disabled={!list.data.hasMore || list.isFetching} onClick={() => setPage(value => value + 1)}>下一页</Button></nav>}

  </main>
}
