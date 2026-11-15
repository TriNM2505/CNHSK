import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/shared/api'
import { getQuestion, listQuestions } from '@/features/question-bank/api/questions'
import { questionFixture, questionPageFixture } from '../test-fixtures'
import { archiveExam, cloneExam, getExam, publishExam, saveExam } from '../api/exams'
import { examFixture } from '../test-fixtures'
import Page from './Scr066ExamEditorPage'
vi.mock('../api/exams', () => ({
  getExam: vi.fn(),
  saveExam: vi.fn(),
  publishExam: vi.fn(),
  archiveExam: vi.fn(),
  cloneExam: vi.fn(),
}))
vi.mock('@/features/question-bank/api/questions', () => ({
  getQuestion: vi.fn(),
  listQuestions: vi.fn(),
}))
beforeEach(() => {
  vi.mocked(listQuestions).mockReset().mockResolvedValue(questionPageFixture)
  vi.mocked(getQuestion).mockReset().mockResolvedValue(questionFixture)
  vi.mocked(getExam).mockReset().mockResolvedValue(examFixture)
  vi.mocked(saveExam)
    .mockReset()
    .mockResolvedValue({ ...examFixture, etag: 'saved-etag' })
  vi.mocked(publishExam)
    .mockReset()
    .mockResolvedValue({ ...examFixture, status: 'PUBLISHED' })
  vi.mocked(archiveExam)
    .mockReset()
    .mockResolvedValue({ ...examFixture, status: 'ARCHIVED' })
  vi.mocked(cloneExam)
    .mockReset()
    .mockResolvedValue({ ...examFixture, id: 2302, code: 'COPY' })
})
function open(path = '/content/exams/2301') {
  const router = createMemoryRouter(
    [
      { path: '/content/exams/new', element: <Page /> },
      { path: '/content/exams/:id', element: <Page /> },
      { path: '/content/exams', element: <p>Danh sách đích</p> },
    ],
    { initialEntries: [path] },
  )
  render(<RouterProvider router={router} />)
  return router
}
const conflict = () =>
  new ApiError({ status: 409, errorCode: 'EDIT_CONFLICT', message: 'x', requestId: 'fixture' })
