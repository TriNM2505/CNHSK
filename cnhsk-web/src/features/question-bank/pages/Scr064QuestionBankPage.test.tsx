import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '@/shared/api'

import { getKnowledgeTree } from '../api/knowledge'
import { listQuestions } from '../api/questions'
import { questionSearchError } from '../questionListQuery'
import type { KnowledgeTree, QuestionPage, QuestionSummary } from '../types'
import Scr064QuestionBankPage from './Scr064QuestionBankPage'

vi.mock('../api/questions', () => ({ listQuestions: vi.fn() }))
vi.mock('../api/knowledge', () => ({ getKnowledgeTree: vi.fn() }))

// Original response fixtures; no preview module or real service is used by these tests.
function question(id: number): QuestionSummary {
  return {
    id,
    code: `TEST_${id}`,
    qtype: 'MULTIPLE_CHOICE',
    hskLevel: 1,
    difficulty: 2,
    stemSummary: `Nội dung tự soạn ${id}`,
    source: 'IMPORT',
    status: 'APPROVED',
    primaryKid: 'NP1-901',
    secondaryKids: ['TV2-901'],
    used: id === 901,
  }
}

const firstPage: QuestionPage = {
  items: [901, 905, 903, 902, 904, 906, 907, 908, 909, 910].map(question),
  page: 0,
  size: 10,
  totalElements: 12,
}
const secondPage: QuestionPage = {
  items: [911, 912].map(question),
  page: 1,
  size: 10,
  totalElements: 12,
}
const tree: KnowledgeTree = {
  nodes: [
    {
      kid: 'NP1-901',
      domain: 'NP',
      hskLevel: 1,
      nameVi: 'Câu giới thiệu tự soạn',
      nameZh: '示例',
      prereqKids: [],
      supportKids: [],
    },
    {
      kid: 'TV2-901',
      domain: 'TV',
      hskLevel: 2,
      nameVi: 'Từ hoạt động tự soạn',
      nameZh: '示例',
      prereqKids: [],
      supportKids: [],
    },
  ],
}
const listMock = vi.mocked(listQuestions)
const treeMock = vi.mocked(getKnowledgeTree)
const fetchMock = vi.fn<typeof fetch>()

function renderPage(entry = '/content/questions') {
  const router = createMemoryRouter(
    [
      { path: '/content/questions', element: <Scr064QuestionBankPage /> },
      { path: '/content/questions/:id', element: <h1>Chi tiết câu hỏi</h1> },
    ],
    { initialEntries: [entry] },
  )
  return { router, ...render(<RouterProvider router={router} />) }
}

function params(router: ReturnType<typeof renderPage>['router']) {
  return new URLSearchParams(router.state.location.search)
}

async function table() {
  return screen.findByRole('table', { name: 'Danh sách câu hỏi' })
}

