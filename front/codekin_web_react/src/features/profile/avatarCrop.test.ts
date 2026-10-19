import { afterEach, expect, it, vi } from 'vitest'
import { createAvatarFile, drawAvatarCrop } from './avatarCrop'

afterEach(() => vi.restoreAllMocks())
it('renders selected source pixels for both preview and a bounded square PNG upload', async () => {
  const drawImage = vi.fn()
  vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue({ drawImage } as unknown as CanvasRenderingContext2D)
  const encode = vi.spyOn(HTMLCanvasElement.prototype, 'toBlob').mockImplementation(function (this: HTMLCanvasElement, callback) {
    expect(this.width).toBe(512)
    expect(this.height).toBe(512)
    callback(new Blob(['png'], { type: 'image/png' }))
  })
  const image = new Image()
  const area = { x: 100, y: 80, width: 800, height: 800 }
  drawAvatarCrop(document.createElement('canvas'), image, area, 144)
  const file = await createAvatarFile(image, area)
  expect(drawImage.mock.calls).toEqual([[image, 100, 80, 800, 800, 0, 0, 144, 144], [image, 100, 80, 800, 800, 0, 0, 512, 512]])
  expect(encode).toHaveBeenCalledWith(expect.any(Function), 'image/png')
  expect(file.name).toBe('avatar.png')
  expect(file.type).toBe('image/png')
})
it('does not enlarge small images and rejects failed encoding', async () => {
  vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue({ drawImage: vi.fn() } as unknown as CanvasRenderingContext2D)
  vi.spyOn(HTMLCanvasElement.prototype, 'toBlob').mockImplementation(function (this: HTMLCanvasElement, callback) {
    expect(this.width).toBe(80)
    callback(null)
  })
  await expect(createAvatarFile(new Image(), { x: 0, y: 0, width: 80, height: 80 })).rejects.toThrow('图片裁剪失败')
})
