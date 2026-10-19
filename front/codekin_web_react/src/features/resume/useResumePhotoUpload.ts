import { useRef, useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { fileApi } from '@/api/file'
import { useAuthStore } from '@/features/auth/session'
import { compressImage } from '@/utils/imageUtils'

const TYPE_CODE = 'RESUME_PHOTO'

/** 直传确认后保留文件 ID，预览地址只供本地编辑使用。 */
export function useResumePhotoUpload(open: boolean) {
  const userId = useAuthStore(state => state.userId)
  const [phase, setPhase] = useState('')
  const [progress, setProgress] = useState(0)
  const lock = useRef(false)
  const policy = useQuery({
    queryKey: ['upload-policy', TYPE_CODE, userId],
    enabled: open && Boolean(userId),
    staleTime: Infinity,
    queryFn: async () => {
      const restriction = (await fileApi.uploadTypes()).find(item => item.code === TYPE_CODE)
      if (!restriction) throw new Error('证件照上传暂未开放')
      return restriction
    },
  })
  const mutation = useMutation({
    mutationFn: async (file: File) => {
      const owner = useAuthStore.getState().userId
      if (!owner) throw new Error('请登录后上传证件照')
      const restriction = policy.data
      if (!restriction) throw new Error('请先获取证件照上传限制')
      const extension = file.name.split('.').pop()?.toLowerCase() || ''
      if (!restriction.extensions.includes(extension) || !restriction.contentTypes.includes(file.type)) {
        throw new Error('请选择支持的图片格式')
      }
      if (file.size < 1 || file.size > restriction.maxBytes) {
        throw new Error(`图片大小必须在 1 字节至 ${restriction.maxBytes / 1024 / 1024} MiB 之间`)
      }
      setPhase('准备照片预览')
      // 仅为当前编辑内容生成内存预览；签名声明及直传正文必须使用同一个原始 File。
      const preview = await compressImage(file, 1024, 1024, 0.8)
      if (useAuthStore.getState().userId !== owner) throw new Error('账号已变更，请重新操作')
      setPhase('获取上传凭证')
      const signature = await fileApi.signature(TYPE_CODE, file)
      if (useAuthStore.getState().userId !== owner) throw new Error('账号已变更，请重新操作')
      setPhase('上传证件照')
      await fileApi.directUpload(signature, file, setProgress)
      if (useAuthStore.getState().userId !== owner) throw new Error('账号已变更，请重新操作')
      setPhase('核验证件照')
      const ready = await fileApi.complete(signature.fileId)
      if (useAuthStore.getState().userId !== owner) throw new Error('账号已变更，请重新操作')
      if (ready.fileId !== signature.fileId || ready.typeCode !== TYPE_CODE) {
        throw new Error('证件照确认结果不正确，请重新上传')
      }
      return { preview, fileId: ready.fileId }
    },
  })
  async function upload(file: File) {
    if (lock.current) return
    lock.current = true
    setProgress(0)
    try { return await mutation.mutateAsync(file) }
    finally { lock.current = false; setPhase('') }
  }
  return { userId, policy, upload, phase, progress, isUploading: mutation.isPending }
}
