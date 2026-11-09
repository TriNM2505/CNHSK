import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { CurrentUser, RoleCode } from '@/features/auth/types'
import type { HanziCanvasHandle, HanziCanvasProps } from '@/features/hanzi/components/HanziCanvas'
import { ApiError } from '@/shared/api'
import { AuthContext, type AuthContextValue } from '@/shared/auth'

import { getLexeme, listRadicals, searchByRadical, searchDictionary } from '../api/dictionary'
import { addCard, listDecks } from '../api/flashcards'
import { createNote } from '../api/notes'
import type { LexemeDetail } from '../types'
import Scr024DictionarySearchPage from './Scr024DictionarySearchPage'
import Scr025DictionaryDetailPage from './Scr025DictionaryDetailPage'

vi.mock('../api/dictionary', () => ({
  getLexeme: vi.fn(),
  searchDictionary: vi.fn(),
  listRadicals: vi.fn(),
  searchByRadical: vi.fn(),
}))
vi.mock('../api/notes', () => ({ createNote: vi.fn() }))
vi.mock('../api/flashcards', () => ({ listDecks: vi.fn(), addCard: vi.fn() }))
const canvasCalls = vi.hoisted(() => ({ animate: vi.fn(), quiz: vi.fn() }))
vi.mock('@/features/hanzi/components/HanziCanvas', async () => {
  const { forwardRef, useImperativeHandle } = await import('react')
  return {
    HanziCanvas: forwardRef<HanziCanvasHandle, HanziCanvasProps>(function MockCanvas(props, ref) {
      useImperativeHandle(ref, () => ({
        animateCharacter: (done) => {
          canvasCalls.animate(props.character)
          done(false)
        },
        cancelAnimations: vi.fn(),
        cancelQuiz: vi.fn(),
        quiz: canvasCalls.quiz,
        setVisibility: vi.fn(),
      }))
      return (
        <div aria-label="Canvas mô phỏng">
          <button onClick={props.onReady}>Hoàn tất tải nét</button>
          <button onClick={() => props.onDataError?.('Missing strokes')}>Lỗi tải nét</button>
        </div>
      )
    }),
  }
})

const entry: LexemeDetail = {
  id: 101,
  kind: 'CHAR',
  text: '日',
  pinyin: 'rì',
  sinoViet: 'nhật',
  meaningVi: 'ngày; mặt trời',
  hskLevel: 1,
  audioUrl: null,
  radical: '日',
  strokeCount: 4,
  strokeData: null,
  structure: null,
  examples: [{ cn: '今天有日光。', pinyin: 'Jīntiān yǒu rìguāng.', vi: 'Hôm nay có ánh nắng.' }],
  components: [],
  containedIn: [{ id: 212, text: '生日', pinyin: 'shēngrì', meaningVi: 'sinh nhật' }],
  related: [{ id: 102, text: '月', pinyin: 'yuè', meaningVi: 'tháng; mặt trăng' }],
}
const decks = [
  { id: 501, name: 'Từ tự soạn', cardCount: 2, dueCount: 1, createdAt: '2026-11-01T00:00:00Z' },
  { id: 502, name: 'Câu tự đặt', cardCount: 0, dueCount: 0, createdAt: '2026-11-01T00:00:00Z' },
]
const reloadAuth = vi.fn()
function authValue(
  roles: RoleCode[] | null,
  status?: AuthContextValue['status'],
  id = 7,
): AuthContextValue {
  const user: CurrentUser | null = roles
    ? {
        id,
        displayName: 'Người học thử',
        email: 'learner@test.invalid',
        emailVerified: true,
        roles,
      }
    : null
  return {
    user,
    status: status ?? (user ? 'authenticated' : 'guest'),
    error: null,
    reloadCurrentUser: reloadAuth,
    setAuthenticatedUser: () => undefined,
    signOut: async () => undefined,
  }
}
function renderPage(
  path = '/dictionary/101',
  roles: RoleCode[] | null = ['LEARNER'],
  status?: AuthContextValue['status'],
) {
  const router = createMemoryRouter(
    [
      { path: '/dictionary/:id', element: <Scr025DictionaryDetailPage /> },
      { path: '/dictionary', element: <Scr024DictionarySearchPage /> },
    ],
    { initialEntries: [path] },
  )
  const view = render(
    <AuthContext.Provider value={authValue(roles, status)}>
      <RouterProvider router={router} />
    </AuthContext.Provider>,
  )
  return {
    router,
    changeAuth: (value: AuthContextValue) =>
      view.rerender(
        <AuthContext.Provider value={value}>
          <RouterProvider router={router} />
        </AuthContext.Provider>,
      ),
    ...view,
  }
}
const ready = () => screen.findByRole('heading', { name: '日' })
async function openNote() {
  await ready()
  await userEvent.click(screen.getByRole('button', { name: 'Lưu vào sổ tay' }))
}
async function openCards() {
  await ready()
  await userEvent.click(screen.getByRole('button', { name: 'Thêm vào bộ thẻ' }))
}

