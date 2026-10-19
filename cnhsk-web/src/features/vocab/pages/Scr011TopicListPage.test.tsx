import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '@/shared/api'

import { getTopics } from '../api/topics'
import type { TopicSummary } from '../types'
import Scr011TopicListPage from './Scr011TopicListPage'

vi.mock('../api/topics', () => ({ getTopics: vi.fn() }))

const topicList: TopicSummary[] = [
  {
    bestTestPercent: 83,
    code: 'FAMILY',
    hskLevel: 1,
    id: 11,
    learnedWords: 1,
    name: 'Gia đình 家庭',
    percent: 71,
    status: 'IN_PROGRESS',
    totalWords: 3,
  },
  {
    bestTestPercent: 95,
    code: 'GREETINGS',
    hskLevel: 9,
    id: 12,
    learnedWords: 10,
    name: 'Chào hỏi',
    percent: 100,
    status: 'COMPLETED',
    totalWords: 10,
  },
  {
    bestTestPercent: null,
    code: 'TRAVEL',
    hskLevel: 2,
    id: 13,
    learnedWords: 0,
    name: 'Du lịch',
    percent: 0,
    status: 'NOT_STARTED',
    totalWords: 8,
  },
]

function LocationProbe() {
  const location = useLocation()
  return <output data-testid="location">{`${location.pathname}${location.search}`}</output>
}

function renderPage(entry = '/learn/topics') {
  const router = createMemoryRouter(
    [
      {
        element: (
          <>
            <Scr011TopicListPage />
            <LocationProbe />
          </>
        ),
        path: '/learn/topics',
      },
      { element: <p>Chi tiết chủ đề</p>, path: '/learn/topics/:id' },
    ],
    { initialEntries: [entry] },
  )
  render(<RouterProvider router={router} />)
  return router
}

describe('Scr011TopicListPage', () => {
  beforeEach(() => vi.mocked(getTopics).mockReset().mockResolvedValue(topicList))

  it('chuẩn hoá cấp HSK sai về Tất cả bằng replace và tải toàn bộ danh sách', async () => {
    const router = renderPage('/learn/topics?hskLevel=10&from=menu')

    expect(await screen.findByRole('link', { name: /Gia đình/ })).toBeVisible()
    await waitFor(() => expect(router.state.location.search).toBe('?from=menu'))
    expect(router.state.historyAction).toBe('REPLACE')
    expect(getTopics).toHaveBeenCalledTimes(1)
    expect(getTopics).toHaveBeenCalledWith(undefined, expect.any(AbortSignal))
    expect(screen.getByLabelText('Cấp HSK')).toHaveValue('')
  })

  it('gửi cấp HSK hợp lệ lên API và giữ trên URL', async () => {
    const user = userEvent.setup()
    renderPage('/learn/topics?hskLevel=1')

    await screen.findByRole('link', { name: /Gia đình/ })
    expect(getTopics).toHaveBeenCalledWith(1, expect.any(AbortSignal))

    await user.selectOptions(screen.getByLabelText('Cấp HSK'), '9')
    await waitFor(() => expect(getTopics).toHaveBeenCalledWith(9, expect.any(AbortSignal)))
    expect(screen.getByTestId('location')).toHaveTextContent('/learn/topics?hskLevel=9')
  })

  it('tìm không dấu tiếng Việt theo chuỗi con mà không gọi lại API', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByRole('link', { name: /Gia đình/ })

    await user.type(screen.getByRole('searchbox', { name: 'Tìm chủ đề' }), 'gia dinh')

    expect(screen.getByRole('link', { name: /Gia đình/ })).toBeVisible()
    expect(screen.queryByRole('link', { name: /Chào hỏi/ })).not.toBeInTheDocument()
    expect(getTopics).toHaveBeenCalledTimes(1)
  })

  it('lọc trạng thái phía client và giữ thống kê của toàn bộ danh sách server', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByRole('link', { name: /Gia đình/ })

    await user.click(screen.getByRole('button', { name: 'Hoàn thành' }))

    expect(screen.getByRole('link', { name: /Chào hỏi/ })).toBeVisible()
    expect(screen.queryByRole('link', { name: /Gia đình/ })).not.toBeInTheDocument()
    const stats = screen.getByRole('region', { name: 'Thống kê chủ đề' })
    expect(within(stats).getByText('Tổng chủ đề').nextElementSibling).toHaveTextContent('3')
    expect(within(stats).getByText('Đang học').nextElementSibling).toHaveTextContent('1')
    expect(within(stats).getByText('Hoàn thành').nextElementSibling).toHaveTextContent('1')
    expect(getTopics).toHaveBeenCalledTimes(1)
  })

  it('dùng nguyên tiến độ, trạng thái và kết quả kiểm tra từ server', async () => {
    renderPage()

    const familyCard = await screen.findByRole('link', { name: /Gia đình/ })
    expect(familyCard).toHaveAttribute('href', '/learn/topics/11')
    expect(within(familyCard).getByText('Gia đình 家庭')).toHaveAttribute('lang', 'zh-CN')
    expect(within(familyCard).getByText('Đang học')).toBeVisible()
    expect(within(familyCard).getByRole('progressbar')).toHaveAttribute('aria-valuenow', '71')
    expect(within(familyCard).getByText('83%')).toBeVisible()
    expect(
      within(screen.getByRole('link', { name: /Du lịch/ })).getByText('Chưa kiểm tra'),
    ).toBeVisible()
    expect(within(screen.getByRole('link', { name: /Chào hỏi/ })).getByText('HSK 9')).toBeVisible()
    expect(screen.queryByText(/mở khóa|còn khóa|đã thành thạo/i)).not.toBeInTheDocument()
  })

  it('phân biệt danh sách server rỗng với bộ lọc phía client không khớp', async () => {
    const user = userEvent.setup()
    vi.mocked(getTopics).mockResolvedValueOnce([])
    const router = renderPage('/learn/topics?hskLevel=2')

    expect(await screen.findByText('Chưa có chủ đề cho cấp này')).toBeVisible()
    expect(screen.queryByText('Không có chủ đề phù hợp')).not.toBeInTheDocument()

    vi.mocked(getTopics).mockResolvedValueOnce(topicList)
    await user.selectOptions(screen.getByLabelText('Cấp HSK'), '1')
    await screen.findByRole('link', { name: /Gia đình/ })
    await user.type(screen.getByRole('searchbox', { name: 'Tìm chủ đề' }), 'không tồn tại')

    expect(screen.getByText('Không có chủ đề phù hợp')).toBeVisible()
    expect(screen.queryByText('Chưa có chủ đề cho cấp này')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Xoá bộ lọc' }))
    expect(screen.getByRole('link', { name: /Gia đình/ })).toBeVisible()
    expect(router.state.location.search).toBe('?hskLevel=1')
  })

  it('hiện lỗi trong trang và cho phép tải lại', async () => {
    const user = userEvent.setup()
    vi.mocked(getTopics).mockRejectedValueOnce(
      new ApiError({
        errorCode: 'TOPICS_UNAVAILABLE',
        message: 'Chưa tải được chủ đề.',
        requestId: 'req-topics',
        status: 503,
      }),
    )
    renderPage()

    expect(await screen.findByText('Chưa tải được chủ đề.')).toBeVisible()
    expect(screen.getByText('Mã lỗi: req-topics')).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Thử lại' }))
    expect(await screen.findByRole('link', { name: /Gia đình/ })).toBeVisible()
    expect(getTopics).toHaveBeenCalledTimes(2)
  })
})
