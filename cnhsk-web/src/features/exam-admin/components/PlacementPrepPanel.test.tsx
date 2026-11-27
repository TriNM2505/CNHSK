import { fireEvent, render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '@/shared/api'

import { buildPlacementForms, getPlacementPool } from '../api/placement'
import { PlacementPrepPanel } from './PlacementPrepPanel'

vi.mock('../api/placement', () => ({
  getPlacementPool: vi.fn(),
  buildPlacementForms: vi.fn(),
}))

const level = (n: number, approved: number) => ({ level: n, approved, pending: 0, needed: 50 })

function renderPanel(onBuilt = vi.fn()) {
  return render(
    <MemoryRouter>
      <PlacementPrepPanel onBuilt={onBuilt} />
    </MemoryRouter>,
  )
}

beforeEach(() => {
  vi.mocked(getPlacementPool)
    .mockReset()
    .mockResolvedValue({ levels: [level(1, 50), level(2, 50), level(3, 12)], forms: [] })
  vi.mocked(buildPlacementForms).mockReset().mockResolvedValue({ built: [], kept: [] })
})

describe('placement preparation for content editors', () => {
  it('points to the CSV import, offers no AI drafting and waits for fifty questions per level', async () => {
    renderPanel()
    expect(await screen.findByText('12/50 câu đã duyệt')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Tạo 5 đề xếp trình độ' })).toBeDisabled()
    expect(screen.getByRole('link', { name: 'Nạp câu bằng CSV' })).toHaveAttribute('href', '/content/imports')
    expect(screen.queryByRole('button', { name: /Soạn câu/ })).not.toBeInTheDocument()
    expect(screen.queryByText(/AI/)).not.toBeInTheDocument()
  })

  it('names each level that is short of questions when building fails', async () => {
    vi.mocked(getPlacementPool).mockResolvedValue({
      levels: [level(1, 50), level(2, 50), level(3, 50)],
      forms: [],
    })
    vi.mocked(buildPlacementForms).mockRejectedValue(
      new ApiError({
        status: 422,
        errorCode: 'PLACEMENT_BANK_INSUFFICIENT',
        message: 'x',
        details: [{ level: 3, available: 41, needed: 50 }],
      }),
    )
    renderPanel()
    fireEvent.click(await screen.findByRole('button', { name: 'Tạo 5 đề xếp trình độ' }))
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Tạo đề' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('HSK 3 mới có 41/50 câu')
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
    renderPanel(onBuilt)
    expect(await screen.findByText('PLACEMENT-01 · Đã xuất bản · 30 câu')).toBeVisible()
    fireEvent.click(screen.getByRole('button', { name: 'Tạo 5 đề xếp trình độ' }))
    expect(buildPlacementForms).not.toHaveBeenCalled()
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Tạo đề' }))
    expect(await screen.findByRole('status')).toHaveTextContent('Đã tạo PLACEMENT-02, PLACEMENT-03')
    expect(onBuilt).toHaveBeenCalledTimes(1)
  })
})