beforeEach(() => {
  vi.resetAllMocks()
  reloadAuth.mockResolvedValue(undefined)
  vi.mocked(getLexeme).mockResolvedValue(entry)
  vi.mocked(listDecks).mockResolvedValue(decks)
  vi.mocked(createNote).mockResolvedValue({
    id: 401,
    content: 'Ghi chú thử',
    tags: [],
    createdAt: '2026-11-04T00:00:00Z',
    updatedAt: '2026-11-04T00:00:00Z',
  })
  vi.mocked(addCard).mockResolvedValue({
    id: 601,
    front: '日',
    back: 'rì — ngày; mặt trời',
    reviewCount: 0,
    lapseCount: 0,
  })
  vi.mocked(listRadicals).mockResolvedValue([{ radical: '日', charCount: 1 }])
  vi.mocked(searchByRadical).mockResolvedValue({
    items: [{ ...entry, approximate: false }],
    total: 45,
  })
  vi.mocked(searchDictionary).mockResolvedValue({
    items: [{ ...entry, approximate: false }],
    total: 1,
  })
  vi.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue(undefined)
  vi.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(() => undefined)
})

it('shows the duplicate-note action while retaining the dictionary save draft', async () => {
  vi.mocked(createNote).mockRejectedValueOnce(
    new ApiError({
      status: 409,
      errorCode: 'NOTE_DUPLICATE',
      message: 'Trùng',
      details: { noteId: 42 },
    }),
  )
  const { router } = renderPage()
  await openNote()
  fireEvent.change(screen.getByLabelText(/^Nội dung ghi chú/), {
    target: { value: 'Nháp từ trang tra từ' },
  })
  fireEvent.click(screen.getByRole('button', { name: 'Lưu ghi chú' }))
  await screen.findByText('Từ này đã có trong sổ tay')
  expect(screen.getByLabelText(/^Nội dung ghi chú/)).toHaveValue('Nháp từ trang tra từ')
  expect(screen.getByRole('link', { name: 'Mở ghi chú đã có' })).toHaveAttribute(
    'href',
    '/library/notes?noteId=42',
  )
  expect(router.state.location.pathname).toBe('/dictionary/101')
})
afterEach(() => vi.restoreAllMocks())

