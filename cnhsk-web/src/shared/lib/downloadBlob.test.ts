import { afterEach, describe, expect, it, vi } from 'vitest'

import { downloadBlob } from './downloadBlob'

describe('downloadBlob', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  it('tải file bằng object URL rồi dọn tài nguyên và phần tử tạm', () => {
    const createObjectURL = vi.fn().mockReturnValue('blob:cnhsk-download')
    const revokeObjectURL = vi.fn()
    vi.stubGlobal('URL', { createObjectURL, revokeObjectURL })

    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined)
    const blob = new Blob(['nội dung CSV'], { type: 'text/csv' })

    downloadBlob(blob, 'danh-sách-lỗi.csv')

    const clickedLink = click.mock.instances[0] as HTMLAnchorElement | undefined
    expect(createObjectURL).toHaveBeenCalledWith(blob)
    expect(clickedLink).toMatchObject({
      href: 'blob:cnhsk-download',
      download: 'danh-sách-lỗi.csv',
    })
    expect(document.body.contains(clickedLink ?? null)).toBe(false)
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:cnhsk-download')
  })
})
