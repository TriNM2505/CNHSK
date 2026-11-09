import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { RoleCode } from '@/features/auth/types'
import { ApiError } from '@/shared/api'
import { AuthContext, type AuthContextValue } from '@/shared/auth'

import {
  createNote,
  deleteNote,
  getNote,
  listNotes,
  listTrash,
  restoreNote,
  updateNote,
} from '../api/notes'
import type { Note, NotePage, TrashNote } from '../types'
import Scr027PersonalNotesPage from './Scr027PersonalNotesPage'

vi.mock('../api/notes', () => ({
  createNote: vi.fn(),
  deleteNote: vi.fn(),
  getNote: vi.fn(),
  listNotes: vi.fn(),
  listTrash: vi.fn(),
  restoreNote: vi.fn(),
  updateNote: vi.fn(),
}))

const note: Note = {
  id: 1,
  title: 'Ghi chú tự soạn',
  content: 'Nội dung gốc',
  tags: ['hsk1', 'a,b'],
  lexemeId: 101,
  sourceType: 'DICTIONARY',
  createdAt: '2026-11-01T02:00:00Z',
  updatedAt: '2026-11-02T03:00:00Z',
}
const page: NotePage = {
  items: [{ ...note, snippet: 'Ghi nhớ <script>gia đình</script> <b>HTML</b>.' }],
  total: 1,
}
const trashNote: TrashNote = {
  ...page.items[0]!,
  id: 3,
  title: 'Ghi chú đã xoá',
  deletedAt: '2026-11-03T04:00:00Z',
  restorableUntil: '2026-12-03T04:00:00Z',
}

function authValue(id: number | null = 7, roles: RoleCode[] = ['LEARNER']): AuthContextValue {
  return {
    user:
      id === null
        ? null
        : {
            id,
            displayName: 'Người học thử',
            email: 'learner@test.invalid',
            emailVerified: true,
            roles,
          },
    status: id === null ? 'guest' : 'authenticated',
    error: null,
    reloadCurrentUser: async () => undefined,
    setAuthenticatedUser: () => undefined,
    signOut: async () => undefined,
  }
}

function renderPage(path = '/library/notes', auth = authValue()) {
  const router = createMemoryRouter(
    [{ path: '/library/notes', element: <Scr027PersonalNotesPage /> }],
    { initialEntries: [path] },
  )
  const view = render(
    <AuthContext.Provider value={auth}>
      <RouterProvider router={router} />
    </AuthContext.Provider>,
  )
  return {
    ...view,
    router,
    changeAuth: (next: AuthContextValue) =>
      view.rerender(
        <AuthContext.Provider value={next}>
          <RouterProvider router={router} />
        </AuthContext.Provider>,
      ),
  }
}

async function ready() {
  await screen.findByRole('button', { name: 'Mở ghi chú' })
}
async function newDraft(content = 'Bản nháp cần giữ') {
  await userEvent.click(screen.getAllByRole('button', { name: 'Tạo ghi chú' })[0]!)
  fireEvent.change(screen.getByLabelText(/^Nội dung/), { target: { value: content } })
}
function save() {
  fireEvent.submit(screen.getByRole('form', { name: 'Soạn ghi chú' }))
}
function error(status: number, code: string, details?: unknown) {
  return new ApiError({
    status,
    errorCode: code,
    message: 'Thông báo kiểm thử',
    requestId: 'notes-error',
    details,
  })
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(listNotes).mockResolvedValue(page)
  vi.mocked(listTrash).mockResolvedValue([trashNote])
  vi.mocked(getNote).mockResolvedValue(note)
  vi.mocked(createNote).mockResolvedValue({
    ...note,
    id: 2,
    title: null,
    content: 'Bản nháp cần giữ',
    lexemeId: null,
    sourceType: 'MANUAL',
    tags: [],
  })
  vi.mocked(updateNote).mockResolvedValue(note)
  vi.mocked(deleteNote).mockResolvedValue(undefined)
  vi.mocked(restoreNote).mockResolvedValue(note)
})