describe('SCR-025 detail and media (FR-024, AT-24)', () => {
  it('shows all available detail fields and navigable related entries with preserved return context', async () => {
    const back = '/dictionary?type=PINYIN&q=ri4&page=2'
    renderPage(`/dictionary/101?${new URLSearchParams({ from: back })}`)
    await ready()
    expect(getLexeme).toHaveBeenCalledWith(101, expect.any(AbortSignal))
    for (const text of [
      'rì',
      'nhật',
      'ngày; mặt trời',
      'HSK 1',
      '4',
      '今天有日光。',
      'Jīntiān yǒu rìguāng.',
      'Hôm nay có ánh nắng.',
    ])
      expect(screen.getByText(text)).toBeVisible()
    expect(screen.getByRole('link', { name: 'Về kết quả tra cứu' })).toHaveAttribute('href', back)
    for (const name of [/生日/, /月/]) {
      const link = screen.getByRole('link', { name })
      expect(
        new URL(link.getAttribute('href')!, 'https://test.invalid').searchParams.get('from'),
      ).toBe(back)
    }
    expect(listDecks).not.toHaveBeenCalled()
  })

  it.each([
    { kind: 'WORD' as const, text: '明天', structure: null },
    { kind: 'GRAMMAR' as const, text: '是', structure: 'Chủ ngữ + 是 + danh từ' },
  ])(
    'renders $kind fields and component links without fabricating stroke animation',
    async ({ kind, text, structure }) => {
      vi.mocked(getLexeme).mockResolvedValue({
        ...entry,
        kind,
        text,
        structure,
        strokeData: {},
        components: [{ id: 102, text: '月', meaningVi: 'tháng; mặt trăng' }],
        related: [],
        containedIn: [],
        examples: [],
      })
      renderPage()
      expect(await screen.findByRole('heading', { name: text })).toBeVisible()
      expect(screen.getByRole('heading', { name: 'Chữ thành phần' })).toBeVisible()
      expect(screen.getByRole('link', { name: /月/ })).toBeVisible()
      expect(screen.queryByRole('region', { name: 'Hoạt hình nét' })).not.toBeInTheDocument()
      if (structure) expect(screen.getByText(structure)).toBeVisible()
      expect(screen.getByText('Chưa có ví dụ cho mục từ này.')).toBeVisible()
    },
  )

  it('FR024_hides audio and animation when their data is null', async () => {
    renderPage()
    await ready()
    expect(screen.queryByRole('button', { name: 'Nghe phát âm' })).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Âm thanh mục từ')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Canvas mô phỏng')).not.toBeInTheDocument()
  })

  it('uses the existing 006 canvas only for viewing and replaying animation', async () => {
    vi.mocked(getLexeme).mockResolvedValue({ ...entry, strokeData: {} })
    renderPage()
    await ready()
    const animate = screen.getByRole('button', { name: 'Xem hoạt hình nét' })
    expect(animate).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: 'Hoàn tất tải nét' }))
    await userEvent.click(animate)
    await userEvent.click(animate)
    expect(canvasCalls.animate).toHaveBeenCalledTimes(2)
    expect(canvasCalls.animate).toHaveBeenCalledWith('日')
    expect(canvasCalls.quiz).not.toHaveBeenCalled()
    expect(createNote).not.toHaveBeenCalled()
    expect(addCard).not.toHaveBeenCalled()
  })

  it('onDataError removes only the animation block and retains the readable entry and save actions', async () => {
    vi.mocked(getLexeme).mockResolvedValue({ ...entry, strokeData: {} })
    renderPage()
    await ready()
    await userEvent.click(screen.getByRole('button', { name: 'Lỗi tải nét' }))
    expect(screen.queryByRole('region', { name: 'Hoạt hình nét' })).not.toBeInTheDocument()
    expect(screen.getByText(entry.meaningVi)).toBeVisible()
    expect(screen.getByRole('button', { name: 'Lưu vào sổ tay' })).toBeVisible()
    expect(screen.getByRole('heading', { name: 'Ví dụ' })).toBeVisible()
  })

  it('plays dictionary audio repeatedly without a practice replay quota and reports playback failure', async () => {
    vi.mocked(getLexeme).mockResolvedValue({
      ...entry,
      audioUrl: 'https://media.test.invalid/original-audio.mp3',
    })
    renderPage()
    await ready()
    const listen = screen.getByRole('button', { name: 'Nghe phát âm' })
    await userEvent.click(listen)
    await userEvent.click(listen)
    expect(HTMLMediaElement.prototype.play).toHaveBeenCalledTimes(2)
    vi.mocked(HTMLMediaElement.prototype.play).mockRejectedValueOnce(new Error('Blocked'))
    await userEvent.click(listen)
    expect(await screen.findByText('Chưa thể phát âm thanh. Vui lòng thử lại.')).toBeVisible()
    await userEvent.click(listen)
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('escapes server-supplied meaning, examples and structure', async () => {
    vi.mocked(getLexeme).mockResolvedValue({
      ...entry,
      meaningVi: '<img src=x onerror=alert(1)>',
      structure: '<script>bad()</script>',
      examples: [{ cn: '<iframe src=x>', vi: '<b>văn bản</b>' }],
    })
    const view = renderPage()
    await ready()
    expect(screen.getByText('<img src=x onerror=alert(1)>')).toBeVisible()
    expect(screen.getByText('<script>bad()</script>')).toBeVisible()
    expect(view.container.querySelector('script, img, iframe')).toBeNull()
  })

  it.each(['0', '-1', 'abc', '1.5', '1e2', '9007199254740992'])(
    'does not request malformed lexeme id %s',
    async (id) => {
      renderPage(`/dictionary/${id}`)
      expect(await screen.findByRole('heading', { name: 'Không tìm thấy mục từ' })).toBeVisible()
      expect(getLexeme).not.toHaveBeenCalled()
      expect(screen.queryByRole('button', { name: 'Lưu vào sổ tay' })).not.toBeInTheDocument()
    },
  )

  it('shows server 404 as an error with a path back to search', async () => {
    vi.mocked(getLexeme).mockRejectedValue(
      new ApiError({
        status: 404,
        errorCode: 'LEXEME_NOT_FOUND',
        message: 'Mục từ không có',
        requestId: 'lookup-404',
      }),
    )
    renderPage()
    expect(await screen.findByText('Mục từ không có')).toBeVisible()
    expect(screen.getByText('Mã lỗi: lookup-404')).toBeVisible()
    expect(screen.getByRole('link', { name: 'Về kết quả tra cứu' })).toHaveAttribute(
      'href',
      '/dictionary',
    )
    expect(screen.queryByRole('button', { name: 'Thử lại' })).not.toBeInTheDocument()
  })

  it('keeps loading and server failure states distinct and supports retry', async () => {
    let reject!: (error: unknown) => void
    vi.mocked(getLexeme).mockImplementationOnce(
      () =>
        new Promise((_, fail) => {
          reject = fail
        }),
    )
    renderPage()
    expect(screen.getByLabelText('Đang tải mục từ')).toBeVisible()
    await act(async () =>
      reject(
        new ApiError({ status: 503, errorCode: 'UNAVAILABLE', message: 'Tạm thời gián đoạn' }),
      ),
    )
    expect(await screen.findByText('Tạm thời gián đoạn')).toBeVisible()
    await userEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
    await ready()
  })

  it.each([
    'https://outside.test.invalid',
    '//outside.test.invalid',
    '/admin/users',
    '/dictionary/9',
    '/dictionary#other',
  ])('rejects an unrelated return destination: %s', async (from) => {
    renderPage(`/dictionary/101?${new URLSearchParams({ from })}`)
    await ready()
    expect(screen.getByRole('link', { name: 'Về kết quả tra cứu' })).toHaveAttribute(
      'href',
      '/dictionary',
    )
  })

  it('returns from an actual detail page to the same radical filters and zero-based page', async () => {
    const { router } = renderPage('/dictionary?type=RADICAL&radical=%E6%97%A5&strokes=4&page=2')
    expect(await screen.findByText('Trang 3 / 3')).toBeVisible()
    await userEvent.click(
      within(screen.getByRole('region', { name: 'Kết quả tra cứu' })).getByRole('link', {
        name: /日/,
      }),
    )
    await ready()
    await userEvent.click(screen.getByRole('link', { name: 'Về kết quả tra cứu' }))
    expect(await screen.findByText('Trang 3 / 3')).toBeVisible()
    const params = new URLSearchParams(router.state.location.search)
    expect(params.get('radical')).toBe('日')
    expect(params.get('strokes')).toBe('4')
    expect(params.get('page')).toBe('2')
    expect(screen.getByLabelText('Số nét')).toHaveValue('4')
  })

  it('ignores a late detail response after opening another lexeme', async () => {
    let resolveOld!: (value: LexemeDetail) => void
    vi.mocked(getLexeme)
      .mockImplementationOnce(
        () =>
          new Promise((done) => {
            resolveOld = done
          }),
      )
      .mockResolvedValueOnce({ ...entry, id: 102, text: '月' })
    const { router } = renderPage()
    await waitFor(() => expect(getLexeme).toHaveBeenCalledTimes(1))
    const signal = vi.mocked(getLexeme).mock.calls[0]![1]!
    await act(async () => {
      await router.navigate('/dictionary/102')
    })
    expect(await screen.findByRole('heading', { name: '月' })).toBeVisible()
    expect(signal.aborted).toBe(true)
    await act(async () => resolveOld(entry))
    expect(screen.queryByRole('heading', { name: '日' })).not.toBeInTheDocument()
  })
})

