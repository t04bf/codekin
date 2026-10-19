import { useCallback, useEffect, useId, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { userApi, type MyUser } from '@/api/private'
import { fileApi } from '@/api/file'
import { useAuthStore } from '@/features/auth/session'
import { UserAvatar } from '@/components/UserAvatar'
import { LoadingState } from '@/components/LoadingState'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogTitle, DialogFooter } from '@/components/ui/dialog'
import { errorMessage } from '@/lib/errors'
import { AvatarCropEditor, type AvatarCropSelection } from './AvatarCropEditor'
import { createAvatarFile } from './avatarCrop'

export function AvatarPicker({ me }: { me: MyUser }) {
  const descriptionId = useId()
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const [key, setKey] = useState('')
  const [file, setFile] = useState<File>()
  const [preview, setPreview] = useState('')
  const [phase, setPhase] = useState('')
  const [progress, setProgress] = useState(0)
  const [busy, setBusy] = useState(false)
  const [selection, setSelection] = useState<AvatarCropSelection | null>(null)
  const onSelection = useCallback((value: AvatarCropSelection | null) => setSelection(value), [])
  const input = useRef<HTMLInputElement>(null)
  const lock = useRef(false)
  // Fresh signed display URLs on every open; failures do not block local image uploads.
  const defaults = useQuery({ queryKey: ['default-avatars', me.userId], queryFn: () => userApi.getDefaultAvatars(), enabled: open, staleTime: 0, gcTime: 0 })
  const policy = useQuery({ queryKey: ['upload-policy', 'AVATAR', me.userId], enabled: open, staleTime: Infinity, queryFn: async () => {
    const avatar = (await fileApi.uploadTypes()).find(item => item.code === 'AVATAR')
    if (!avatar) throw new Error('头像上传暂未开放')
    return avatar
  } })
  useEffect(() => {
    if (!file) { setPreview(''); return }
    const url = URL.createObjectURL(file)
    setPreview(url)
    return () => URL.revokeObjectURL(url)
  }, [file])
  const save = useMutation({ mutationFn: async (selection: { file?: File; objectKey: string }) => {
    const checkOwner = () => { if (useAuthStore.getState().userId !== me.userId) throw new Error('登录用户已变更，请重新打开头像设置') }
    checkOwner()
    if (selection.file) {
      setPhase('获取上传凭证')
      const signature = await fileApi.signature('AVATAR', selection.file)
      checkOwner()
      setPhase('上传图片')
      await fileApi.directUpload(signature, selection.file, setProgress)
      checkOwner()
      setPhase('核验图片并保存头像')
      const ready = await fileApi.complete(signature.fileId)
      checkOwner()
      if (ready.fileId !== signature.fileId || ready.typeCode !== 'AVATAR') throw new Error('头像确认结果不一致，请重试')
      await userApi.setAvatar(ready.fileId)
    } else await userApi.setDefaultAvatar(selection.objectKey)
  } })
  function close() {
    if (lock.current) return
    setOpen(false); setFile(undefined); setSelection(null); setKey('')
  }
  function selectFile(candidate?: File) {
    const restriction = policy.data
    if (!candidate || !restriction || lock.current) return
    const extension = candidate.name.split('.').pop()?.toLowerCase() || ''
    if (!restriction.extensions.includes(extension) || !restriction.contentTypes.includes(candidate.type)) { toast.error('请选择支持的图片格式'); return }
    if (candidate.size < 1 || candidate.size > restriction.maxBytes) { toast.error(`图片大小必须在 1 字节至 ${restriction.maxBytes / 1024 / 1024} MiB 之间`); return }
    setSelection(null); setFile(candidate)
  }
  async function confirm() {
    if (lock.current || file && !selection || !file && (!key || key === me.avatarKey || defaults.isFetching)) return
    lock.current = true
    setBusy(true)
    setProgress(0)
    try {
      let cropped: File | undefined
      if (file && selection && policy.data) {
        setPhase('生成裁剪图片')
        cropped = await createAvatarFile(selection.image, selection.area)
        if (!policy.data.contentTypes.includes(cropped.type) || !policy.data.extensions.includes('png')) throw new Error('当前上传策略不支持裁剪图片，请稍后重试')
        if (cropped.size < 1 || cropped.size > policy.data.maxBytes) throw new Error('裁剪后的图片超过上传大小限制，请缩小裁剪区域或更换图片')
      }
      await save.mutateAsync({ file: cropped, objectKey: key })
      if (useAuthStore.getState().userId !== me.userId) return
      toast.success('头像已更新')
      setOpen(false); setFile(undefined); setSelection(null); setKey('')
      void queryClient.invalidateQueries({ queryKey: ['me', me.userId] })
    } catch (cause) { toast.error(errorMessage(cause, '头像未更新，请检查网络或稍后重试')) }
    finally { lock.current = false; setBusy(false); setPhase('') }
  }
  return <>
    <Button className="h-auto p-0" variant="ghost" aria-label="更换头像" onClick={() => { setFile(undefined); setSelection(null); setKey(me.avatarKey || ''); setOpen(true) }}><UserAvatar src={me.avatarUrl} nickname={me.nickname} size={100}/></Button>
    <Dialog open={open} onOpenChange={value => { if (!value) close() }}><DialogContent hideClose={busy} aria-describedby={file ? descriptionId : undefined} className={`codekin-theme w-[min(680px,94vw)] max-w-[680px] max-h-[90dvh] overflow-y-auto ${file ? 'data-[state=open]:animate-none data-[state=closed]:animate-none' : ''}`} onInteractOutside={event => event.preventDefault()}>
      <DialogTitle>{file ? '剪裁头像' : '选择头像'}</DialogTitle>{file && <DialogDescription id={descriptionId}>拖动或缩放图片，调整裁剪区域。</DialogDescription>}
      <input ref={input} className="sr-only" type="file" aria-label="头像图片文件" accept={policy.data?.extensions.map(item => `.${item}`).join(',')} disabled={busy || !policy.data} onChange={event => { selectFile(event.target.files?.[0]); event.target.value = '' }}/>
      {file ? <>
        {preview && policy.data && <AvatarCropEditor key={preview} source={preview} policy={policy.data} disabled={busy} onSelection={onSelection}/>}
        {busy && <div className="space-y-2"><p role="status" className="text-xs text-muted-foreground">{phase}</p><progress className="w-full accent-primary" value={progress} max={100} aria-label="头像上传进度"/></div>}
        <DialogFooter className="gap-2 pt-2"><Button type="button" variant="ghost" disabled={busy} onClick={() => { setFile(undefined); setSelection(null) }}>取消</Button><Button type="button" variant="outline" disabled={busy} onClick={() => input.current?.click()}>重新上传</Button><Button type="button" disabled={busy || !selection} onClick={() => { void confirm() }}>{busy ? '保存中' : '确认'}</Button></DialogFooter>
      </> : <>
        <section aria-label="当前头像" className="flex items-center gap-5 rounded-lg border bg-muted/30 p-5"><UserAvatar src={me.avatarUrl} nickname={me.nickname} size={80}/><div className="space-y-3"><p className="text-sm font-medium">当前头像</p><Button type="button" disabled={busy || !policy.data} onClick={() => input.current?.click()}>上传头像</Button></div></section>
        <section className="space-y-4 border-t pt-5"><h3 className="text-sm font-medium">默认头像</h3>{defaults.isFetching ? <LoadingState variant="section" className="min-h-20"/> : defaults.isError ? <Button type="button" variant="outline" disabled={busy} onClick={() => { void defaults.refetch() }}>重新加载默认头像</Button> : <div className="flex flex-wrap gap-3" aria-label="默认头像">{defaults.data?.map(avatar => <Button type="button" key={avatar.objectKey} variant={key === avatar.objectKey ? 'default' : 'outline'} className="h-[68px] w-[68px] rounded-full p-1" aria-label={avatar.name} aria-pressed={key === avatar.objectKey} disabled={busy} onClick={() => setKey(avatar.objectKey)}><UserAvatar src={avatar.url} nickname={avatar.name} size={58}/></Button>)}</div>}</section>
        {policy.data ? <p className="text-xs text-muted-foreground">{policy.data.extensions.map(item => item.toUpperCase()).join(' / ')}，最大 {policy.data.maxBytes / 1024 / 1024} MiB</p> : policy.isFetching ? <LoadingState variant="inline"/> : <Button type="button" variant="outline" disabled={busy} onClick={() => { void policy.refetch() }}>重新获取上传限制</Button>}
        <DialogFooter className="gap-2"><Button type="button" variant="outline" disabled={busy} onClick={close}>取消</Button><Button type="button" disabled={busy || !key || key === me.avatarKey || defaults.isFetching} onClick={() => { void confirm() }}>{busy ? '保存中' : '确认'}</Button></DialogFooter>
      </>}
    </DialogContent></Dialog>
  </>
}