describe('exam editor requests and concurrency', () => {
  it('merges a late selected question into the current draft without reverting typed metadata', async () => {
    let resolve!: (value: typeof questionFixture) => void
    vi.mocked(getQuestion).mockReturnValue(
      new Promise((done) => {
        resolve = done
      }),
    )
    open()
    await screen.findByDisplayValue(examFixture.title)
    fireEvent.click(screen.getByRole('button', { name: 'Chọn câu hỏi' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Thêm CAU-902' }))
    fireEvent.change(screen.getByLabelText('Tên đề', { exact: false }), {
      target: { value: 'Tên vừa nhập trong lúc chờ câu' },
    })
    await act(async () => resolve(questionFixture))
    expect(screen.getByDisplayValue('Tên vừa nhập trong lúc chờ câu')).toBeVisible()
    fireEvent.click(screen.getByRole('button', { name: 'Lưu nháp' }))
    await waitFor(() =>
      expect(saveExam).toHaveBeenCalledWith(
        2301,
        expect.objectContaining({
          title: 'Tên vừa nhập trong lúc chờ câu',
          questions: [
            { questionId: 901, sectionIndex: 0, points: 10 },
            { questionId: 902, sectionIndex: 0, points: 1 },
          ],
        }),
        'opaque-revision-a',
      ),
    )
  })
  it('keeps declared score and live sum separate; saves a mismatched draft once with its etag', async () => {
    vi.mocked(saveExam).mockImplementation(() => new Promise(() => {}))
    open()
    await screen.findByDisplayValue(examFixture.title)
    fireEvent.change(screen.getByLabelText('Tổng điểm khai báo', { exact: false }), {
      target: { value: '100' },
    })
    expect(screen.getByText(/Chưa khớp tổng khai báo/)).toBeVisible()
    fireEvent.click(screen.getByRole('button', { name: 'Lưu nháp' }))
    fireEvent.click(screen.getByRole('button', { name: 'Lưu nháp' }))
    await waitFor(() => expect(saveExam).toHaveBeenCalledTimes(1))
    expect(saveExam).toHaveBeenCalledWith(
      2301,
      expect.objectContaining({
        totalScore: 100,
        questions: [{ questionId: 901, sectionIndex: 0, points: 10 }],
      }),
      'opaque-revision-a',
    )
  })
  it('holds the draft on EDIT_CONFLICT until confirmed reload and never overwrites with a new etag', async () => {
    vi.mocked(saveExam).mockRejectedValue(conflict())
    const router = open()
    await screen.findByDisplayValue(examFixture.title)
    fireEvent.change(screen.getByLabelText('Tên đề', { exact: false }), {
      target: { value: 'Bản đang sửa' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Lưu nháp' }))
    expect(await screen.findByText('Đề vừa được người khác sửa, tải lại')).toBeVisible()
    expect(screen.getByDisplayValue('Bản đang sửa')).toBeDisabled()
    expect(getExam).toHaveBeenCalledTimes(1)
    fireEvent.click(screen.getByRole('button', { name: 'Tải lại đề' }))
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Huỷ' }))
    expect(screen.getByDisplayValue('Bản đang sửa')).toBeInTheDocument()
    vi.mocked(getExam).mockResolvedValue({
      ...examFixture,
      title: 'Bản trên máy chủ',
      etag: 'new-revision',
    })
    fireEvent.click(screen.getByRole('button', { name: 'Tải lại đề' }))
    fireEvent.click(
      within(screen.getByRole('dialog')).getByRole('button', { name: 'Tải lại bản trên máy chủ' }),
    )
    await screen.findByDisplayValue('Bản trên máy chủ')
    expect(saveExam).toHaveBeenCalledTimes(1)
    expect(router.state.location.pathname).toBe('/content/exams/2301')
  })
  it('lets backend decide publication even when GET already reports errors and displays all 422 details', async () => {
    const details = [
      'EMPTY_SECTION',
      'QUESTION_NOT_APPROVED',
      'LISTENING_AUDIO_MISSING',
      'SCORE_MISMATCH',
    ].map((code) => ({ code, message: code, sectionIndex: 0 }))
    vi.mocked(getExam).mockResolvedValue({ ...examFixture, publicationErrors: [details[0]!] })
    vi.mocked(publishExam).mockRejectedValue(
      new ApiError({
        status: 422,
        errorCode: 'EXAM_PUBLISH_INVALID',
        message: 'Không thể công bố',
        details,
      }),
    )
    open()
    await screen.findByDisplayValue(examFixture.title)
    fireEvent.click(screen.getByRole('button', { name: 'Công bố' }))
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Xác nhận' }))
    await waitFor(() => expect(publishExam).toHaveBeenCalledWith(2301, 'opaque-revision-a'))
    await waitFor(() =>
      expect(screen.getByRole('region', { name: 'Lỗi chặn công bố' })).toHaveTextContent(
        'SCORE_MISMATCH',
      ),
    )
    for (const item of details)
      expect(screen.getByRole('region', { name: 'Lỗi chặn công bố' })).toHaveTextContent(item.code)
  })
  it.each(['PUBLISHED', 'ARCHIVED'] as const)(
    'keeps %s structure readonly while allowing cloning',
    async (status) => {
      vi.mocked(getExam).mockResolvedValue({ ...examFixture, status })
      open()
      await screen.findByDisplayValue(examFixture.title)
      expect(screen.getByLabelText('Tên đề', { exact: false })).toBeDisabled()
      expect(screen.getByLabelText('Điểm câu CAU-901')).toBeDisabled()
      expect(screen.queryByRole('button', { name: 'Lưu nháp' })).not.toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Nhân bản đề' })).toBeEnabled()
    },
  )
  it('keeps legacy null fields blank and prevents an invalid PUT', async () => {
    vi.mocked(getExam).mockResolvedValue({
      ...examFixture,
      sections: [{ name: 'Nhập cũ', type: null, durationMin: null }],
    })
    open()
    await screen.findByDisplayValue('Nhập cũ')
    expect(screen.getByLabelText('Loại phần 1', { exact: false })).toHaveValue('')
    expect(screen.getByLabelText('Thời lượng phần 1 (phút)', { exact: false })).toHaveValue(null)
    fireEvent.click(screen.getByRole('button', { name: 'Lưu nháp' }))
    expect(screen.getByRole('alert')).toHaveTextContent('bổ sung loại')
    expect(saveExam).not.toHaveBeenCalled()
  })
  it('confirms leaving a dirty draft and retains it on cancel', async () => {
    const router = open()
    await screen.findByDisplayValue(examFixture.title)
    fireEvent.change(screen.getByLabelText('Tên đề', { exact: false }), {
      target: { value: 'Chưa lưu' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Về danh sách đề' }))
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Huỷ' }))
    expect(screen.getByDisplayValue('Chưa lưu')).toBeVisible()
    fireEvent.click(screen.getByRole('button', { name: 'Về danh sách đề' }))
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Rời trang' }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/content/exams'))
    expect(saveExam).not.toHaveBeenCalled()
  })
  it('rejects an invalid detail ID without a request', () => {
    open('/content/exams/abc')
    expect(screen.getByRole('alert')).toHaveTextContent('Mã đề không hợp lệ')
    expect(getExam).not.toHaveBeenCalled()
  })
  it('creates a new draft and replaces the new URL', async () => {
    const router = open('/content/exams/new')
    await screen.findByRole('heading', { name: 'Tạo đề nháp' })
    fireEvent.change(screen.getByLabelText('Mã đề', { exact: false }), { target: { value: 'NEW' } })
    fireEvent.change(screen.getByLabelText('Tên đề', { exact: false }), {
      target: { value: 'Đề mới' },
    })
    fireEvent.change(screen.getByLabelText('Cấp HSK', { exact: false }), { target: { value: '1' } })
    fireEvent.change(screen.getByLabelText('Tổng điểm khai báo', { exact: false }), {
      target: { value: '100' },
    })
    fireEvent.change(screen.getByLabelText('Tên phần 1', { exact: false }), {
      target: { value: 'Đọc' },
    })
    fireEvent.change(screen.getByLabelText('Loại phần 1', { exact: false }), {
      target: { value: 'READING' },
    })
    fireEvent.change(screen.getByLabelText('Thời lượng phần 1 (phút)', { exact: false }), {
      target: { value: '0' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Lưu nháp' }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/content/exams/2301'))
    expect(saveExam).toHaveBeenCalledWith(
      null,
      expect.objectContaining({ code: 'NEW', questions: [], totalScore: 100 }),
      undefined,
    )
  })
})