describe('SCR-025 save permissions and forms (FR-001, FR-024, AT-24)', () => {
  it('FR024_guest sees full detail and a login link preserving the full destination', async () => {
    const path = '/dictionary/101?from=%2Fdictionary%3Fq%3Dri4#examples'
    renderPage(path, null)
    await ready()
    const login = screen.getByRole('link', { name: 'Đăng nhập để lưu' })
    expect(
      new URL(login.getAttribute('href')!, 'https://test.invalid').searchParams.get('redirect'),
    ).toBe(path)
    expect(screen.queryByRole('button', { name: 'Lưu vào sổ tay' })).not.toBeInTheDocument()
    expect(screen.getByText(entry.meaningVi)).toBeVisible()
    expect(listDecks).not.toHaveBeenCalled()
    expect(createNote).not.toHaveBeenCalled()
    expect(addCard).not.toHaveBeenCalled()
  })

  it.each(['ADMIN', 'TEACHER', 'MODERATOR', 'CONTENT_EDITOR', 'ACCOUNTANT'] satisfies RoleCode[])(
    'shows the approved learner-role message for %s without private requests',
    async (role) => {
      renderPage('/dictionary/101', [role])
      expect(await screen.findByText('Cần quyền học viên để lưu')).toBeVisible()
      expect(screen.queryByRole('link', { name: 'Đăng nhập để lưu' })).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Lưu vào sổ tay' })).not.toBeInTheDocument()
      expect(listDecks).not.toHaveBeenCalled()
    },
  )

  it('accepts LEARNER combined with ADMIN', async () => {
    renderPage('/dictionary/101', ['ADMIN', 'LEARNER'])
    await ready()
    expect(screen.getByRole('button', { name: 'Lưu vào sổ tay' })).toBeVisible()
  })

  it.each(['loading', 'error'] as const)(
    'keeps public detail but no private actions while auth is %s',
    async (status) => {
      renderPage('/dictionary/101', null, status)
      await ready()
      expect(screen.queryByRole('link', { name: 'Đăng nhập để lưu' })).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Lưu vào sổ tay' })).not.toBeInTheDocument()
      expect(listDecks).not.toHaveBeenCalled()
      if (status === 'error') {
        await userEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
        expect(reloadAuth).toHaveBeenCalledTimes(1)
      }
    },
  )

  it('FR024_prefills an editable note and sends the edited snapshot with dictionary provenance', async () => {
    renderPage()
    await openNote()
    expect(screen.getByLabelText('Tiêu đề')).toHaveValue('日')
    expect(screen.getByLabelText(/Nội dung ghi chú/)).toHaveValue('日\nrì\nngày; mặt trời')
    fireEvent.change(screen.getByLabelText('Tiêu đề'), { target: { value: 'Ghi chú của tôi' } })
    fireEvent.change(screen.getByLabelText(/Nội dung ghi chú/), {
      target: { value: 'Tự đặt câu sau buổi học.' },
    })
    fireEvent.change(screen.getByLabelText('Thẻ ghi chú'), { target: { value: 'tự học, hsk1' } })
    await userEvent.click(screen.getByRole('button', { name: 'Lưu ghi chú' }))
    expect(await screen.findByText('Đã lưu vào sổ tay.')).toBeVisible()
    expect(createNote).toHaveBeenCalledWith({
      title: 'Ghi chú của tôi',
      content: 'Tự đặt câu sau buổi học.',
      tags: ['tự học', 'hsk1'],
      lexemeId: 101,
      sourceType: 'DICTIONARY',
    })
    expect(listDecks).not.toHaveBeenCalled()
  })

  it.each([
    { content: '  ', tags: '' },
    { content: 'ghi chú', tags: Array.from({ length: 11 }, (_, index) => `thẻ${index}`).join(',') },
    { content: 'ghi chú', tags: 'x'.repeat(51) },
  ])(
    'validates note limits without dropping the edited draft: $tags',
    async ({ content, tags }) => {
      renderPage()
      await openNote()
      fireEvent.change(screen.getByLabelText(/Nội dung ghi chú/), { target: { value: content } })
      fireEvent.change(screen.getByLabelText('Thẻ ghi chú'), { target: { value: tags } })
      await userEvent.click(screen.getByRole('button', { name: 'Lưu ghi chú' }))
      expect(screen.getByRole('alert')).toHaveTextContent('Nội dung cần 1–10.000 ký tự')
      expect(screen.getByLabelText(/Nội dung ghi chú/)).toHaveValue(content)
      expect(createNote).not.toHaveBeenCalled()
    },
  )

  it.each([
    {
      status: 403,
      code: 'COLLECTION_NOT_OWNED',
      message: 'Bạn không có quyền lưu vào sổ tay hoặc bộ thẻ này.',
    },
    { status: 422, code: 'LIBRARY_LIMIT_REACHED', message: 'Đã đạt giới hạn ghi chú' },
    { status: 422, code: 'VALIDATION_FAILED', message: 'Nội dung không hợp lệ' },
  ])('preserves the note on $code and allows a manual retry', async ({ status, code, message }) => {
    vi.mocked(createNote).mockRejectedValueOnce(
      new ApiError({ status, errorCode: code, message, requestId: 'note-save' }),
    )
    renderPage()
    await openNote()
    fireEvent.change(screen.getByLabelText(/Nội dung ghi chú/), {
      target: { value: 'Bản nháp cần giữ.' },
    })
    await userEvent.click(screen.getByRole('button', { name: 'Lưu ghi chú' }))
    expect(await screen.findByText(message)).toBeVisible()
    expect(screen.getByLabelText(/Nội dung ghi chú/)).toHaveValue('Bản nháp cần giữ.')
    expect(screen.queryByText('Đã lưu vào sổ tay.')).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Lưu ghi chú' }))
    expect(await screen.findByText('Đã lưu vào sổ tay.')).toBeVisible()
  })

  it('FR024_loads only the owned deck list when opened and reports duplicate without losing selection', async () => {
    vi.mocked(addCard).mockRejectedValueOnce(
      new ApiError({ status: 409, errorCode: 'CARD_DUPLICATE', message: 'Trùng thẻ' }),
    )
    renderPage()
    await ready()
    expect(listDecks).not.toHaveBeenCalled()
    await openCards()
    const select = await screen.findByRole('combobox', { name: 'Bộ thẻ' })
    expect(listDecks).toHaveBeenCalledWith(expect.any(AbortSignal))
    expect(within(select).getAllByRole('option')).toHaveLength(3)
    await userEvent.click(screen.getByRole('button', { name: 'Thêm thẻ' }))
    expect(screen.getByText('Chọn một bộ thẻ của bạn.')).toBeVisible()
    expect(addCard).not.toHaveBeenCalled()
    fireEvent.change(select, { target: { value: '501' } })
    await userEvent.click(screen.getByRole('button', { name: 'Thêm thẻ' }))
    expect(
      await screen.findByText('Mục từ đã có trong bộ thẻ này. Hãy chọn bộ khác.'),
    ).toBeVisible()
    expect(select).toHaveValue('501')
    expect(addCard).toHaveBeenCalledWith(501, { lexemeId: 101 })
    fireEvent.change(select, { target: { value: '502' } })
    await userEvent.click(screen.getByRole('button', { name: 'Thêm thẻ' }))
    expect(await screen.findByText('Đã thêm vào bộ thẻ.')).toBeVisible()
    expect(addCard).toHaveBeenLastCalledWith(502, { lexemeId: 101 })
  })

  it('offers deck creation when there are no owned decks and does not post an invented destination', async () => {
    vi.mocked(listDecks).mockResolvedValue([])
    renderPage()
    await openCards()
    expect(await screen.findByRole('link', { name: 'Tạo bộ thẻ' })).toHaveAttribute(
      'href',
      '/library/decks',
    )
    expect(screen.getByRole('button', { name: 'Thêm thẻ' })).toBeDisabled()
    expect(addCard).not.toHaveBeenCalled()
  })

  it('supports retrying a deck-list failure without showing stale deck choices', async () => {
    vi.mocked(listDecks).mockRejectedValueOnce(
      new ApiError({ status: 503, errorCode: 'UNAVAILABLE', message: 'Chưa tải được bộ' }),
    )
    renderPage()
    await openCards()
    expect(await screen.findByText('Chưa tải được bộ')).toBeVisible()
    expect(screen.queryByRole('combobox', { name: 'Bộ thẻ' })).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
    expect(await screen.findByRole('combobox', { name: 'Bộ thẻ' })).toBeVisible()
  })

  it('deduplicates repeated submit events and ignores a late save success after logout', async () => {
    let resolveSave!: (value: Awaited<ReturnType<typeof createNote>>) => void
    vi.mocked(createNote).mockImplementation(
      () =>
        new Promise((done) => {
          resolveSave = done
        }),
    )
    const { changeAuth } = renderPage()
    await openNote()
    const form = screen.getByRole('button', { name: 'Lưu ghi chú' }).closest('form')!
    fireEvent.submit(form)
    fireEvent.submit(form)
    expect(createNote).toHaveBeenCalledTimes(1)
    expect(screen.getByRole('button', { name: 'Lưu ghi chú' })).toBeDisabled()
    changeAuth(authValue(null))
    expect(await screen.findByRole('link', { name: 'Đăng nhập để lưu' })).toBeVisible()
    await act(async () =>
      resolveSave({
        id: 401,
        content: 'Bản cũ',
        tags: [],
        createdAt: '2026-11-04T00:00:00Z',
        updatedAt: '2026-11-04T00:00:00Z',
      }),
    )
    expect(screen.queryByText('Đã lưu vào sổ tay.')).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/Nội dung ghi chú/)).not.toBeInTheDocument()
  })

  it('aborts the previous user deck request and clears its private options on account switch', async () => {
    let resolveDecks!: (value: typeof decks) => void
    vi.mocked(listDecks).mockImplementationOnce(
      () =>
        new Promise((done) => {
          resolveDecks = done
        }),
    )
    const { changeAuth } = renderPage()
    await openCards()
    await waitFor(() => expect(listDecks).toHaveBeenCalledTimes(1))
    const signal = vi.mocked(listDecks).mock.calls[0]![0]!
    changeAuth(authValue(['LEARNER'], undefined, 8))
    expect(signal.aborted).toBe(true)
    await act(async () => resolveDecks(decks))
    expect(screen.queryByRole('combobox', { name: 'Bộ thẻ' })).not.toBeInTheDocument()
    expect(screen.queryByText('Từ tự soạn (2 thẻ)')).not.toBeInTheDocument()
  })
})