beforeEach(() => {
  listMock.mockReset().mockResolvedValue(firstPage)
  treeMock.mockReset().mockResolvedValue(tree)
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => vi.unstubAllGlobals())

describe('SCR-064 question bank (FR-017, FR-018, FR-026 · AT-26)', () => {
  it('FR017_AT26_sends contract defaults and uses only the mocked API wrappers', async () => {
    const { router } = renderPage()
    await table()
    expect(listMock).toHaveBeenCalledWith(
      {
        hskLevel: undefined,
        qtype: undefined,
        status: undefined,
        kid: undefined,
        q: undefined,
        page: 0,
        size: 10,
      },
      expect.any(AbortSignal),
    )
    expect(treeMock).toHaveBeenCalledWith({}, expect.any(AbortSignal))
    expect(Object.fromEntries(params(router))).toEqual({ page: '0', size: '10' })
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('FR017_AT26_restores every filter and Unicode search from a shared URL', async () => {
    const query = new URLSearchParams({
      hskLevel: '2',
      qtype: 'FILL_BLANK',
      status: 'ARCHIVED',
      kid: 'TV2-901',
      q: '今天 & 明天?',
      page: '2',
      size: '50',
    })
    const { router } = renderPage(`/content/questions?${query}`)
    await table()
    expect(listMock).toHaveBeenCalledWith(
      {
        hskLevel: 2,
        qtype: 'FILL_BLANK',
        status: 'ARCHIVED',
        kid: 'TV2-901',
        q: '今天 & 明天?',
        page: 2,
        size: 10,
      },
      expect.any(AbortSignal),
    )
    expect(screen.getByLabelText('Cấp HSK')).toHaveValue('2')
    expect(screen.getByLabelText('Loại câu hỏi')).toHaveValue('FILL_BLANK')
    expect(screen.getByLabelText('Trạng thái')).toHaveValue('ARCHIVED')
    expect(screen.getByLabelText('Nút năng lực')).toHaveValue('TV2-901')
    expect(screen.getByLabelText('Tìm câu hỏi')).toHaveValue('今天 & 明天?')
    expect(params(router).get('size')).toBe('10')
  })

  it.each([
    ['hskLevel', '0'],
    ['hskLevel', '4'],
    ['hskLevel', '9'],
    ['hskLevel', 'abc'],
    ['qtype', 'UNKNOWN'],
    ['qtype', 'constructor'],
    ['status', 'BROKEN'],
    ['page', '-1'],
    ['page', '1.5'],
    ['page', 'abc'],
    ['page', '999999999999999999999'],
  ])(
    'FR017_AT26_normalizes invalid %s=%s by replace while retaining valid filters',
    async (key, value) => {
      const query = new URLSearchParams({
        status: 'APPROVED',
        kid: 'NP1-999',
        q: '今天',
        page: '3',
        size: '100',
      })
      query.set(key, value)
      const { router } = renderPage(`/content/questions?${query}`)
      await table()
      await waitFor(() => expect(params(router).get(key)).toBe(key === 'page' ? '0' : null))
      expect(router.state.historyAction).toBe('REPLACE')
      expect(params(router).get('kid')).toBe('NP1-999')
      expect(params(router).get('q')).toBe('今天')
      expect(params(router).get('size')).toBe('10')
      expect(listMock.mock.lastCall?.[0]?.kid).toBe('NP1-999')
      expect(listMock).toHaveBeenCalledTimes(1)
    },
  )

  it.each(['a', 'a'.repeat(101), '𠀀', '😀', '𠀀'.repeat(101), '😀'.repeat(101)])(
    'FR017_AT26_removes an invalid URL search without sending it',
    async (q) => {
      const { router } = renderPage(
        `/content/questions?${new URLSearchParams({ q, kid: 'NP1-901' })}`,
      )
      await table()
      expect(params(router).has('q')).toBe(false)
      expect(params(router).get('kid')).toBe('NP1-901')
      expect(listMock.mock.lastCall?.[0]?.q).toBeUndefined()
    },
  )

  it('FR026_AT26_offers only HSK one through three and every contract type and status', async () => {
    renderPage()
    await table()
    expect(
      within(screen.getByLabelText('Cấp HSK'))
        .getAllByRole('option')
        .map((option) => (option as HTMLOptionElement).value),
    ).toEqual(['', '1', '2', '3'])
    expect(within(screen.getByLabelText('Loại câu hỏi')).getAllByRole('option')).toHaveLength(8)
    expect(within(screen.getByLabelText('Trạng thái')).getAllByRole('option')).toHaveLength(5)
  })

  it.each([
    ['Cấp HSK', '2', 'hskLevel', 2],
    ['Loại câu hỏi', 'FILL_BLANK', 'qtype', 'FILL_BLANK'],
    ['Trạng thái', 'ARCHIVED', 'status', 'ARCHIVED'],
    ['Nút năng lực', 'TV2-901', 'kid', 'TV2-901'],
  ])(
    'FR017_AT26_updates %s in the URL and resets the page without filtering response rows',
    async (label, value, key, expected) => {
      const { router } = renderPage('/content/questions?q=TEST&page=3')
      await table()
      await waitFor(() => expect(screen.getByLabelText('Nút năng lực')).not.toBeDisabled())
      fireEvent.change(screen.getByLabelText(label), { target: { value } })
      await waitFor(() =>
        expect(listMock.mock.lastCall?.[0]).toMatchObject({
          [key]: expected,
          q: 'TEST',
          page: 0,
          size: 10,
        }),
      )
      expect(params(router).get(key)).toBe(value)
      expect(params(router).get('page')).toBe('0')
      expect(within(await table()).getAllByRole('row')).toHaveLength(11)
    },
  )

  it('FR017_AT26_keeps typed search local until the search button is pressed', async () => {
    const { router } = renderPage('/content/questions?hskLevel=2&page=3')
    await table()
    fireEvent.change(screen.getByLabelText('Tìm câu hỏi'), {
      target: { value: '  今天 & 明天?  ' },
    })
    expect(listMock).toHaveBeenCalledTimes(1)
    expect(params(router).has('q')).toBe(false)
    fireEvent.click(screen.getByRole('button', { name: 'Tìm kiếm' }))
    await waitFor(() =>
      expect(listMock.mock.lastCall?.[0]).toMatchObject({
        q: '今天 & 明天?',
        hskLevel: 2,
        page: 0,
      }),
    )
    expect(params(router).get('q')).toBe('今天 & 明天?')
  })

  it('FR017_AT26_submits a valid search with Enter', async () => {
    const user = userEvent.setup()
    const { router } = renderPage()
    await table()
    await user.type(screen.getByLabelText('Tìm câu hỏi'), 'TEST{enter}')
    await waitFor(() => expect(params(router).get('q')).toBe('TEST'))
    expect(listMock.mock.lastCall?.[0]?.q).toBe('TEST')
  })

  it.each(['a', '😀'])('FR017_AT26_does not submit an invalid draft search', async (value) => {
    const { router } = renderPage('/content/questions?q=TEST&page=1')
    await table()
    fireEvent.change(screen.getByLabelText('Tìm câu hỏi'), { target: { value } })
    fireEvent.click(screen.getByRole('button', { name: 'Tìm kiếm' }))
    expect(screen.getByText('Nhập từ 2 đến 100 ký tự hoặc để trống.')).toBeVisible()
    expect(screen.getByLabelText('Tìm câu hỏi')).toHaveAttribute('aria-invalid', 'true')
    expect(params(router).get('q')).toBe('TEST')
    expect(params(router).get('page')).toBe('1')
    expect(listMock).toHaveBeenCalledTimes(1)
  })

  it.each(['ab', 'a'.repeat(100)])(
    'FR017_AT26_accepts a search at the contract boundaries',
    async (q) => {
      const { router } = renderPage()
      await table()
      fireEvent.change(screen.getByLabelText('Tìm câu hỏi'), { target: { value: q } })
      fireEvent.click(screen.getByRole('button', { name: 'Tìm kiếm' }))
      await waitFor(() => expect(params(router).get('q')).toBe(q))
      expect(listMock.mock.lastCall?.[0]?.q).toBe(q)
    },
  )

  it.each(['𠀀', '😀'])(
    'FR017_AT26_checks Unicode %s at 1/2 and 100/101 code points',
    async (character) => {
      expect(questionSearchError(character)).toBeTruthy()
      expect(questionSearchError(character.repeat(2))).toBeUndefined()
      expect(questionSearchError(character.repeat(100))).toBeUndefined()
      expect(questionSearchError(character.repeat(101))).toBeTruthy()
      const { router } = renderPage('/content/questions?hskLevel=3&kid=NP1-901&q=TEST&page=1')
      await table()
      const input = screen.getByLabelText('Tìm câu hỏi')
      expect(input).not.toHaveAttribute('maxlength')
      for (const count of [2, 100, 101]) {
        fireEvent.change(input, { target: { value: character.repeat(count) } })
        const expected = character.repeat(Math.min(count, 100))
        expect(input).toHaveValue(expected)
        fireEvent.click(screen.getByRole('button', { name: 'Tìm kiếm' }))
        await waitFor(() => expect(listMock.mock.lastCall?.[0]?.q).toBe(expected))
        expect(params(router).get('q')).toBe(expected)
        expect(params(router).get('hskLevel')).toBe('3')
        expect(params(router).get('kid')).toBe('NP1-901')
        expect(params(router).get('page')).toBe('0')
        expect(params(router).get('size')).toBe('10')
      }
    },
  )

  it('FR017_AT26_preserves the filter URL and request id on server validation errors and retries', async () => {
    listMock.mockRejectedValueOnce(
      new ApiError({
        status: 422,
        errorCode: 'VALIDATION_FAILED',
        message: 'Bộ lọc không hợp lệ.',
        requestId: 'req-list-validation',
        details: [{ field: 'q', message: 'Kiểm tra từ khoá.' }],
      }),
    )
    const { router } = renderPage('/content/questions?hskLevel=3&kid=NP1-901&q=TEST&page=1')
    expect(await screen.findByText('Bộ lọc không hợp lệ.')).toBeVisible()
    expect(screen.getByText('Mã lỗi: req-list-validation')).toBeVisible()
    expect(params(router).get('q')).toBe('TEST')
    expect(params(router).get('page')).toBe('1')
    fireEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
    await table()
    expect(listMock.mock.lastCall?.[0]).toMatchObject({
      hskLevel: 3,
      kid: 'NP1-901',
      q: 'TEST',
      page: 1,
      size: 10,
    })
  })

  it('FR017_AT26_can clear a committed search while retaining other filters', async () => {
    const { router } = renderPage('/content/questions?q=TEST&status=APPROVED&page=2')
    await table()
    fireEvent.change(screen.getByLabelText('Tìm câu hỏi'), { target: { value: '' } })
    fireEvent.click(screen.getByRole('button', { name: 'Tìm kiếm' }))
    await waitFor(() =>
      expect(listMock.mock.lastCall?.[0]).toMatchObject({
        q: undefined,
        status: 'APPROVED',
        page: 0,
      }),
    )
    expect(params(router).has('q')).toBe(false)
  })

  it('FR018_AT26_preserves every response row and its order instead of sorting or filtering locally', async () => {
    renderPage('/content/questions?hskLevel=3&qtype=ESSAY&status=ARCHIVED&kid=NP1-999&q=missing')
    const result = await table()
    const rows = within(result).getAllByRole('row').slice(1)
    expect(rows.map((row) => within(row).getAllByRole('cell')[0]?.textContent)).toEqual(
      firstPage.items.map((item) => item.code),
    )
    expect(
      within(result)
        .getAllByRole('columnheader')
        .map((header) => header.textContent),
    ).toEqual([
      'Mã câu hỏi',
      'Tóm tắt nội dung',
      'Loại câu hỏi',
      'HSK',
      'Nguồn / Trạng thái',
      'Nút năng lực',
      'Đã dùng',
    ])
    expect(within(rows[0]!).getByText('Chính: NP1-901')).toBeVisible()
    expect(within(rows[0]!).getByText('Phụ: TV2-901')).toBeVisible()
    expect(within(rows[0]!).getByText('Đã dùng')).toBeVisible()
  })

  it('FR018_AT26_displays nullable codes and source and status metadata from the response', async () => {
    listMock.mockResolvedValue({
      ...firstPage,
      items: [
        {
          ...question(920),
          code: null,
          source: 'AI',
          status: 'PENDING_REVIEW',
          primaryKid: null,
          secondaryKids: [],
          used: false,
        },
      ],
    })
    renderPage()
    const result = await table()
    expect(within(result).getByText('—')).toBeVisible()
    expect(within(result).getByText('AI')).toBeVisible()
    expect(within(result).getByText('Chờ duyệt')).toBeVisible()
    expect(within(result).getByText('Chính: Chưa gắn nút')).toBeVisible()
    expect(within(result).getByText('Chưa dùng')).toBeVisible()
  })

  it('FR017_FR018_AT26_requests page two and renders its two returned rows', async () => {
    listMock.mockResolvedValueOnce(firstPage).mockResolvedValueOnce(secondPage)
    const { router } = renderPage('/content/questions?q=TEST&hskLevel=2')
    await table()
    fireEvent.click(screen.getByRole('button', { name: 'Sau' }))
    expect(await screen.findByText('Trang 2 / 2')).toBeVisible()
    expect(within(await table()).getAllByRole('row')).toHaveLength(3)
    expect(listMock.mock.lastCall?.[0]).toMatchObject({ q: 'TEST', hskLevel: 2, page: 1, size: 10 })
    expect(params(router).get('page')).toBe('1')
    expect(screen.getByRole('button', { name: 'Sau' })).toBeDisabled()
    fireEvent.click(screen.getByRole('button', { name: 'Trước' }))
    await waitFor(() => expect(params(router).get('page')).toBe('0'))
  })

  it('FR018_AT26_displays the response page and total instead of inventing them from the URL', async () => {
    listMock.mockResolvedValue({ ...firstPage, page: 1, totalElements: 35 })
    renderPage('/content/questions?page=0')
    expect(await screen.findByText('Trang 2 / 4')).toBeVisible()
    expect(screen.getByText('Tổng cộng 35 câu hỏi')).toBeVisible()
  })

  it('FR026_AT26_restores filters and search after Back Forward and reloading a copied URL', async () => {
    const first = renderPage('/content/questions?hskLevel=1&q=TEST')
    await table()
    fireEvent.change(screen.getByLabelText('Cấp HSK'), { target: { value: '2' } })
    await waitFor(() => expect(params(first.router).get('hskLevel')).toBe('2'))
    await act(() => first.router.navigate(-1))
    await waitFor(() => expect(screen.getByLabelText('Cấp HSK')).toHaveValue('1'))
    await act(() => first.router.navigate(1))
    await waitFor(() => expect(screen.getByLabelText('Cấp HSK')).toHaveValue('2'))
    const link = `${first.router.state.location.pathname}${first.router.state.location.search}`
    first.unmount()
    renderPage(link)
    await table()
    expect(screen.getByLabelText('Cấp HSK')).toHaveValue('2')
    expect(screen.getByLabelText('Tìm câu hỏi')).toHaveValue('TEST')
    expect(listMock.mock.lastCall?.[0]).toMatchObject({ hskLevel: 2, q: 'TEST' })
  })

  it.each(['click', 'Enter'])(
    'FR026_AT26_opens a row with %s and keeps the full query when returning',
    async (action) => {
      const { router } = renderPage('/content/questions?hskLevel=2&q=TEST&kid=TV2-901&page=1')
      const result = await table()
      const originalSearch = router.state.location.search
      const row = within(result).getByRole('row', { name: 'Mở câu hỏi TEST_901' })
      if (action === 'click') fireEvent.click(row)
      else fireEvent.keyDown(row, { key: 'Enter' })
      expect(await screen.findByRole('heading', { name: 'Chi tiết câu hỏi' })).toBeVisible()
      expect(router.state.location.pathname).toBe('/content/questions/901')
      expect(router.state.location.search).toBe(originalSearch)
      await act(() => router.navigate(-1))
      await table()
      expect(router.state.location.search).toBe(originalSearch)
      expect(screen.getByLabelText('Cấp HSK')).toHaveValue('2')
    },
  )

  it('FR026_AT26_shows the loading state until the response arrives', async () => {
    listMock.mockReturnValue(new Promise<QuestionPage>(() => undefined))
    renderPage()
    expect(screen.getByRole('status', { name: 'Đang tải câu hỏi' })).toBeVisible()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })

  it('FR026_AT26_shows an empty result for an unknown KID and clears all filters', async () => {
    listMock.mockResolvedValue({ items: [], page: 3, size: 10, totalElements: 0 })
    const { router } = renderPage('/content/questions?hskLevel=2&q=TEST&kid=NP1-999&page=3')
    expect(
      await screen.findByRole('heading', { name: 'Chưa có câu hỏi trong phạm vi này' }),
    ).toBeVisible()
    expect(screen.getByLabelText('Nút năng lực')).toHaveValue('NP1-999')
    fireEvent.click(screen.getAllByRole('button', { name: 'Xoá bộ lọc' })[1]!)
    await waitFor(() =>
      expect(Object.fromEntries(params(router))).toEqual({ page: '0', size: '10' }),
    )
    expect(screen.getByLabelText('Tìm câu hỏi')).toHaveValue('')
    expect(listMock.mock.lastCall?.[0]).toMatchObject({
      hskLevel: undefined,
      q: undefined,
      kid: undefined,
      page: 0,
    })
  })

  it('FR026_AT26_returns from a page beyond the end without dropping filters', async () => {
    listMock.mockResolvedValue({ items: [], page: 9, size: 10, totalElements: 12 })
    const { router } = renderPage('/content/questions?q=TEST&status=ARCHIVED&page=9')
    fireEvent.click(await screen.findByRole('button', { name: 'Về trang đầu' }))
    await waitFor(() =>
      expect(listMock.mock.lastCall?.[0]).toMatchObject({ q: 'TEST', status: 'ARCHIVED', page: 0 }),
    )
    expect(params(router).get('status')).toBe('ARCHIVED')
  })

  it('FR026_AT26_retries a failed question request successfully', async () => {
    listMock.mockRejectedValueOnce(
      new ApiError({
        status: 503,
        errorCode: 'NETWORK_ERROR',
        message: 'Chưa tải được câu hỏi.',
        requestId: 'test-t02',
      }),
    )
    renderPage()
    expect(await screen.findByRole('heading', { name: 'Không thể tải kho câu hỏi' })).toBeVisible()
    expect(screen.getByText('Chưa tải được câu hỏi.')).toBeVisible()
    expect(screen.getByText('Mã lỗi: test-t02')).toBeVisible()
    fireEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
    await table()
    expect(listMock).toHaveBeenCalledTimes(2)
  })

  it('FR026_AT26_retries node choices independently of the question page', async () => {
    treeMock.mockRejectedValueOnce(
      new ApiError({ status: 503, errorCode: 'NETWORK_ERROR', message: 'Chưa tải được cây.' }),
    )
    renderPage()
    await table()
    expect(await screen.findByRole('heading', { name: 'Không thể tải nút năng lực' })).toBeVisible()
    fireEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
    await waitFor(() =>
      expect(
        screen.getByRole('option', { name: 'TV2-901 · Từ hoạt động tự soạn' }),
      ).toBeInTheDocument(),
    )
    expect(treeMock).toHaveBeenCalledTimes(2)
    expect(listMock).toHaveBeenCalledTimes(1)
  })
})
