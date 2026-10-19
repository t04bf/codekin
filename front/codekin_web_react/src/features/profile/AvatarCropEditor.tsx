import { useCallback, useEffect, useRef, useState } from 'react'
import Cropper, { type Area } from 'react-easy-crop'
import type { UploadTypePolicy } from '@/api/file'
import { drawAvatarCrop } from './avatarCrop'

export interface AvatarCropSelection { image: HTMLImageElement; area: Area }

export function AvatarCropEditor({ source, policy, disabled, onSelection }: {
  source: string
  policy: UploadTypePolicy
  disabled: boolean
  onSelection: (selection: AvatarCropSelection | null) => void
}) {
  const [crop, setCrop] = useState({ x: 0, y: 0 })
  const [zoom, setZoom] = useState(1)
  const [image, setImage] = useState<HTMLImageElement>()
  const [area, setArea] = useState<Area>()
  const onCropAreaChange = useCallback((_percentages: Area, pixels: Area) => setArea(pixels), [])
  const [error, setError] = useState('')
  const preview = useRef<HTMLCanvasElement>(null)
  useEffect(() => {
    const loaded = new Image()
    let active = true
    loaded.onload = () => {
      if (!active) return
      if (loaded.naturalWidth > policy.maxWidth || loaded.naturalHeight > policy.maxHeight || loaded.naturalWidth * loaded.naturalHeight > policy.maxPixels) {
        setError(`图片尺寸超过限制，请选择不超过 ${policy.maxWidth} × ${policy.maxHeight} 像素且总像素不超过 ${policy.maxPixels.toLocaleString()} 的图片`)
      } else setImage(loaded)
    }
    loaded.onerror = () => { if (active) setError('无法读取图片，请重新上传有效图片') }
    loaded.src = source
    return () => { active = false; loaded.onload = null; loaded.onerror = null }
  }, [source, policy])
  useEffect(() => {
    if (!image || !area || !preview.current || area.width < 1 || area.height < 1) { onSelection(null); return }
    try {
      drawAvatarCrop(preview.current, image, area, 144)
      onSelection({ image, area })
    } catch { setError('无法生成头像预览，请重新上传'); onSelection(null) }
  }, [image, area, onSelection])
  return <div className="space-y-5">
    {error ? <p role="alert" className="rounded-md bg-destructive/10 p-4 text-sm text-destructive">{error}</p> : <div className="grid items-center gap-8 sm:grid-cols-[minmax(0,1fr)_160px]">
      <div className="space-y-4">
        <div role="group" aria-label="头像裁剪区域" className={`relative h-[280px] overflow-hidden rounded-lg bg-neutral-900 sm:h-[320px] ${disabled ? 'pointer-events-none' : ''}`}>
          <Cropper image={source} crop={crop} zoom={zoom} aspect={1} onCropChange={setCrop} onZoomChange={setZoom} onCropAreaChange={onCropAreaChange} zoomWithScroll={!disabled} keyboardStep={disabled ? 0 : 10} mediaProps={{ alt: '待裁剪头像' }}/>
        </div>
        <div className="flex items-center gap-3"><label htmlFor="avatar-zoom" className="shrink-0 text-xs text-muted-foreground">缩放</label><input id="avatar-zoom" type="range" min="1" max="3" step="0.01" value={zoom} disabled={disabled} onChange={event => setZoom(Number(event.target.value))} className="w-full accent-primary"/><span className="w-10 text-right text-xs tabular-nums text-muted-foreground">{Math.round(zoom * 100)}%</span></div>
      </div>
      <div className="flex flex-col items-center gap-4"><p className="text-sm text-muted-foreground">预览</p><canvas ref={preview} width={144} height={144} role="img" aria-label="裁剪后的头像预览" className="h-36 w-36 rounded-full border bg-muted"/></div>
    </div>}
  </div>
}
