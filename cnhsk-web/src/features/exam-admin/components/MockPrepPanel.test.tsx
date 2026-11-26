import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import {
  buildMockPapers,
  getMockPool,
  getWordImages,
  uploadWordImage,
  type MockPool,
} from '../api/mockPrep'
import { MockPrepPanel } from './MockPrepPanel'

vi.mock('../api/mockPrep', () => ({
  getMockPool: vi.fn(),
  buildMockPapers: vi.fn(),
  getWordImages: vi.fn(),
  uploadWordImage: vi.fn(),
  removeWordImage: vi.fn(),
}))

function pool(level: number, approved: number): MockPool {
  return {
    level,
    questionsPerPaper: 40,
    parts: [
      { part: 'L1', section: 1, skill: 'LISTENING', qtype: 'TRUE_FALSE', picture: 'STEM', approved, pending: 2, needed: 15, options: 2, guide: 'Dòng đầu là emoji.' },
      { part: 'R4', section: 2, skill: 'READING', qtype: 'MULTIPLE_CHOICE', picture: 'NONE', approved, pending: 0, needed: 15, options: 3, guide: 'Câu có ___.' },
    ],
    papers: [],
  }
}

describe('HSK-format mock paper preparation', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(getMockPool).mockImplementation((level) => Promise.resolve(pool(level, 3)))
    vi.mocked(getWordImages).mockResolvedValue([
      { lexemeId: 7, text: '猫', pinyin: 'māo', meaningVi: 'con mèo', imageUrl: null },
      { lexemeId: 8, text: '书', pinyin: 'shū', meaningVi: 'sách', imageUrl: 'https://img.test/book.png' },
    ])
  })

  it('shows how to import each part and switches level', async () => {
    const user = userEvent.setup()
    render(<MockPrepPanel onBuilt={vi.fn()} />)
    const parts = await screen.findByRole('list', { name: 'Các phần đề HSK 1' })
    expect(within(parts).getByText('L1 · Nghe – Đúng/sai · có hình câu')).toBeVisible()
    expect(within(parts).getByText('3/15 câu đã nạp · 2 câu đang chờ duyệt')).toBeVisible()
    expect(within(parts).getByText(/Dòng đầu là emoji/)).toHaveTextContent('MCK1-L1-')
    expect(within(parts).queryByRole('button')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'HSK 3' }))
    expect(await screen.findByRole('list', { name: 'Các phần đề HSK 3' })).toBeVisible()
    expect(getMockPool).toHaveBeenLastCalledWith(3, expect.anything())
  })
  it('uploads a picture for a word', async () => {
    vi.mocked(uploadWordImage).mockResolvedValue({ lexemeId: 7, text: '猫', meaningVi: 'con mèo', imageUrl: 'x' })
    const user = userEvent.setup()
    render(<MockPrepPanel onBuilt={vi.fn()} />)
    expect(await screen.findByText('Ảnh thật cho từ vựng HSK 1 (1/2 từ có ảnh)')).toBeInTheDocument()
    const file = new File(['x'], 'cat.png', { type: 'image/png' })
    await user.upload(screen.getByLabelText('Tải ảnh cho 猫'), file)
    expect(uploadWordImage).toHaveBeenCalledWith(7, file)
  })

  it('builds the papers once every part has enough approved questions', async () => {
    vi.mocked(getMockPool).mockImplementation((level) => Promise.resolve(pool(level, 15)))
    vi.mocked(buildMockPapers).mockResolvedValue({ built: ['MOCK-HSK1-01'], kept: [] })
    const onBuilt = vi.fn()
    const user = userEvent.setup()
    render(<MockPrepPanel onBuilt={onBuilt} />)
    await user.click(await screen.findByRole('button', { name: 'Tạo 3 đề HSK 1' }))
    await user.click(screen.getByRole('button', { name: 'Tạo đề' }))
    expect(buildMockPapers).toHaveBeenCalledWith(1)
    expect(await screen.findByRole('status')).toHaveTextContent('MOCK-HSK1-01')
    expect(onBuilt).toHaveBeenCalled()
  })
})
