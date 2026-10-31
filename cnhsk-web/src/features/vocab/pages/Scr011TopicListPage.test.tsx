import { act, render, screen, waitFor, within } from '@testing-library/react'
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

const hskThreeTopic: TopicSummary = {
  bestTestPercent: null,
  code: 'SCHOOL',
  hskLevel: 3,
  id: 14,
  learnedWords: 4,
  name: 'Trường học 学校',
  percent: 80,
  status: 'IN_PROGRESS',
  totalWords: 5,
}

function pendingTopics() {
  let settle: ((topics: TopicSummary[]) => void) | undefined
  const promise = new Promise<TopicSummary[]>((resolve) => {
    settle = resolve
  })

  return {
    promise,
    async resolve(topics: TopicSummary[]) {
      const complete = settle
      if (!complete) throw new Error('Topic response resolver is unavailable')
      await act(async () => {
        complete(topics)
      })
    },
  }
}

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

    await user.selectOptions(screen.getByLabelText('Cấp HSK'), '3')
    await waitFor(() => expect(getTopics).toHaveBeenCalledWith(3, expect.any(AbortSignal)))
    expect(screen.getByTestId('location')).toHaveTextContent('/learn/topics?hskLevel=3')
  })

  it('chỉ cho lọc HSK 1–3 và bỏ qua cấp ngoài phạm vi trên URL', async () => {
    renderPage('/learn/topics?hskLevel=5')

    await screen.findByRole('link', { name: /Gia đình/ })
    expect(getTopics).toHaveBeenCalledWith(undefined, expect.any(AbortSignal))
    const options = within(screen.getByLabelText('Cấp HSK')).getAllByRole('option')
    expect(options.map((option) => option.getAttribute('value'))).toEqual(['', '1', '2', '3'])
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

  it('FR041_rendersEveryTopicCardWithServerCountsProgressStatusAndBestResult', async () => {
    renderPage()
    await screen.findByRole('link', { name: /Gia đình/ })
    const labels = {
      NOT_STARTED: 'Chưa học',
      IN_PROGRESS: 'Đang học',
      COMPLETED: 'Hoàn thành',
    }

    for (const topic of topicList) {
      const card = screen.getByRole('link', { name: (name) => name.includes(topic.name) })
      expect(within(card).getByText(topic.name)).toBeVisible()
      expect(within(card).getByText(`HSK ${topic.hskLevel}`)).toBeVisible()
      expect(within(card).getByText(`${topic.learnedWords}/${topic.totalWords} từ`)).toBeVisible()
      expect(within(card).getByRole('progressbar')).toHaveAttribute(
        'aria-valuenow',
        String(topic.percent),
      )
      expect(within(card).getByText(labels[topic.status])).toBeVisible()
      expect(
        within(card).getByText(
          topic.bestTestPercent === null ? 'Chưa kiểm tra' : `${topic.bestTestPercent}%`,
        ),
      ).toBeVisible()
    }
  })

  it('FR041_distinguishesZeroTestScoreFromNoTestAndDoesNotInferCompletionFromLearnedPercent', async () => {
    vi.mocked(getTopics).mockResolvedValueOnce([
      { ...hskThreeTopic, bestTestPercent: 0, learnedWords: 5, percent: 100 },
      ...topicList.filter((topic) => topic.status === 'NOT_STARTED'),
    ])
    renderPage()

    const schoolCard = await screen.findByRole('link', { name: /Trường học/ })
    expect(within(schoolCard).getByText('0%')).toBeVisible()
    expect(within(schoolCard).getByText('Đang học')).toBeVisible()
    expect(within(schoolCard).queryByText('Hoàn thành')).not.toBeInTheDocument()
    expect(within(schoolCard).getByRole('progressbar')).toHaveAttribute('aria-valuenow', '100')
    expect(
      within(screen.getByRole('link', { name: /Du lịch/ })).getByText('Chưa kiểm tra'),
    ).toBeVisible()
  })

  it('FR041_showsLoadingUntilTheServerRespondsWithoutPrematureEmptyState', async () => {
    const response = pendingTopics()
    vi.mocked(getTopics).mockReturnValueOnce(response.promise)
    renderPage()

    expect(screen.getByLabelText('Đang tải chủ đề')).toBeVisible()
    expect(screen.queryByText('Chưa có chủ đề cho cấp này')).not.toBeInTheDocument()
    expect(screen.queryByText('Không có chủ đề phù hợp')).not.toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Danh sách chủ đề' })).not.toBeInTheDocument()

    await response.resolve(topicList)

    expect(await screen.findByRole('link', { name: /Gia đình/ })).toBeVisible()
    expect(screen.queryByLabelText('Đang tải chủ đề')).not.toBeInTheDocument()
    expect(getTopics).toHaveBeenCalledTimes(1)
  })

  it('FR041_combinesAndClearsClientFiltersWithoutRefetchingOrRemovingTheHskFilter', async () => {
    const user = userEvent.setup()
    vi.mocked(getTopics).mockResolvedValueOnce(
      topicList.map<TopicSummary>((topic) => ({ ...topic, hskLevel: 1 })),
    )
    const router = renderPage('/learn/topics?hskLevel=1&from=menu')
    await screen.findByRole('link', { name: /Gia đình/ })

    await user.click(screen.getByRole('button', { name: 'Hoàn thành' }))
    const search = screen.getByRole('searchbox', { name: 'Tìm chủ đề' })
    await user.type(search, 'CHAO HOI')
    expect(screen.getByRole('link', { name: /Chào hỏi/ })).toBeVisible()
    expect(screen.getAllByRole('link')).toHaveLength(1)

    await user.clear(search)
    await user.type(search, 'gia dinh')
    expect(screen.getByText('Không có chủ đề phù hợp')).toBeVisible()
    expect(screen.queryByRole('link')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Xoá bộ lọc' }))
    expect(screen.getAllByRole('link')).toHaveLength(3)
    expect(search).toHaveValue('')
    expect(screen.getByRole('button', { name: 'Tất cả' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByLabelText('Cấp HSK')).toHaveValue('1')
    expect(router.state.location.search).toBe('?hskLevel=1&from=menu')
    expect(getTopics).toHaveBeenCalledTimes(1)
  })

  it('FR041_AT40_restoresTheHskFilterAndServerListWithBackAndForward', async () => {
    const user = userEvent.setup()
    vi.mocked(getTopics).mockImplementation((level) =>
      Promise.resolve(
        [...topicList, hskThreeTopic].filter(
          (topic) => level === undefined || topic.hskLevel === level,
        ),
      ),
    )
    const router = renderPage('/learn/topics?hskLevel=1&from=menu')
    await screen.findByRole('link', { name: /Gia đình/ })
    expect(
      within(screen.getByLabelText('Cấp HSK'))
        .getAllByRole('option')
        .map((option) => option.getAttribute('value')),
    ).toEqual(['', '1', '2', '3'])

    await user.selectOptions(screen.getByLabelText('Cấp HSK'), '3')
    expect(await screen.findByRole('link', { name: /Trường học/ })).toBeVisible()
    expect(screen.queryByRole('link', { name: /Gia đình/ })).not.toBeInTheDocument()
    expect(router.state.location.search).toBe('?hskLevel=3&from=menu')

    await act(async () => {
      await router.navigate(-1)
    })
    expect(await screen.findByRole('link', { name: /Gia đình/ })).toBeVisible()
    expect(screen.getByLabelText('Cấp HSK')).toHaveValue('1')
    expect(router.state.location.search).toBe('?hskLevel=1&from=menu')

    await act(async () => {
      await router.navigate(1)
    })
    expect(await screen.findByRole('link', { name: /Trường học/ })).toBeVisible()
    expect(screen.getByLabelText('Cấp HSK')).toHaveValue('3')
    expect(router.state.location.search).toBe('?hskLevel=3&from=menu')
    expect(getTopics).toHaveBeenCalledTimes(4)
    expect(getTopics).toHaveBeenLastCalledWith(3, expect.any(AbortSignal))
  })

  it('FR041_ignoresTheOldHskResponseWhenItArrivesAfterTheNewSelection', async () => {
    const user = userEvent.setup()
    const first = pendingTopics()
    const second = pendingTopics()
    vi.mocked(getTopics).mockImplementation((level) =>
      level === 1 ? first.promise : second.promise,
    )
    const router = renderPage('/learn/topics?hskLevel=1')
    await waitFor(() => expect(getTopics).toHaveBeenCalledWith(1, expect.any(AbortSignal)))

    await user.selectOptions(screen.getByLabelText('Cấp HSK'), '3')
    await waitFor(() => expect(getTopics).toHaveBeenCalledWith(3, expect.any(AbortSignal)))
    expect(vi.mocked(getTopics).mock.calls[0]?.[1]?.aborted).toBe(true)

    await second.resolve([hskThreeTopic])
    expect(await screen.findByRole('link', { name: /Trường học/ })).toBeVisible()
    await first.resolve(topicList.filter((topic) => topic.hskLevel === 1))

    expect(screen.getByRole('link', { name: /Trường học/ })).toBeVisible()
    expect(screen.queryByRole('link', { name: /Gia đình/ })).not.toBeInTheDocument()
    expect(screen.getAllByRole('link')).toHaveLength(1)
    expect(screen.getByLabelText('Cấp HSK')).toHaveValue('3')
    expect(router.state.location.search).toBe('?hskLevel=3')
  })

  it('FR041_opensTheSelectedTopicDetailWhenItsCardIsClicked', async () => {
    const user = userEvent.setup()
    const router = renderPage()

    await user.click(await screen.findByRole('link', { name: /Gia đình/ }))

    expect(await screen.findByText('Chi tiết chủ đề')).toBeVisible()
    expect(router.state.location.pathname).toBe('/learn/topics/11')
    expect(screen.queryByRole('region', { name: 'Danh sách chủ đề' })).not.toBeInTheDocument()
    expect(getTopics).toHaveBeenCalledTimes(1)
  })

  it('FR041_displaysAllThreeStatusesWithoutLockedLabelsOrLockIcons', async () => {
    renderPage()
    await screen.findByRole('link', { name: /Gia đình/ })

    for (const name of ['Gia đình', 'Chào hỏi', 'Du lịch']) {
      const card = screen.getByRole('link', { name: (label) => label.includes(name) })
      expect(card).toHaveAttribute('href')
      expect(card).not.toHaveAttribute('aria-disabled', 'true')
    }
    expect(screen.getByRole('button', { name: 'Chưa học' })).toBeVisible()
    expect(screen.getByRole('button', { name: 'Đang học' })).toBeVisible()
    expect(screen.getByRole('button', { name: 'Hoàn thành' })).toBeVisible()
    expect(screen.queryByText(/LOCKED|khóa|khoá/i)).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/LOCKED|khóa|khoá/i)).not.toBeInTheDocument()
    expect(document.querySelector('svg[class*="lucide-lock"]')).not.toBeInTheDocument()
  })
})
