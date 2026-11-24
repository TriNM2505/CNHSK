import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { getLearningTasks, type LearningTask } from '../api/learning-tasks'
import { createGradingRequest, getGradingPolicy } from '../api/requests'
import WritingPracticePage from './WritingPracticePage'

vi.mock('@/shared/auth', () => ({
  useCurrentUser: () => ({
    status: 'authenticated',
    user: { id: 7, hskLevel: 2, roles: ['LEARNER'] },
  }),
}))
vi.mock('../api/learning-tasks', () => ({ getLearningTasks: vi.fn() }))
vi.mock('../api/requests', () => ({ createGradingRequest: vi.fn(), getGradingPolicy: vi.fn() }))

const task: LearningTask = {
  id: 31,
  code: 'VIET_NL2_004',
  kind: 'WRITING',
  hskLevel: 2,
  nlKid: 'NL2-004',
  titleVi: 'Viết đoạn mô tả một người',
  promptVi: 'Viết 3–5 câu mô tả một người bạn.',
  promptZh: '写一写你的一个朋友。',
  minChars: 10,
  maxSeconds: null,
}

function Location() {
  return <p data-testid="path">{useLocation().pathname + useLocation().search}</p>
}

function renderPage(path = '/learn/writing') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route element={<WritingPracticePage />} path="/learn/writing" />
        <Route element={<p>Chi tiết bài</p>} path="/grading/:id" />
      </Routes>
      <Location />
    </MemoryRouter>,
  )
}

beforeEach(() => {
  vi.mocked(getLearningTasks).mockReset().mockResolvedValue([task])
  vi.mocked(getGradingPolicy).mockReset().mockResolvedValue({
    claimHours: 24,
    gradingHours: 24,
    minHanzi: 5,
    maxHanzi: 800,
    maxChars: 3000,
    cost: 1,
    refundTerms: '',
  })
  vi.mocked(createGradingRequest).mockReset()
})

describe('Viết', () => {
  it('opens on the account level, lists its writing tasks and switches level through the address', async () => {
    renderPage()
    await screen.findByText(task.titleVi)
    expect(getLearningTasks).toHaveBeenLastCalledWith('WRITING', 2, expect.any(AbortSignal))
    expect(screen.getByRole('button', { name: 'HSK 2' })).toHaveAttribute('aria-pressed', 'true')
    await userEvent.click(screen.getByRole('button', { name: 'HSK 3' }))
    await waitFor(() =>
      expect(getLearningTasks).toHaveBeenLastCalledWith('WRITING', 3, expect.any(AbortSignal)),
    )
    expect(screen.getByTestId('path')).toHaveTextContent('/learn/writing?hsk=3')
  })

  it('sends the essay with the task once it reaches the task length', async () => {
    vi.mocked(createGradingRequest).mockResolvedValue({ id: 88 } as Awaited<
      ReturnType<typeof createGradingRequest>
    >)
    renderPage()
    await userEvent.click(await screen.findByRole('button', { name: new RegExp(task.titleVi) }))
    const send = screen.getByRole('button', { name: /Gửi giáo viên chấm/ })
    await userEvent.type(screen.getByLabelText(/Bài viết/), '我的朋友')
    expect(send).toBeDisabled()
    await userEvent.type(screen.getByLabelText(/Bài viết/), '很高，他喜欢穿蓝色的衣服。')
    expect(send).toBeEnabled()
    await userEvent.click(send)
    await screen.findByText('Chi tiết bài')
    expect(createGradingRequest).toHaveBeenCalledWith({
      essay: '我的朋友很高，他喜欢穿蓝色的衣服。',
      prompt: null,
      hskLevel: 2,
      taskId: 31,
    })
  })

  it('offers free writing when a level has no task', async () => {
    vi.mocked(getLearningTasks).mockResolvedValue([])
    renderPage('/learn/writing?hsk=1')
    expect(await screen.findByText('Chưa có nhiệm vụ viết')).toBeVisible()
    expect(screen.getAllByRole('link', { name: 'Viết tự do' })[0]).toHaveAttribute(
      'href',
      '/grading/new',
    )
  })
})
