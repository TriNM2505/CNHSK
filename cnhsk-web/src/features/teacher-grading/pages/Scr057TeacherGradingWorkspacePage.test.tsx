import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Link, MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/shared/api'
import { completeGrading, getAssignedGrading, releaseGrading } from '../api/requests'
import { TeacherGradingWorkspaceView } from '../components/TeacherGradingWorkspaceView'
import { assigned, completed, draft, result } from '../test-fixtures'
import Scr057TeacherGradingWorkspacePage from './Scr057TeacherGradingWorkspacePage'

const auth = vi.hoisted(() => ({ status: 'authenticated', user: { id: 1, roles: ['TEACHER'] } }))
vi.mock('@/shared/auth', () => ({ useCurrentUser: () => auth }))
vi.mock('../api/requests', () => ({
  completeGrading: vi.fn(),
  getAssignedGrading: vi.fn(),
  releaseGrading: vi.fn(),
}))
function Location() {
  return <p data-testid="path">{useLocation().pathname}</p>
}
function content() {
  return (
    <MemoryRouter initialEntries={['/teacher/grading/42']}>
      <Routes>
        <Route path="/teacher/grading/:id" element={<Scr057TeacherGradingWorkspacePage />} />
        <Route path="/teacher/grading" element={<p>Hàng đợi đích</p>} />
      </Routes>
      <Link to="/teacher/grading/43">Mở bài khác</Link>
      <Location />
    </MemoryRouter>
  )
}
async function fill() {
  await screen.findByLabelText(/Ngữ pháp/)
  for (const [label, value] of [
    ['Ngữ pháp', '20'],
    ['Từ vựng', '18'],
    ['Mạch lạc', '21'],
    ['Chữ viết', '19'],
  ])
    fireEvent.change(screen.getByLabelText(new RegExp(label!)), { target: { value } })
  fireEvent.change(screen.getByLabelText(/Nhận xét/), { target: { value: result.feedback } })
}
async function submit() {
  await userEvent.click(screen.getByRole('button', { name: 'Nộp kết quả' }))
  await userEvent.click(screen.getByRole('button', { name: 'Xác nhận nộp' }))
}
beforeEach(() => {
  auth.user = { id: 1, roles: ['TEACHER'] }
  vi.mocked(getAssignedGrading).mockReset().mockResolvedValue(assigned)
  vi.mocked(completeGrading).mockReset().mockResolvedValue(completed)
  vi.mocked(releaseGrading).mockReset().mockResolvedValue(undefined)
})
afterEach(() => vi.restoreAllMocks())
describe('teacher grading workspace', () => {
  it('shows loading, text-only essay/prompt and the deadline without revealing an identity', async () => {
    vi.mocked(getAssignedGrading).mockResolvedValue({
      ...assigned,
      essay: '<script>privateEssay()</script>',
      prompt: '<b>Đề mẫu</b>',
    })
    render(content())
    expect(screen.getByText('Đang tải bài cần chấm…')).toBeVisible()
    await screen.findByText('<script>privateEssay()</script>')
    expect(screen.getByText('<b>Đề mẫu</b>')).toBeVisible()
    expect(document.querySelector('script')).toBeNull()
    expect(screen.getByRole('timer')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Nộp kết quả' })).toBeDisabled()
  })
  it('sums the rubric, enforces bounds, and confirms before sending only contract fields', async () => {
    render(content())
    await fill()
    expect(screen.getByText('Tổng điểm: 78/100')).toBeVisible()
    fireEvent.change(screen.getByLabelText(/Ngữ pháp/), { target: { value: '26' } })
    expect(screen.getByRole('button', { name: 'Nộp kết quả' })).toBeDisabled()
    fireEvent.change(screen.getByLabelText(/Ngữ pháp/), { target: { value: '20' } })
    await userEvent.click(screen.getByRole('button', { name: 'Thêm lỗi' }))
    expect(screen.getByRole('button', { name: 'Nộp kết quả' })).toBeDisabled()
    fireEvent.change(screen.getByLabelText(/Đã viết 1/), { target: { value: '原文' } })
    fireEvent.change(screen.getByLabelText(/Sửa thành 1/), { target: { value: '改文' } })
    await userEvent.click(screen.getByRole('button', { name: 'Nộp kết quả' }))
    expect(completeGrading).not.toHaveBeenCalled()
    await userEvent.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    await submit()
    await screen.findByText('Đã nộp kết quả')
    expect(completeGrading).toHaveBeenCalledWith('42', {
      criteria: result.criteria,
      feedback: result.feedback,
      corrections: [{ original: '原文', corrected: '改文', note: null }],
    })
    expect(screen.queryByRole('spinbutton')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Nộp kết quả' })).not.toBeInTheDocument()
    expect(screen.getByText('78/100')).toBeVisible()
    expect(screen.getByText('Điểm trả cho bài: 2 điểm')).toBeVisible()
  })
  it('adds and removes corrections and caps the list at 50 rows', () => {
    const onDraft = vi.fn()
    render(
      <MemoryRouter>
        <TeacherGradingWorkspaceView
          state="ready"
          detail={assigned}
          draft={{
            ...draft,
            corrections: Array.from({ length: 50 }, () => ({
              original: '原文',
              corrected: '改文',
              note: '',
            })),
          }}
          seconds={3600}
          pending={false}
          confirmation={null}
          onDraft={onDraft}
          onRetry={vi.fn()}
          onReview={vi.fn()}
          onClose={vi.fn()}
          onConfirm={vi.fn()}
        />
      </MemoryRouter>,
    )
    expect(screen.getByRole('button', { name: 'Thêm lỗi' })).toBeDisabled()
    fireEvent.click(screen.getByRole('button', { name: 'Xoá lỗi 1' }))
    expect(onDraft.mock.calls[0]?.[0].corrections).toHaveLength(49)
  })
  it.each(['GRADING_INVALID_RESULT', 'GRADING_DEADLINE_MISSED'])(
    'keeps the form after 422 %s without an automatic POST retry',
    async (errorCode) => {
      vi.mocked(completeGrading).mockRejectedValue(
        new ApiError({ status: 422, errorCode, message: 'Không nộp được kết quả.' }),
      )
      const storage = vi.spyOn(Storage.prototype, 'setItem')
      render(content())
      await fill()
      await submit()
      await screen.findByText('Không nộp được kết quả.')
      expect(screen.getByLabelText(/Nhận xét/)).toHaveValue(result.feedback)
      expect(screen.getByLabelText(/Ngữ pháp/)).toHaveValue(20)
      expect(completeGrading).toHaveBeenCalledTimes(1)
      expect(storage).not.toHaveBeenCalled()
    },
  )
  it('prevents double completion and displays result text without rendering markup', async () => {
    let resolve!: (value: typeof completed) => void
    vi.mocked(completeGrading).mockImplementation(
      () =>
        new Promise((done) => {
          resolve = done
        }),
    )
    render(content())
    await fill()
    await userEvent.click(screen.getByRole('button', { name: 'Nộp kết quả' }))
    const button = screen.getByRole('button', { name: 'Xác nhận nộp' })
    fireEvent.click(button)
    fireEvent.click(button)
    await waitFor(() => expect(completeGrading).toHaveBeenCalledTimes(1))
    await act(async () =>
      resolve({
        ...completed,
        result: {
          ...completed.result!,
          feedback: '<script>feedback()</script>',
          corrections: [
            { original: '<b>old</b>', corrected: '<img src=x>', note: '<script>note()</script>' },
          ],
        },
      }),
    )
    await screen.findByText('<script>feedback()</script>')
    expect(document.querySelector('script')).toBeNull()
    expect(document.querySelector('img')).toBeNull()
    expect(screen.queryByRole('button', { name: 'Trả lại bài' })).not.toBeInTheDocument()
  })
  it('confirms release, can cancel it, and returns to the queue on 204', async () => {
    render(content())
    await screen.findByText(assigned.essay)
    await userEvent.click(screen.getByRole('button', { name: 'Trả lại bài' }))
    expect(releaseGrading).not.toHaveBeenCalled()
    await userEvent.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Trả lại bài' }))
    await userEvent.click(screen.getByRole('button', { name: 'Xác nhận trả lại' }))
    await screen.findByText('Hàng đợi đích')
    expect(releaseGrading).toHaveBeenCalledTimes(1)
    expect(releaseGrading).toHaveBeenCalledWith('42')
    expect(completeGrading).not.toHaveBeenCalled()
  })
  it('handles a refreshed completed/unowned request as 403 with a way back and no private content', async () => {
    vi.mocked(getAssignedGrading).mockRejectedValue(
      new ApiError({
        status: 403,
        errorCode: 'GRADING_NOT_ASSIGNEE',
        message: 'Không còn giữ bài này.',
      }),
    )
    render(content())
    await screen.findByText('Không còn giữ bài này.')
    expect(screen.getByRole('link', { name: 'Về hàng đợi' })).toHaveAttribute(
      'href',
      '/teacher/grading',
    )
    expect(screen.queryByText(assigned.essay)).not.toBeInTheDocument()
    expect(completeGrading).not.toHaveBeenCalled()
  })
  it('shows a load error and retries GET only', async () => {
    vi.mocked(getAssignedGrading).mockRejectedValueOnce(
      new ApiError({ status: 0, errorCode: 'NETWORK_ERROR', message: 'Lỗi tải bài.' }),
    )
    render(content())
    await screen.findByText('Lỗi tải bài.')
    await userEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
    await screen.findByText(assigned.essay)
    expect(completeGrading).not.toHaveBeenCalled()
  })
  it('clears the draft and ignores pending completion when moving to another ID', async () => {
    let resolve!: (value: typeof completed) => void
    vi.mocked(completeGrading).mockImplementation(
      () =>
        new Promise((done) => {
          resolve = done
        }),
    )
    render(content())
    await fill()
    await submit()
    await userEvent.click(screen.getByRole('link', { name: 'Mở bài khác' }))
    await waitFor(() =>
      expect(getAssignedGrading).toHaveBeenLastCalledWith('43', expect.any(AbortSignal)),
    )
    await waitFor(() => expect(screen.getByLabelText(/Nhận xét/)).toHaveValue(''))
    await act(async () => resolve(completed))
    expect(screen.getByTestId('path')).toHaveTextContent('/teacher/grading/43')
    expect(screen.queryByText('Đã nộp kết quả')).not.toBeInTheDocument()
  })
  it('clears input when the teacher account changes', async () => {
    const view = render(content())
    await fill()
    auth.user = { id: 2, roles: ['TEACHER'] }
    view.rerender(content())
    await waitFor(() => expect(screen.getByLabelText(/Nhận xét/)).toHaveValue(''))
  })
})
