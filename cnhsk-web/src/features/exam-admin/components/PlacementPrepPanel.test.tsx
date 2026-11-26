import { fireEvent, render, screen, within } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '@/shared/api'

import { buildPlacementForms, draftPlacementQuestions, getPlacementPool } from '../api/placement'
import { PlacementPrepPanel } from './PlacementPrepPanel'

vi.mock('../api/placement', () => ({
  getPlacementPool: vi.fn(),
  draftPlacementQuestions: vi.fn(),
  buildPlacementForms: vi.fn(),
}))

const level = (n: number, approved: number) => ({ level: n, approved, pending: 4, needed: 50 })

beforeEach(() => {
  vi.mocked(getPlacementPool)
    .mockReset()
    .mockResolvedValue({ levels: [level(1, 50), level(2, 50), level(3, 12)], forms: [] })
  vi.mocked(draftPlacementQuestions).mockReset().mockResolvedValue({ started: true })
  vi.mocked(buildPlacementForms).mockReset().mockResolvedValue({ built: [], kept: [] })
})

describe('placement preparation for content editors', () => {
  it('drafts sixty questions for a level and waits until every level has enough approved', async () => {
    render(<PlacementPrepPanel onBuilt={vi.fn()} />)
    expect(await screen.findByText('12/50 câu đã duyệt · 4 câu đang chờ duyệt')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Tạo 5 đề xếp trình độ' })).toBeDisabled()
    fireEvent.click(screen.getByRole('button', { name: 'Soạn câu HSK 3' }))
    expect(await screen.findByRole('status')).toHaveTextContent('Đã nhờ AI soạn câu HSK 3')
    expect(draftPlacementQuestions).toHaveBeenCalledWith(3, 60)
  })

  it('explains a busy AI run instead of a generic error', async () => {
    vi.mocked(draftPlacementQuestions).mockRejectedValue(
      new ApiError({ status: 409, errorCode: 'AI_GENERATION_BUSY', message: 'x' }),
    )
    render(<PlacementPrepPanel onBuilt={vi.fn()} />)
    fireEvent.click(await screen.findByRole('button', { name: 'Soạn câu HSK 1' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Một lô AI khác đang chạy')
  })

  it('builds the forms after an in-page confirmation and refreshes the exam list', async () => {
    vi.mocked(getPlacementPool).mockResolvedValue({
      levels: [level(1, 50), level(2, 55), level(3, 60)],
      forms: [{ id: 9, code: 'PLACEMENT-01', status: 'PUBLISHED', questions: 30 }],
    })
    vi.mocked(buildPlacementForms).mockResolvedValue({
      built: ['PLACEMENT-02', 'PLACEMENT-03'],
      kept: ['PLACEMENT-01'],
    })
    const onBuilt = vi.fn()
    render(<PlacementPrepPanel onBuilt={onBuilt} />)
    expect(await screen.findByText('PLACEMENT-01 · Đã xuất bản · 30 câu')).toBeVisible()
    fireEvent.click(screen.getByRole('button', { name: 'Tạo 5 đề xếp trình độ' }))
    expect(buildPlacementForms).not.toHaveBeenCalled()
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Tạo đề' }))
    expect(await screen.findByRole('status')).toHaveTextContent('Đã tạo PLACEMENT-02, PLACEMENT-03')
    expect(onBuilt).toHaveBeenCalledTimes(1)
  })
})