describe('SCR-027 personal notes (FR-026, AT-26)', () => {
  it('cancels a deep-link load when its ID is removed and ignores the late result', async () => {
    let finish!: (value: Note) => void
    vi.mocked(getNote).mockReturnValueOnce(
      new Promise<Note>((resolve) => {
        finish = resolve
      }),
    )
    const { router } = renderPage('/library/notes?noteId=42')
    await screen.findByText('Đang mở ghi chú…')
    const signal = vi.mocked(getNote).mock.calls[0]![1]!
    await act(async () => {
      await router.navigate('/library/notes')
    })
    expect(signal.aborted).toBe(true)
    expect(screen.queryByText('Đang mở ghi chú…')).not.toBeInTheDocument()
    await act(async () => {
      finish(note)
    })
    expect(screen.queryByRole('form', { name: 'Soạn ghi chú' })).not.toBeInTheDocument()
    expect(screen.getByText('Chọn một ghi chú')).toBeVisible()
  })

  it('opens an existing note requested by ID and retries a failed load', async () => {
    vi.mocked(getNote).mockRejectedValueOnce(error(403, 'COLLECTION_NOT_OWNED'))
    renderPage('/library/notes?noteId=42')
    await screen.findByText('Bạn không có quyền truy cập ghi chú này.')
    expect(getNote).toHaveBeenCalledWith(42, expect.any(AbortSignal))
    expect(screen.queryByRole('form', { name: 'Soạn ghi chú' })).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: /Thử lại/ }))
    await screen.findByRole('form', { name: 'Soạn ghi chú' })
    expect(screen.getByLabelText(/^Nội dung/)).toHaveValue(note.content)
  })

  it.each(['0', '-1', '1.5', 'NaN', '9007199254740992'])(
    'does not fetch invalid noteId=%s from the URL',
    async (id) => {
      renderPage(`/library/notes?noteId=${id}`)
      await screen.findByText('ID ghi chú không hợp lệ.')
      expect(getNote).not.toHaveBeenCalled()
    },
  )
  it('loads exactly one unfiltered page without a separate quota query', async () => {
    renderPage()
    await ready()
    expect(listNotes).toHaveBeenCalledTimes(1)
    expect(listNotes).toHaveBeenCalledWith(
      { q: undefined, tag: undefined, page: 0, size: 20 },
      expect.any(AbortSignal),
    )
    expect(listTrash).not.toHaveBeenCalled()
    expect(screen.getByText('Ghi chú tự soạn')).toBeVisible()
    expect(screen.getByText('Cập nhật:')).toHaveTextContent('10:00')
  })

  it('AT26_renders script and HTML as text while highlighting literal keyword matches', async () => {
    const view = renderPage('/library/notes?q=gia+đình')
    await ready()
    expect(view.container.querySelector('script, b, iframe, img')).toBeNull()
    const snippet = screen.getByText(
      (_, element) => element?.tagName === 'P' && element.textContent === page.items[0]!.snippet,
    )
    expect(snippet).toHaveTextContent('<script>gia đình</script> <b>HTML</b>')
    expect(within(snippet).getByText('gia đình', { selector: 'mark' })).toBeVisible()
  })

  it.each([1, 2, 100, 101])(
    'checks %i code points before searching and retains input',
    async (count) => {
      renderPage()
      await ready()
      const q = '𠀀'.repeat(count)
      fireEvent.change(screen.getByLabelText('Từ khoá tìm kiếm'), { target: { value: q } })
      await userEvent.click(screen.getByRole('button', { name: 'Tìm ghi chú' }))
      expect(screen.getByLabelText('Từ khoá tìm kiếm')).toHaveValue(q)
      if (count === 1 || count === 101) {
        expect(screen.getByRole('alert')).toHaveTextContent('2 đến 100')
        expect(listNotes).toHaveBeenCalledTimes(1)
      } else
        await waitFor(() =>
          expect(listNotes).toHaveBeenLastCalledWith(
            { q, tag: undefined, page: 0, size: 20 },
            expect.any(AbortSignal),
          ),
        )
    },
  )

  it('normalizes NFC, sends the exact tag and resets page when filters change', async () => {
    const { router } = renderPage('/library/notes?page=2')
    await ready()
    fireEvent.change(screen.getByLabelText('Từ khoá tìm kiếm'), {
      target: { value: '  a\u0301b  ' },
    })
    fireEvent.change(screen.getByLabelText('Lọc theo thẻ'), { target: { value: 'Thẻ A & B' } })
    await userEvent.click(screen.getByRole('button', { name: 'Tìm ghi chú' }))
    await waitFor(() =>
      expect(listNotes).toHaveBeenLastCalledWith(
        { q: 'áb', tag: 'Thẻ A & B', page: 0, size: 20 },
        expect.any(AbortSignal),
      ),
    )
    expect(new URLSearchParams(router.state.location.search).get('page')).toBe('0')
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Xoá bộ lọc' }))
    await waitFor(() =>
      expect(listNotes).toHaveBeenLastCalledWith(
        { q: undefined, tag: undefined, page: 0, size: 20 },
        expect.any(AbortSignal),
      ),
    )
  })

  it('keeps search and tag while moving between zero-based pages', async () => {
    vi.mocked(listNotes).mockResolvedValue({ ...page, total: 45 })
    renderPage('/library/notes?q=gia+đình&tag=hsk1&page=1')
    expect(await screen.findByText('Trang 2 / 3')).toBeVisible()
    await userEvent.click(screen.getByRole('button', { name: 'Sau' }))
    await waitFor(() =>
      expect(listNotes).toHaveBeenLastCalledWith(
        { q: 'gia đình', tag: 'hsk1', page: 2, size: 20 },
        expect.any(AbortSignal),
      ),
    )
  })

  it('shows loading, read error and retry without inventing an empty result', async () => {
    let reject!: (reason: unknown) => void
    vi.mocked(listNotes).mockImplementationOnce(
      () =>
        new Promise((_, fail) => {
          reject = fail
        }),
    )
    renderPage()
    expect(screen.getByLabelText('Đang tải ghi chú')).toBeVisible()
    await act(async () => reject(error(503, 'UNAVAILABLE')))
    expect(await screen.findByRole('heading', { name: 'Chưa tải được ghi chú' })).toBeVisible()
    expect(screen.queryByRole('heading', { name: 'Chưa có ghi chú' })).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
    await ready()
  })

  it('shows an empty search and lets the learner clear filters', async () => {
    vi.mocked(listNotes).mockResolvedValueOnce({ items: [], total: 0 })
    renderPage('/library/notes?q=không+có')
    expect(await screen.findByRole('heading', { name: 'Không tìm thấy ghi chú' })).toBeVisible()
    await userEvent.click(screen.getAllByRole('button', { name: 'Xoá bộ lọc' })[0]!)
    await ready()
  })

  it('creates a note with optional title and pending tag text, preserving typed content', async () => {
    renderPage()
    await ready()
    await newDraft('  Nội dung giữ khoảng trắng  ')
    fireEvent.change(screen.getByLabelText('Thẻ mới'), { target: { value: '  hsk1  ' } })
    save()
    expect(await screen.findByText('Đã lưu ghi chú.')).toBeVisible()
    expect(createNote).toHaveBeenCalledWith({
      title: null,
      content: '  Nội dung giữ khoảng trắng  ',
      tags: ['hsk1'],
      lexemeId: null,
      sourceType: 'MANUAL',
    })
  })

  it('edits content without changing lexemeId, sourceType or a tag containing commas', async () => {
    renderPage()
    await ready()
    await userEvent.click(screen.getByRole('button', { name: 'Mở ghi chú' }))
    expect(await screen.findByLabelText(/^Nội dung/)).toHaveValue(note.content)
    fireEvent.change(screen.getByLabelText(/^Nội dung/), { target: { value: 'Nội dung đã sửa' } })
    save()
    await screen.findByText('Đã lưu ghi chú.')
    expect(updateNote).toHaveBeenCalledWith(1, {
      title: note.title,
      content: 'Nội dung đã sửa',
      tags: ['hsk1', 'a,b'],
      lexemeId: 101,
      sourceType: 'DICTIONARY',
    })
  })

  it.each([
    { content: ' ', title: '', tag: '', message: 'Nội dung cần' },
    { content: 'a'.repeat(10001), title: '', tag: '', message: 'Nội dung cần' },
    { content: 'a', title: 'a'.repeat(201), tag: '', message: 'Tiêu đề tối đa' },
    { content: 'a', title: '', tag: 'a'.repeat(51), message: 'Tối đa 10 thẻ' },
  ])('keeps invalid editor input on $message', async ({ content, title, tag, message }) => {
    renderPage()
    await ready()
    await newDraft(content)
    fireEvent.change(screen.getByLabelText('Tiêu đề'), { target: { value: title } })
    fireEvent.change(screen.getByLabelText('Thẻ mới'), { target: { value: tag } })
    save()
    expect(screen.getByRole('alert')).toHaveTextContent(message)
    expect(screen.getByLabelText(/^Nội dung/)).toHaveValue(content)
    expect(screen.getByLabelText('Tiêu đề')).toHaveValue(title)
    expect(createNote).not.toHaveBeenCalled()
  })

  it('adds and removes explicit tags and rejects an eleventh pending tag', async () => {
    renderPage()
    await ready()
    await newDraft()
    for (let index = 0; index < 10; index++) {
      fireEvent.change(screen.getByLabelText('Thẻ mới'), { target: { value: `thẻ${index}` } })
      await userEvent.click(screen.getByRole('button', { name: 'Thêm thẻ' }))
    }
    fireEvent.change(screen.getByLabelText('Thẻ mới'), { target: { value: 'thẻ11' } })
    save()
    expect(screen.getByRole('alert')).toHaveTextContent('Tối đa 10 thẻ')
    expect(createNote).not.toHaveBeenCalled()
    await userEvent.click(screen.getByRole('button', { name: 'Bỏ thẻ thẻ0' }))
    save()
    await screen.findByText('Đã lưu ghi chú.')
    expect(vi.mocked(createNote).mock.calls[0]![0].tags).toHaveLength(10)
  })

  it.each([
    {
      status: 422,
      code: 'LIBRARY_LIMIT_REACHED',
      details: { kind: 'NOTES', limit: 123 },
      message: '123 ghi chú',
    },
    { status: 404, code: 'LEXEME_NOT_FOUND', details: undefined, message: 'Mục từ gắn' },
    {
      status: 403,
      code: 'COLLECTION_NOT_OWNED',
      details: undefined,
      message: 'Bạn không có quyền',
    },
    { status: 422, code: 'VALIDATION_FAILED', details: undefined, message: 'Thông báo kiểm thử' },
    { status: 503, code: 'UNAVAILABLE', details: undefined, message: 'Thông báo kiểm thử' },
  ])('retains editor and permits retry on $code', async ({ status, code, details, message }) => {
    vi.mocked(createNote).mockRejectedValueOnce(error(status, code, details))
    renderPage()
    await ready()
    await newDraft()
    save()
    expect(await screen.findByRole('alert')).toHaveTextContent(message)
    expect(screen.getByLabelText(/^Nội dung/)).toHaveValue('Bản nháp cần giữ')
    expect(screen.queryByText('Đã lưu ghi chú.')).not.toBeInTheDocument()
    expect(listNotes).toHaveBeenCalledTimes(1)
    save()
    await screen.findByText('Đã lưu ghi chú.')
  })

  it('does not auto-open a duplicate; failed explicit opening preserves draft and retries', async () => {
    vi.mocked(updateNote).mockRejectedValueOnce(error(409, 'NOTE_DUPLICATE', { noteId: 42 }))
    vi.mocked(getNote)
      .mockResolvedValueOnce(note)
      .mockRejectedValueOnce(error(503, 'UNAVAILABLE'))
      .mockResolvedValueOnce({ ...note, id: 42, content: 'Ghi chú đã có' })
    renderPage()
    await ready()
    await userEvent.click(screen.getByRole('button', { name: 'Mở ghi chú' }))
    await screen.findByLabelText(/^Nội dung/)
    fireEvent.change(screen.getByLabelText(/^Nội dung/), {
      target: { value: 'Bản nháp giữ nguyên' },
    })
    save()
    expect(await screen.findByText('Từ này đã có trong sổ tay')).toBeVisible()
    expect(getNote).toHaveBeenCalledTimes(1)
    expect(screen.getByLabelText(/^Nội dung/)).toHaveValue('Bản nháp giữ nguyên')
    await userEvent.click(screen.getByRole('button', { name: 'Mở ghi chú đã có' }))
    await screen.findByRole('heading', { name: 'Chưa mở được ghi chú' })
    expect(screen.getByLabelText(/^Nội dung/)).toHaveValue('Bản nháp giữ nguyên')
    const region = screen.getByRole('region', { name: 'Chỉnh sửa ghi chú' })
    await userEvent.click(within(region).getByRole('button', { name: 'Thử lại' }))
    await waitFor(() => expect(screen.getByLabelText(/^Nội dung/)).toHaveValue('Ghi chú đã có'))
    expect(getNote).toHaveBeenLastCalledWith(42, expect.any(AbortSignal))
  })

  it('deduplicates repeated save submits', async () => {
    let resolve!: (note: Note) => void
    vi.mocked(createNote).mockImplementationOnce(
      () =>
        new Promise((done) => {
          resolve = done
        }),
    )
    renderPage()
    await ready()
    await newDraft()
    save()
    save()
    expect(createNote).toHaveBeenCalledTimes(1)
    expect(screen.getByRole('button', { name: 'Lưu ghi chú' })).toBeDisabled()
    await act(async () => resolve(note))
    expect(await screen.findByText('Đã lưu ghi chú.')).toBeVisible()
  })

  it('AT26_confirms deletion in the page, cancels with focus restored, and waits for success', async () => {
    let resolve!: () => void
    vi.mocked(deleteNote).mockImplementationOnce(
      () =>
        new Promise((done) => {
          resolve = done
        }),
    )
    renderPage()
    await ready()
    const trigger = screen.getByRole('button', { name: 'Xoá ghi chú' })
    await userEvent.click(trigger)
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Huỷ' }))
    expect(trigger).toHaveFocus()
    expect(deleteNote).not.toHaveBeenCalled()
    await userEvent.click(trigger)
    await userEvent.click(screen.getByRole('button', { name: 'Xác nhận xoá' }))
    expect(deleteNote).toHaveBeenCalledWith(1)
    expect(screen.getByText(note.title!)).toBeVisible()
    expect(listNotes).toHaveBeenCalledTimes(1)
    vi.mocked(listNotes).mockResolvedValue({ items: [], total: 0 })
    await act(async () => resolve())
    expect(await screen.findByText('Đã chuyển ghi chú vào thùng rác.')).toBeVisible()
    expect(await screen.findByRole('heading', { name: 'Chưa có ghi chú' })).toBeVisible()
  })

  it('keeps the list after a deletion failure and requires confirmation again on retry', async () => {
    vi.mocked(deleteNote).mockRejectedValueOnce(error(403, 'COLLECTION_NOT_OWNED'))
    renderPage()
    await ready()
    await userEvent.click(screen.getByRole('button', { name: 'Xoá ghi chú' }))
    await userEvent.click(screen.getByRole('button', { name: 'Xác nhận xoá' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Bạn không có quyền')
    expect(screen.getByText(note.title!)).toBeVisible()
    expect(listNotes).toHaveBeenCalledTimes(1)
    await userEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
    expect(screen.getByRole('dialog')).toBeVisible()
    expect(deleteNote).toHaveBeenCalledTimes(1)
  })

  it('does not reopen a deleted note from a late detail response', async () => {
    let resolve!: (note: Note) => void
    vi.mocked(getNote).mockImplementationOnce(
      () =>
        new Promise((done) => {
          resolve = done
        }),
    )
    renderPage()
    await ready()
    await userEvent.click(screen.getByRole('button', { name: 'Mở ghi chú' }))
    const signal = vi.mocked(getNote).mock.calls[0]![1]!
    await userEvent.click(screen.getByRole('button', { name: 'Xoá ghi chú' }))
    vi.mocked(listNotes).mockResolvedValue({ items: [], total: 0 })
    await userEvent.click(screen.getByRole('button', { name: 'Xác nhận xoá' }))
    await screen.findByText('Đã chuyển ghi chú vào thùng rác.')
    expect(signal.aborted).toBe(true)
    await act(async () => resolve(note))
    expect(screen.queryByLabelText(/^Nội dung/)).not.toBeInTheDocument()
  })

  it('does not roll back new filters when a deletion on the previous last page finishes', async () => {
    let resolve!: () => void
    vi.mocked(deleteNote).mockImplementationOnce(
      () =>
        new Promise((done) => {
          resolve = done
        }),
    )
    vi.mocked(listNotes)
      .mockResolvedValueOnce({ ...page, total: 41 })
      .mockResolvedValue({ items: [], total: 0 })
    const { router } = renderPage('/library/notes?page=2')
    await ready()
    await userEvent.click(screen.getByRole('button', { name: 'Xoá ghi chú' }))
    await userEvent.click(screen.getByRole('button', { name: 'Xác nhận xoá' }))
    await act(async () => {
      await router.navigate('/library/notes?q=mới&page=0')
    })
    await screen.findByRole('heading', { name: 'Không tìm thấy ghi chú' })
    await act(async () => resolve())
    await screen.findByText('Đã chuyển ghi chú vào thùng rác.')
    expect(new URLSearchParams(router.state.location.search).get('q')).toBe('mới')
    expect(new URLSearchParams(router.state.location.search).get('page')).toBe('0')
  })

  it('AT26_loads trash only when selected and displays deletion and restoration times', async () => {
    renderPage()
    await ready()
    expect(listTrash).not.toHaveBeenCalled()
    await userEvent.click(screen.getByRole('tab', { name: 'Thùng rác' }))
    expect(await screen.findByRole('button', { name: 'Khôi phục' })).toBeVisible()
    expect(screen.getByText('Đã xoá:')).toHaveTextContent('11:00')
    expect(screen.getByText('Khôi phục đến:').querySelector('time')).toHaveAttribute(
      'dateTime',
      trashNote.restorableUntil,
    )
    expect(listTrash).toHaveBeenCalledTimes(1)
    expect(listNotes).toHaveBeenCalledTimes(1)
  })

  it.each([
    {
      status: 409,
      code: 'NOTE_NOT_RESTORABLE',
      details: undefined,
      message: 'không thể khôi phục',
    },
    {
      status: 409,
      code: 'NOTE_DUPLICATE',
      details: { noteId: 42 },
      message: 'Từ này đã có trong sổ tay',
    },
    {
      status: 422,
      code: 'LIBRARY_LIMIT_REACHED',
      details: { kind: 'NOTES', limit: 1000 },
      message: '1.000 ghi chú',
    },
  ])(
    'handles restore $code without losing the editor or trash row',
    async ({ status, code, details, message }) => {
      vi.mocked(restoreNote).mockRejectedValueOnce(error(status, code, details))
      vi.mocked(listTrash).mockResolvedValueOnce([trashNote]).mockResolvedValue([])
      renderPage('/library/notes?tab=trash')
      await screen.findByRole('button', { name: 'Khôi phục' })
      await newDraft()
      await userEvent.click(screen.getByRole('button', { name: 'Khôi phục' }))
      expect(await screen.findByRole('alert')).toHaveTextContent(message)
      expect(screen.getByLabelText(/^Nội dung/)).toHaveValue('Bản nháp cần giữ')
      expect(screen.getByText(trashNote.title!)).toBeVisible()
      expect(getNote).not.toHaveBeenCalled()
      expect(listNotes).not.toHaveBeenCalled()
      if (code === 'NOTE_DUPLICATE')
        expect(screen.getByRole('button', { name: 'Mở ghi chú đã có' })).toBeVisible()
      await userEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
      expect(await screen.findByText('Đã khôi phục ghi chú.')).toBeVisible()
      expect(await screen.findByRole('heading', { name: 'Thùng rác trống' })).toBeVisible()
    },
  )

  it('keeps trash failures distinct from empty trash and retries', async () => {
    vi.mocked(listTrash).mockRejectedValueOnce(error(503, 'UNAVAILABLE'))
    renderPage('/library/notes?tab=trash')
    await screen.findByRole('heading', { name: 'Chưa tải được thùng rác' })
    expect(screen.queryByRole('heading', { name: 'Thùng rác trống' })).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
    await screen.findByRole('button', { name: 'Khôi phục' })
  })

  it('waits for restore success before refreshing trash and deduplicates restore clicks', async () => {
    let resolve!: (note: Note) => void
    vi.mocked(restoreNote).mockImplementationOnce(
      () =>
        new Promise((done) => {
          resolve = done
        }),
    )
    renderPage('/library/notes?tab=trash')
    const restore = await screen.findByRole('button', { name: 'Khôi phục' })
    fireEvent.click(restore)
    fireEvent.click(restore)
    expect(restoreNote).toHaveBeenCalledTimes(1)
    expect(listTrash).toHaveBeenCalledTimes(1)
    expect(screen.getByText(trashNote.title!)).toBeVisible()
    vi.mocked(listTrash).mockResolvedValue([])
    await act(async () => resolve(note))
    await screen.findByRole('heading', { name: 'Thùng rác trống' })
  })

  it('aborts stale searches and ignores late responses', async () => {
    let resolve!: (page: NotePage) => void
    vi.mocked(listNotes)
      .mockImplementationOnce(
        () =>
          new Promise((done) => {
            resolve = done
          }),
      )
      .mockResolvedValueOnce({ items: [], total: 0 })
    renderPage()
    await waitFor(() => expect(listNotes).toHaveBeenCalledTimes(1))
    const signal = vi.mocked(listNotes).mock.calls[0]![1]!
    fireEvent.change(screen.getByLabelText('Từ khoá tìm kiếm'), {
      target: { value: 'ghi chú mới' },
    })
    await userEvent.click(screen.getByRole('button', { name: 'Tìm ghi chú' }))
    await screen.findByRole('heading', { name: 'Không tìm thấy ghi chú' })
    expect(signal.aborted).toBe(true)
    await act(async () => resolve(page))
    expect(screen.queryByText(note.title!)).not.toBeInTheDocument()
  })

  it('cancels a detail request when creating a new draft and ignores its late result', async () => {
    let resolve!: (note: Note) => void
    vi.mocked(getNote).mockImplementationOnce(
      () =>
        new Promise((done) => {
          resolve = done
        }),
    )
    renderPage()
    await ready()
    await userEvent.click(screen.getByRole('button', { name: 'Mở ghi chú' }))
    const signal = vi.mocked(getNote).mock.calls[0]![1]!
    await newDraft('Bản nháp mới')
    expect(signal.aborted).toBe(true)
    await act(async () => resolve(note))
    expect(screen.getByLabelText(/^Nội dung/)).toHaveValue('Bản nháp mới')
  })

  it('clears private editor and ignores an old save after account switch', async () => {
    let resolve!: (note: Note) => void
    vi.mocked(createNote).mockImplementationOnce(
      () =>
        new Promise((done) => {
          resolve = done
        }),
    )
    const view = renderPage()
    await ready()
    await newDraft('Bí mật tài khoản cũ')
    save()
    vi.mocked(listNotes).mockResolvedValue({ items: [], total: 0 })
    view.changeAuth(authValue(8))
    await screen.findByRole('heading', { name: 'Chưa có ghi chú' })
    expect(screen.queryByLabelText(/^Nội dung/)).not.toBeInTheDocument()
    expect(screen.queryByText(note.title!)).not.toBeInTheDocument()
    await act(async () => resolve(note))
    expect(screen.queryByText('Đã lưu ghi chú.')).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/^Nội dung/)).not.toBeInTheDocument()
  })

  it('aborts private reads on logout', async () => {
    vi.mocked(getNote).mockImplementationOnce(() => new Promise(() => undefined))
    const view = renderPage()
    await ready()
    const listSignal = vi.mocked(listNotes).mock.calls[0]![1]!
    await userEvent.click(screen.getByRole('button', { name: 'Mở ghi chú' }))
    const detailSignal = vi.mocked(getNote).mock.calls[0]![1]!
    view.changeAuth(authValue(null))
    expect(listSignal.aborted).toBe(true)
    expect(detailSignal.aborted).toBe(true)
    expect(screen.queryByText(note.title!)).not.toBeInTheDocument()
  })

  it.each([null, ['ADMIN'] as RoleCode[]])(
    'does not request private notes without LEARNER: %j',
    async (roles) => {
      renderPage('/library/notes', roles === null ? authValue(null) : authValue(7, roles))
      expect(
        await screen.findByText('Cần đăng nhập với quyền học viên để xem sổ tay.'),
      ).toBeVisible()
      expect(listNotes).not.toHaveBeenCalled()
      expect(listTrash).not.toHaveBeenCalled()
    },
  )
})
