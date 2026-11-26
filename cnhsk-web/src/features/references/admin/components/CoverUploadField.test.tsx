import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'

import { CoverUploadField } from './CoverUploadField'

function Harness({ upload }: { upload: (file: File) => Promise<string> }) {
  const [value, setValue] = useState('')
  return <CoverUploadField disabled={false} onChange={setValue} upload={upload} value={value} />
}

describe('cover upload', () => {
  it('uploads a picture from the computer and keeps its link as the cover', async () => {
    const upload = vi
      .fn()
      .mockResolvedValue(
        'https://res.cloudinary.com/demo/image/upload/cnhsk/reference-covers/a.jpg',
      )
    render(<Harness upload={upload} />)
    const file = new File(['x'], 'bia.jpg', { type: 'image/jpeg' })
    await userEvent.upload(screen.getByLabelText('Chọn ảnh bìa từ máy'), file)
    await waitFor(() => expect(screen.getByAltText('Ảnh bìa đã chọn')).toBeVisible())
    expect(upload).toHaveBeenCalledWith(file)
    expect(screen.getByLabelText('Ảnh bìa (không bắt buộc)')).toHaveValue(
      'https://res.cloudinary.com/demo/image/upload/cnhsk/reference-covers/a.jpg',
    )
    await userEvent.click(screen.getByRole('button', { name: 'Bỏ ảnh' }))
    expect(screen.getByLabelText('Ảnh bìa (không bắt buộc)')).toHaveValue('')
  })

  it('refuses files that are not pictures or are too large, without uploading', async () => {
    const upload = vi.fn()
    render(<Harness upload={upload} />)
    const picker = screen.getByLabelText('Chọn ảnh bìa từ máy')
    await userEvent.upload(
      picker,
      new File([new Uint8Array(6 * 1024 * 1024)], 'to.png', { type: 'image/png' }),
    )
    expect(await screen.findByRole('alert')).toHaveTextContent('Ảnh bìa tối đa 5 MB.')
    expect(upload).not.toHaveBeenCalled()
  })

  it('shows why an upload failed', async () => {
    render(
      <Harness upload={vi.fn().mockRejectedValue(new Error('Chưa bật tính năng tải ảnh lên.'))} />,
    )
    await userEvent.upload(
      screen.getByLabelText('Chọn ảnh bìa từ máy'),
      new File(['x'], 'b.png', { type: 'image/png' }),
    )
    expect(await screen.findByRole('alert')).toHaveTextContent('Chưa bật tính năng tải ảnh lên.')
  })
})
