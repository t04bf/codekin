import type { Area } from 'react-easy-crop'

/** Preview and upload use the same source pixels; the circular mask is display-only. */
export function drawAvatarCrop(canvas: HTMLCanvasElement, image: HTMLImageElement, area: Area, size: number) {
  canvas.width = size
  canvas.height = size
  const context = canvas.getContext('2d')
  if (!context) throw new Error('当前浏览器不支持图片裁剪')
  context.drawImage(image, area.x, area.y, area.width, area.height, 0, 0, size, size)
}

export async function createAvatarFile(image: HTMLImageElement, area: Area): Promise<File> {
  const canvas = document.createElement('canvas')
  drawAvatarCrop(canvas, image, area, Math.min(512, Math.round(area.width), Math.round(area.height)))
  const blob = await new Promise<Blob>((resolve, reject) => canvas.toBlob(value => {
    if (value) resolve(value)
    else reject(new Error('图片裁剪失败，请重新选择图片'))
  }, 'image/png'))
  return new File([blob], 'avatar.png', { type: 'image/png' })
}
