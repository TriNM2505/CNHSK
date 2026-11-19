import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { appRoutes } from '@/app/routes'
import { getCurrentUser } from '@/features/auth/api/getCurrentUser'
import type { RoleCode } from '@/features/auth/types'
import { getKnowledgeTree } from '@/features/question-bank/api/knowledge'
import { getQuestion } from '@/features/question-bank/api/questions'
import { ApiError } from '@/shared/api'

import { listReviews } from './api/reviews'

vi.mock('@/features/auth/api/getCurrentUser', () => ({ getCurrentUser: vi.fn() }))
vi.mock('./api/reviews', () => ({
  listReviews: vi.fn(),
  bulkApproveReviews: vi.fn(),
  approveReview: vi.fn(),
  rejectReview: vi.fn(),
}))
vi.mock('@/features/question-bank/api/knowledge', () => ({ getKnowledgeTree: vi.fn() }))
vi.mock('@/features/question-bank/api/questions', () => ({
  getQuestion: vi.fn(),
  updateQuestion: vi.fn(),
  archiveQuestion: vi.fn(),
}))
function renderRoute(roles: RoleCode[] | null, entry = '/content/ai-review') {
  if (roles)
    vi.mocked(getCurrentUser).mockResolvedValue({
      id: 7,
      displayName: 'Kiểm thử',
      email: 'test@example.test',
      emailVerified: true,
      roles,
    })
  else
    vi.mocked(getCurrentUser).mockRejectedValue(
      new ApiError({ status: 401, errorCode: 'UNAUTHENTICATED', message: 'Chưa đăng nhập.' }),
    )
  const router = createMemoryRouter(appRoutes, { initialEntries: [entry] })
  render(<RouterProvider router={router} />)
  return router
}
beforeEach(() => {
  vi.mocked(getCurrentUser).mockReset()
  vi.mocked(listReviews)
    .mockReset()
    .mockResolvedValue({
      items: [
        {
          id: 901,
          stemSummary: 'Ví dụ tự soạn.',
          hskLevel: 2,
          labels: [],
          generatedAt: '2026-11-17T00:00:00Z',
          runId: null,
          viewedByMe: false,
        },
      ],
      page: 0,
      size: 20,
      totalElements: 1,
    })
  vi.mocked(getKnowledgeTree).mockReset().mockResolvedValue({ nodes: [] })
  vi.mocked(getQuestion)
    .mockReset()
    .mockResolvedValue({
      id: 901,
      qtype: 'FILL_BLANK',
      hskLevel: 2,
      difficulty: 2,
      stem: '我喝___。',
      options: [],
      correctKey: '水',
      explanation: 'Ví dụ tự soạn.',
      source: 'AI',
      status: 'PENDING_REVIEW',
      labels: [],
      used: false,
    })
})

describe('AI review route integration', () => {
  it.each([
    ['TEACHER'],
    ['ADMIN', 'TEACHER'],
    ['CONTENT_EDITOR', 'TEACHER'],
  ] as RoleCode[][])(
    'allows explicit roles %j with a single link per navigation surface',
    async (...roles) => {
      renderRoute(roles)
      expect(await screen.findByRole('heading', { name: 'Duyệt câu hỏi AI' })).toBeVisible()
      for (const name of ['Quản trị', 'Quản trị trên thiết bị di động']) {
        const nav = within(screen.getByRole('navigation', { name }))
        expect(nav.getAllByRole('link', { name: 'Duyệt câu hỏi AI' })).toHaveLength(1)
        expect(nav.getByRole('link', { name: 'Duyệt câu hỏi AI' })).toHaveAttribute(
          'aria-current',
          'page',
        )
      }
    },
  )
  it.each(['ADMIN', 'LEARNER', 'MODERATOR', 'ACCOUNTANT', 'CONTENT_EDITOR'] as const)(
    'denies %s alone without loading protected queue',
    async (role) => {
      const router = renderRoute([role])
      await waitFor(() => expect(router.state.location.pathname).toBe('/403'))
      expect(listReviews).not.toHaveBeenCalled()
      expect(screen.queryByRole('link', { name: 'Duyệt câu hỏi AI' })).not.toBeInTheDocument()
    },
  )
  it('preserves the guest login return URL', async () => {
    const router = renderRoute(null, '/content/ai-review?hsk=2&page=3')
    expect(await screen.findByRole('heading', { name: 'Đăng nhập' })).toBeVisible()
    expect(new URLSearchParams(router.state.location.search).get('redirect')).toBe(
      '/content/ai-review?hsk=2&page=3',
    )
  })
  it('opens the existing detail GET and returns to the safe original queue with a fresh list', async () => {
    const router = renderRoute(['TEACHER'], '/content/ai-review?hsk=2&kid=TV1-901')
    fireEvent.click((await screen.findAllByRole('button', { name: 'Mở câu #901' }))[0]!)
    expect(await screen.findByRole('heading', { name: 'Chi tiết câu hỏi' })).toBeVisible()
    expect(getQuestion).toHaveBeenCalledWith(901, expect.any(AbortSignal))
    fireEvent.click(screen.getByRole('link', { name: 'Về hàng đợi duyệt AI' }))
    expect(await screen.findByRole('heading', { name: 'Duyệt câu hỏi AI' })).toBeVisible()
    await waitFor(() => expect(listReviews).toHaveBeenCalledTimes(2))
    expect(router.state.location.pathname).toBe('/content/ai-review')
    expect(new URLSearchParams(router.state.location.search).get('kid')).toBe('TV1-901')
    expect(screen.getAllByLabelText('Chọn câu #901')[0]).toBeDisabled()
  })
})
