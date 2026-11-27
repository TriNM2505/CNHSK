import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/shared/api'
import { archiveExam, cloneExam, listExams } from '../api/exams'
import { examFixture, pageFixture } from '../test-fixtures'
import Page from './Scr065ExamListPage'
vi.mock('../api/exams', () => ({ listExams: vi.fn(), archiveExam: vi.fn(), cloneExam: vi.fn() }))
vi.mock('../api/mockPrep', () => ({
  getMockPool: vi.fn().mockResolvedValue({ level: 1, questionsPerPaper: 40, parts: [], papers: [] }),
  getWordImages: vi.fn().mockResolvedValue([]),
  buildMockPapers: vi.fn(),
  uploadWordImage: vi.fn(),
  removeWordImage: vi.fn(),
}))
vi.mock('../api/placement', () => ({
  getPlacementPool: vi.fn().mockResolvedValue({ levels: [], forms: [] }),
  buildPlacementForms: vi.fn(),
}))
beforeEach(() => {
  vi.mocked(listExams).mockReset().mockResolvedValue(pageFixture)
  vi.mocked(archiveExam)
    .mockReset()
    .mockResolvedValue({ ...examFixture, status: 'ARCHIVED' })
  vi.mocked(cloneExam)
    .mockReset()
    .mockResolvedValue({ ...examFixture, id: 2302 })
})
function open(path = '/content/exams') {
  const router = createMemoryRouter(
    [
      { path: '/content/exams', element: <Page /> },
      { path: '/content/exams/:id', element: <p>Editor đích</p> },
    ],
    { initialEntries: [path] },
  )
  render(<RouterProvider router={router} />)
  return router
}
describe('exam management list', () => {
  it('reads and preserves URL filters, resetting the page on filtering', async () => {
    const router = open('/content/exams?status=DRAFT&hsk=2&q=TU&page=2')
    await screen.findAllByRole('link', { name: examFixture.title })
    expect(listExams).toHaveBeenCalledWith(
      expect.objectContaining({ status: 'DRAFT', hsk: 2, q: 'TU', page: 2 }),
      expect.any(AbortSignal),
    )
    fireEvent.change(screen.getByLabelText('Trạng thái đề'), { target: { value: 'PUBLISHED' } })
    await waitFor(() => expect(router.state.location.search).toBe('?status=PUBLISHED&hsk=2&q=TU'))
  })
  it('requires archive confirmation and shows EXAM_IN_USE without retrying', async () => {
    vi.mocked(archiveExam).mockRejectedValue(
      new ApiError({ status: 409, errorCode: 'EXAM_IN_USE', message: 'x' }),
    )
    open()
    await screen.findAllByRole('link', { name: examFixture.title })
    fireEvent.click(screen.getAllByRole('button', { name: `Lưu trữ ${examFixture.code}` })[0]!)
    expect(archiveExam).not.toHaveBeenCalled()
    fireEvent.click(
      within(screen.getByRole('dialog')).getByRole('button', { name: 'Xác nhận lưu trữ' }),
    )
    expect(await screen.findByRole('alert')).toHaveTextContent('đang được cuộc thi sử dụng')
    expect(archiveExam).toHaveBeenCalledTimes(1)
  })
  it('clones with an explicit code and opens the new draft', async () => {
    const router = open()
    await screen.findAllByRole('link', { name: examFixture.title })
    fireEvent.click(screen.getAllByRole('button', { name: `Nhân bản ${examFixture.code}` })[0]!)
    fireEvent.change(screen.getByLabelText('Mã đề nhân bản', { exact: false }), {
      target: { value: 'COPY' },
    })
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Nhân bản đề' }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/content/exams/2302'))
    expect(cloneExam).toHaveBeenCalledWith(2301, 'COPY', '')
  })
  it('locks ambiguous network writes until reading the list again', async () => {
    vi.mocked(archiveExam).mockRejectedValue(
      new ApiError({ status: 0, errorCode: 'NETWORK_ERROR', message: 'Mất mạng' }),
    )
    open()
    await screen.findAllByRole('link', { name: examFixture.title })
    fireEvent.click(screen.getAllByRole('button', { name: `Lưu trữ ${examFixture.code}` })[0]!)
    fireEvent.click(
      within(screen.getByRole('dialog')).getByRole('button', { name: 'Xác nhận lưu trữ' }),
    )
    await screen.findByText('Mất mạng')
    expect(
      screen.getAllByRole('button', { name: `Nhân bản ${examFixture.code}` })[0]!,
    ).toBeDisabled()
    fireEvent.click(screen.getByRole('button', { name: 'Tải lại danh sách' }))
    await waitFor(() => expect(listExams).toHaveBeenCalledTimes(2))
    expect(archiveExam).toHaveBeenCalledTimes(1)
  })
})
