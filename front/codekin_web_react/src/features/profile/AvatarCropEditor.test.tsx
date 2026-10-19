import { useEffect } from 'react'
import { afterEach, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import type { Area } from 'react-easy-crop'
import { AvatarCropEditor } from './AvatarCropEditor'

vi.mock('react-easy-crop', () => ({ default: ({ onCropAreaChange }: { onCropAreaChange: (percent: Area, pixels: Area) => void }) => {
  useEffect(() => { onCropAreaChange({ x: 0, y: 12.5, width: 100, height: 75 }, { x: 0, y: 75, width: 450, height: 450 }) }, [onCropAreaChange])
  return <div>裁剪交互</div>
} }))
afterEach(() => vi.restoreAllMocks())
const policy = { code: 'AVATAR', extensions: ['png'], contentTypes: ['image/png'], maxBytes: 1048576, maxWidth: 2048, maxHeight: 2048, maxPixels: 4194304 }

function setup(width = 450) {
  const image = document.createElement('img')
  Object.defineProperties(image, { naturalWidth: { value: width }, naturalHeight: { value: 600 } })
  vi.spyOn(globalThis, 'Image').mockImplementation(function () { return image })
  const drawImage = vi.fn()
  vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue({ drawImage } as unknown as CanvasRenderingContext2D)
  const onSelection = vi.fn()
  render(<AvatarCropEditor source="blob:source" policy={policy} disabled={false} onSelection={onSelection}/> )
  return { image, drawImage, onSelection }
}
it('uses pixel coordinates rather than percentage coordinates for preview and selection', async () => {
  const { image, drawImage, onSelection } = setup()
  fireEvent.load(image)
  await waitFor(() => expect(onSelection).toHaveBeenLastCalledWith({ image, area: { x: 0, y: 75, width: 450, height: 450 } }))
  expect(drawImage).toHaveBeenCalledWith(image, 0, 75, 450, 450, 0, 0, 144, 144)
})
it('blocks oversized source images without enabling crop confirmation', async () => {
  const { image, onSelection } = setup(3000)
  fireEvent.load(image)
  expect(await screen.findByRole('alert')).toHaveTextContent('图片尺寸超过限制')
  expect(onSelection).not.toHaveBeenCalledWith(expect.objectContaining({ image }))
})
