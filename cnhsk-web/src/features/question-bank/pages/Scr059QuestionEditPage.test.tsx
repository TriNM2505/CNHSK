import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { RoleCode } from '@/features/auth/types'
import { ApiError } from '@/shared/api'
import { AuthContext, type AuthContextValue } from '@/shared/auth'

import { getKnowledgeTree } from '../api/knowledge'
import { archiveQuestion, getQuestion, updateQuestion } from '../api/questions'
import type { KnowledgeTree, QuestionDetail, QuestionType } from '../types'
import Scr059QuestionEditPage from './Scr059QuestionEditPage'

vi.mock('../api/knowledge', () => ({ getKnowledgeTree: vi.fn() }))
vi.mock('../api/questions', () => ({
  getQuestion: vi.fn(),
  updateQuestion: vi.fn(),
  archiveQuestion: vi.fn(),
}))

// Original fixtures; page tests never import preview data or contact a real service.
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
    {
      kid: 'CH1-901',
      domain: 'CH',
      hskLevel: 1,
      nameVi: 'Chữ tự soạn',
      nameZh: '示例',
      prereqKids: [],
      supportKids: [],
    },
  ],
}

function question(qtype: QuestionType = 'MULTIPLE_CHOICE', used = false): QuestionDetail {
  return {
    id: 901,
    code: 'TEST_901',
    qtype,
    used,
    hskLevel: 1,
    difficulty: 2,
    stem:
      qtype === 'FILL_BLANK' ? 'Điền từ nước: 我喝___。' : 'Chọn nghĩa của 水 trong câu 我喝水。',
    options:
      qtype === 'FILL_BLANK' || qtype === 'ESSAY'
        ? []
        : qtype === 'SENTENCE_ORDER'
          ? [
              { key: 'A', text: '我', isCorrect: false },
              { key: 'B', text: '喝', isCorrect: false },
              { key: 'C', text: '水', isCorrect: false },
            ]
          : [
              { key: 'A', text: qtype === 'TRUE_FALSE' ? 'Đúng' : 'nước', isCorrect: true },
              { key: 'B', text: qtype === 'TRUE_FALSE' ? 'Sai' : 'sách', isCorrect: false },
            ],
    correctKey: qtype === 'FILL_BLANK' ? '水' : qtype === 'SENTENCE_ORDER' ? 'ABC' : null,
    explanation: '水 nghĩa là nước.',
    source: 'IMPORT',
    status: 'APPROVED',
    imageUrl: qtype === 'IMAGE_MATCH' ? 'https://assets.example.test/image.svg' : null,
    audioUrl: 'https://assets.example.test/audio.mp3',
    labels: [{ kid: 'NP1-901', nameVi: 'Câu giới thiệu tự soạn', role: 'PRIMARY' }],
  }
}

const getMock = vi.mocked(getQuestion)
const treeMock = vi.mocked(getKnowledgeTree)
const updateMock = vi.mocked(updateQuestion)
const archiveMock = vi.mocked(archiveQuestion)
const fetchMock = vi.fn<typeof fetch>()

function renderPage({
  roles = ['TEACHER'],
  entry = '/content/questions/901',
}: {
  roles?: RoleCode[]
  entry?: string
} = {}) {
  const auth: AuthContextValue = {
    user: {
      id: 7,
      displayName: 'Người dùng kiểm thử',
      email: 'question@example.test',
      emailVerified: true,
      roles,
    },
    status: 'authenticated',
    error: null,
    setAuthenticatedUser: vi.fn(),
    reloadCurrentUser: vi.fn(),
    signOut: vi.fn(),
  }
  const router = createMemoryRouter(
    [
      { path: '/content/questions/:id', element: <Scr059QuestionEditPage /> },
      { path: '/content/questions', element: <h1>Kho câu hỏi</h1> },
    ],
    { initialEntries: [entry] },
  )
  render(
    <AuthContext.Provider value={auth}>
      <RouterProvider router={router} />
    </AuthContext.Provider>,
  )
  return router
}

async function form() {
  return screen.findByRole('form', { name: 'Sửa câu hỏi' })
}
function field(label: string) {
  return screen.getByLabelText(new RegExp(`^${label}(?:\\s*\\*)?$`))
}
async function requestSave() {
  await form()
  fireEvent.click(screen.getByRole('button', { name: 'Lưu thay đổi' }))
  return screen.findByRole('region', { name: 'Xác nhận lưu thay đổi' })
}
function apiError(
  status: number,
  errorCode: string,
  message = 'Thông báo an toàn.',
  details?: unknown,
) {
  return new ApiError({ status, errorCode, message, requestId: 'req-question', details })
}
function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((done) => {
    resolve = done
  })
  return { promise, resolve }
}

beforeEach(() => {
  getMock.mockReset().mockResolvedValue(question())
  treeMock.mockReset().mockResolvedValue(tree)
  updateMock.mockReset().mockImplementation(async (_id, body) => ({
    ...question(),
    ...body,
    labels: [
      { kid: body.primaryKid, nameVi: 'Nút chính tự soạn', role: 'PRIMARY' },
      ...body.secondaryKids.map((kid) => ({
        kid,
        nameVi: 'Nút phụ tự soạn',
        role: 'SECONDARY' as const,
      })),
    ],
  }))
  archiveMock.mockReset().mockResolvedValue({ ...question(), status: 'ARCHIVED' })
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
  vi.spyOn(window, 'confirm').mockReturnValue(false)
})
afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('SCR-059 question edit (FR-019…FR-023, FR-027 · AT-27)', () => {
  it.each([
    'MULTIPLE_CHOICE',
    'TRUE_FALSE',
    'IMAGE_MATCH',
    'SENTENCE_MATCH',
    'FILL_BLANK',
    'SENTENCE_ORDER',
  ] as const)('FR019_AT27_displays the complete %s detail for CONTENT_EDITOR', async (qtype) => {
    const data = question(qtype)
    data.reviewReason = 'Lý do kiểm duyệt tự soạn.'
    if (data.options[1] && qtype === 'MULTIPLE_CHOICE')
      Object.assign(data.options[1], { errorKid: 'TV2-901', errorNote: 'Nhầm từ tự soạn.' })
    getMock.mockResolvedValue(data)
    renderPage({ roles: ['CONTENT_EDITOR'] })
    expect(await screen.findByText(data.stem)).toBeVisible()
    expect(screen.getByText(data.explanation!)).toBeVisible()
    expect(screen.getByText('Chính: NP1-901 · Câu giới thiệu tự soạn')).toBeVisible()
    expect(screen.getByText('Lý do kiểm duyệt: Lý do kiểm duyệt tự soạn.')).toBeVisible()
    expect(field('Âm thanh câu hỏi')).toHaveAttribute('src', data.audioUrl)
    if (data.imageUrl)
      expect(screen.getByRole('link', { name: 'Xem ảnh câu hỏi' })).toHaveAttribute(
        'href',
        data.imageUrl,
      )
    if (qtype === 'MULTIPLE_CHOICE') {
      expect(screen.getByText('Đáp án đúng', { exact: false })).toBeVisible()
      expect(screen.getByText('Nút lỗi: TV2-901')).toBeVisible()
    }
    if (data.correctKey)
      expect(
        screen.getByText(
          (_, element) =>
            element?.tagName === 'P' &&
            element.textContent ===
              `${qtype === 'SENTENCE_ORDER' ? 'Thứ tự đúng' : 'Đáp án đúng'}: ${data.correctKey}`,
        ),
      ).toBeVisible()
    expect(screen.queryByRole('form')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Lưu thay đổi' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Duyệt|Từ chối/ })).not.toBeInTheDocument()
  })

  it.each([
    'MULTIPLE_CHOICE',
    'TRUE_FALSE',
    'IMAGE_MATCH',
    'SENTENCE_MATCH',
    'FILL_BLANK',
    'SENTENCE_ORDER',
  ] as const)('FR027_AT27_TEACHER edits %s with the existing API wrappers', async (qtype) => {
    getMock.mockResolvedValue(question(qtype))
    renderPage()
    await form()
    expect(field('Nội dung câu hỏi')).toHaveValue(question(qtype).stem)
    expect(field('Lời giải')).toBeEnabled()
    expect(field('Nút năng lực chính')).toHaveValue('NP1-901')
    expect(getMock).toHaveBeenCalledWith(901, expect.any(AbortSignal))
    expect(treeMock).toHaveBeenCalledTimes(1)
    expect(treeMock).toHaveBeenCalledWith({}, expect.any(AbortSignal))
    expect(screen.queryByRole('button', { name: 'Lưu trữ câu hỏi' })).not.toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it.each(['MULTIPLE_CHOICE', 'IMAGE_MATCH', 'SENTENCE_MATCH'] as const)(
    'FR022_AT27_used %s locks answer selection and option count while keeping content editable',
    async (qtype) => {
      getMock.mockResolvedValue(question(qtype, true))
      renderPage()
      await form()
      expect(
        screen.getByText('Câu đã có người làm — không đổi được đáp án; tạo câu mới thay thế'),
      ).toBeVisible()
      expect(field('Đáp án đúng A')).toBeDisabled()
      expect(field('Đáp án đúng B')).toBeDisabled()
      expect(screen.getByRole('button', { name: 'Thêm phương án' })).toBeDisabled()
      expect(screen.getByRole('button', { name: 'Xoá phương án A' })).toBeDisabled()
      expect(field('Phương án A')).toBeEnabled()
      expect(field('Nội dung câu hỏi')).toBeEnabled()
      expect(field('Lời giải')).toBeEnabled()
      expect(field('Nút năng lực chính')).toBeEnabled()
    },
  )

  it.each(['FILL_BLANK', 'SENTENCE_ORDER'] as const)(
    'FR022_AT27_used %s locks correctKey',
    async (qtype) => {
      getMock.mockResolvedValue(question(qtype, true))
      renderPage()
      await form()
      expect(field(qtype === 'FILL_BLANK' ? 'Đáp án đúng' : 'Thứ tự đúng')).toBeDisabled()
      expect(field('Lời giải')).toBeEnabled()
    },
  )

  it('FR022_AT27_used TRUE_FALSE keeps its fixed labels and locks the correct answer', async () => {
    getMock.mockResolvedValue(question('TRUE_FALSE', true))
    renderPage()
    await form()
    expect(field('Phương án A')).toBeDisabled()
    expect(field('Đáp án đúng A')).toBeDisabled()
    expect(screen.queryByRole('button', { name: 'Thêm phương án' })).not.toBeInTheDocument()
  })

  it('FR020_AT27_confirms a used-question explanation and label update with exactly the writable payload', async () => {
    const data = {
      ...question('MULTIPLE_CHOICE', true),
      source: 'AI' as const,
      status: 'PENDING_REVIEW' as const,
    }
    getMock.mockResolvedValue(data)
    updateMock.mockImplementation(async (_id, body) => ({ ...data, ...body }))
    renderPage()
    await form()
    fireEvent.change(field('Lời giải'), { target: { value: '  Lời giải mới.  ' } })
    fireEvent.change(field('Nút năng lực chính'), { target: { value: 'TV2-901' } })
    fireEvent.change(field('Nút năng lực phụ 1'), { target: { value: 'NP1-901' } })
    await requestSave()
    expect(updateMock).not.toHaveBeenCalled()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận lưu thay đổi' }))
    expect(await screen.findByText('Đã lưu thay đổi.')).toBeVisible()
    expect(updateMock).toHaveBeenCalledTimes(1)
    const body = updateMock.mock.calls[0]![1]
    expect(body).toMatchObject({
      explanation: 'Lời giải mới.',
      primaryKid: 'TV2-901',
      secondaryKids: ['NP1-901'],
      correctKey: null,
    })
    expect(body.options.map(({ key, isCorrect }) => ({ key, isCorrect }))).toEqual(
      data.options.map(({ key, isCorrect }) => ({ key, isCorrect })),
    )
    for (const field of ['qtype', 'code', 'status', 'source', 'used', 'userId'])
      expect(body).not.toHaveProperty(field)
    expect(screen.getByText('Chờ duyệt')).toBeVisible()
    expect(field('Đáp án đúng A')).toBeDisabled()
    expect(treeMock).toHaveBeenCalledTimes(1)
    expect(window.confirm).not.toHaveBeenCalled()
  })

  it('FR020_AT27_cancel keeps the unsaved form without writing', async () => {
    renderPage()
    await form()
    fireEvent.change(field('Lời giải'), { target: { value: 'Bản nháp lời giải.' } })
    const confirmation = await requestSave()
    expect(within(confirmation).getByRole('button', { name: 'Huỷ' })).toHaveFocus()
    fireEvent.click(within(confirmation).getByRole('button', { name: 'Huỷ' }))
    expect(screen.queryByRole('region', { name: 'Xác nhận lưu thay đổi' })).not.toBeInTheDocument()
    expect(field('Lời giải')).toHaveValue('Bản nháp lời giải.')
    expect(updateMock).not.toHaveBeenCalled()
  })

  it('FR020_AT27_disables duplicate submissions while save is pending', async () => {
    const response = deferred<QuestionDetail>()
    updateMock.mockReturnValue(response.promise)
    renderPage()
    await requestSave()
    const confirm = screen.getByRole('button', { name: 'Xác nhận lưu thay đổi' })
    fireEvent.click(confirm)
    fireEvent.click(confirm)
    await waitFor(() => expect(updateMock).toHaveBeenCalledTimes(1))
    expect(confirm).toBeDisabled()
    expect(field('Lời giải')).toBeDisabled()
    await act(async () => response.resolve(question()))
    expect(await screen.findByText('Đã lưu thay đổi.')).toBeVisible()
  })

  it('FR021_AT27_validates primary and duplicate secondary KIDs before confirmation', async () => {
    renderPage()
    await form()
    fireEvent.change(field('Nút năng lực chính'), { target: { value: '' } })
    fireEvent.click(screen.getByRole('button', { name: 'Lưu thay đổi' }))
    expect(await screen.findByText('Chọn một nút năng lực chính.')).toBeVisible()
    fireEvent.change(field('Nút năng lực chính'), { target: { value: 'NP1-901' } })
    fireEvent.change(field('Nút năng lực phụ 1'), { target: { value: 'TV2-901' } })
    fireEvent.change(field('Nút năng lực phụ 2'), { target: { value: 'TV2-901' } })
    fireEvent.click(screen.getByRole('button', { name: 'Lưu thay đổi' }))
    expect(
      await screen.findByText('Nút phụ không được trùng nhau hoặc trùng nút chính.'),
    ).toBeVisible()
    expect(screen.getAllByLabelText(/Nút năng lực phụ \d/)).toHaveLength(2)
    expect(updateMock).not.toHaveBeenCalled()
  })

  it('FR021_AT27_allows adding and removing options before use and changing the correct answer', async () => {
    renderPage()
    await form()
    fireEvent.click(screen.getByRole('button', { name: 'Thêm phương án' }))
    fireEvent.change(field('Phương án C'), { target: { value: 'trà' } })
    fireEvent.click(field('Đáp án đúng C'))
    expect(field('Đáp án đúng A')).not.toBeChecked()
    expect(field('Đáp án đúng C')).toBeChecked()
    fireEvent.click(screen.getByRole('button', { name: 'Xoá phương án B' }))
    expect(field('Phương án B')).toHaveValue('trà')
    await requestSave()
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận lưu thay đổi' }))
    await waitFor(() => expect(updateMock).toHaveBeenCalledOnce())
    expect(updateMock.mock.calls[0]![1].options).toMatchObject([
      { key: 'A', text: 'nước', isCorrect: false },
      { key: 'B', text: 'trà', isCorrect: true },
    ])
  })

  it('FR027_AT27_limits the HSK selection to one through three', async () => {
    renderPage()
    await form()
    expect(
      within(field('Cấp HSK'))
        .getAllByRole('option')
        .map((option) => option.getAttribute('value')),
    ).toEqual(['1', '2', '3'])
  })

  it('FR023_AT27_CONTENT_EDITOR archives only after inline confirmation and hides the action on success', async () => {
    renderPage({ roles: ['CONTENT_EDITOR'] })
    fireEvent.click(await screen.findByRole('button', { name: 'Lưu trữ câu hỏi' }))
    expect(archiveMock).not.toHaveBeenCalled()
    expect(screen.getByRole('region', { name: 'Xác nhận lưu trữ' })).toBeVisible()
    expect(
      screen.getByText(
        'Câu hỏi sẽ ngừng được sử dụng cho lượt mới. Lịch sử bài làm được giữ nguyên.',
      ),
    ).toBeVisible()
    fireEvent.click(screen.getByRole('button', { name: 'Huỷ' }))
    expect(archiveMock).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: 'Lưu trữ câu hỏi' }))
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận lưu trữ' }))
    expect(await screen.findByText('Đã lưu trữ câu hỏi.')).toBeVisible()
    expect(archiveMock).toHaveBeenCalledWith(901, expect.any(AbortSignal))
    expect(screen.queryByRole('button', { name: 'Lưu trữ câu hỏi' })).not.toBeInTheDocument()
    expect(screen.getByText('Đã lưu trữ')).toBeVisible()
    expect(window.confirm).not.toHaveBeenCalled()
  })

  it.each([['TEACHER'], ['CONTENT_EDITOR'], ['TEACHER', 'CONTENT_EDITOR']] as RoleCode[][])(
    'FR027_AT27_archived questions stay read-only for %j',
    async (...roles) => {
      getMock.mockResolvedValue({ ...question(), status: 'ARCHIVED' })
      renderPage({ roles })
      expect(await screen.findByText('Câu đã lưu trữ. Chỉ có thể xem nội dung.')).toBeVisible()
      expect(screen.queryByRole('form')).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Lưu trữ câu hỏi' })).not.toBeInTheDocument()
    },
  )

  it('FR027_AT27_multi-role users receive each explicitly granted action', async () => {
    renderPage({ roles: ['TEACHER', 'CONTENT_EDITOR'] })
    await form()
    expect(screen.getByRole('button', { name: 'Lưu thay đổi' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Lưu trữ câu hỏi' })).toBeEnabled()
  })

  it.each([['TEACHER'], ['CONTENT_EDITOR']] as RoleCode[][])(
    'FR027_AT27_ESSAY stays read-only for %s with archive only for CONTENT_EDITOR',
    async (role) => {
      getMock.mockResolvedValue(question('ESSAY'))
      renderPage({ roles: [role] })
      expect(
        await screen.findByText(
          'Chưa hỗ trợ sửa câu tự luận. Bạn có thể xem nội dung và đáp án hiện có.',
        ),
      ).toBeVisible()
      expect(screen.queryByRole('form')).not.toBeInTheDocument()
      expect(Boolean(screen.queryByRole('button', { name: 'Lưu trữ câu hỏi' }))).toBe(
        role === 'CONTENT_EDITOR',
      )
      expect(updateMock).not.toHaveBeenCalled()
    },
  )

  it.each([
    [409, 'GRADING_CHANGE_NOT_ALLOWED'],
    [409, 'QUESTION_ARCHIVED'],
    [422, 'MALFORMED_QUESTION'],
    [422, 'KNOWLEDGE_NODE_REQUIRED'],
    [403, 'FORBIDDEN'],
  ])('FR022_AT27_displays %s %s without losing input or claiming success', async (status, code) => {
    updateMock.mockRejectedValue(
      apiError(status as number, code as string, 'Không thể lưu câu hỏi.'),
    )
    renderPage()
    await form()
    fireEvent.change(field('Lời giải'), { target: { value: 'Bản nháp cần giữ.' } })
    await requestSave()
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận lưu thay đổi' }))
    expect(await screen.findByText('Không thể lưu câu hỏi.')).toBeVisible()
    expect(screen.getByText('Mã yêu cầu: req-question')).toBeVisible()
    expect(field('Lời giải')).toHaveValue('Bản nháp cần giữ.')
    expect(screen.queryByText('Đã lưu thay đổi.')).not.toBeInTheDocument()
    expect(updateMock).toHaveBeenCalledTimes(1)
  })

  it('FR021_AT27_associates 422 details with form fields and preserves input', async () => {
    updateMock.mockRejectedValue(
      apiError(422, 'MALFORMED_QUESTION', 'Nhãn không hợp lệ.', [
        { field: 'primaryKid', message: 'Nút chính không còn tồn tại.' },
      ]),
    )
    renderPage()
    await requestSave()
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận lưu thay đổi' }))
    await screen.findByText('Nhãn không hợp lệ.')
    expect(field('Nút năng lực chính')).toHaveAttribute('aria-invalid', 'true')
    fireEvent.click(screen.getByRole('button', { name: 'Huỷ' }))
    expect(field('Nút năng lực chính')).toBeEnabled()
  })

  it('FR022_AT27_reloads authoritative used state after a grading conflict', async () => {
    updateMock.mockRejectedValue(apiError(409, 'GRADING_CHANGE_NOT_ALLOWED'))
    getMock
      .mockResolvedValueOnce(question())
      .mockResolvedValueOnce(question('MULTIPLE_CHOICE', true))
    renderPage()
    await requestSave()
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận lưu thay đổi' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Tải lại câu hỏi' }))
    await waitFor(() => expect(field('Đáp án đúng A')).toBeDisabled())
    expect(getMock).toHaveBeenCalledTimes(2)
    expect(treeMock).toHaveBeenCalledTimes(1)
  })

  it('FR023_AT27_keeps archive errors visible without changing the status', async () => {
    archiveMock.mockRejectedValue(apiError(503, 'INTERNAL_ERROR', 'Lưu trữ thất bại.'))
    renderPage({ roles: ['CONTENT_EDITOR'] })
    fireEvent.click(await screen.findByRole('button', { name: 'Lưu trữ câu hỏi' }))
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận lưu trữ' }))
    expect(await screen.findByText('Lưu trữ thất bại.')).toBeVisible()
    expect(screen.getByText('Đã duyệt')).toBeVisible()
    expect(screen.queryByText('Đã lưu trữ câu hỏi.')).not.toBeInTheDocument()
  })

  it('FR027_AT27_shows skeleton while loading detail', async () => {
    const response = deferred<QuestionDetail>()
    getMock.mockReturnValue(response.promise)
    renderPage()
    expect(screen.getByRole('status', { name: 'Đang tải câu hỏi' })).toBeVisible()
    expect(screen.queryByRole('form')).not.toBeInTheDocument()
    await act(async () => response.resolve(question()))
    await form()
  })

  it('FR027_AT27_keeps detail readable and blocks save while the tree is loading, then loads it once', async () => {
    const response = deferred<KnowledgeTree>()
    treeMock.mockReturnValue(response.promise)
    renderPage()
    await form()
    expect(field('Nội dung câu hỏi')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Lưu thay đổi' })).toBeDisabled()
    await act(async () => response.resolve(tree))
    expect(screen.getByRole('button', { name: 'Lưu thay đổi' })).toBeEnabled()
    expect(treeMock).toHaveBeenCalledTimes(1)
  })

  it('FR027_AT27_tree errors have retry without replacing the loaded question', async () => {
    treeMock
      .mockRejectedValueOnce(apiError(503, 'NETWORK_ERROR', 'Chưa tải được cây.'))
      .mockResolvedValueOnce(tree)
    renderPage()
    const error = await screen.findByText('Không thể tải nút năng lực')
    expect(error).toBeVisible()
    expect(screen.getByRole('button', { name: 'Lưu thay đổi' })).toBeDisabled()
    fireEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
    await waitFor(() => expect(screen.getByRole('button', { name: 'Lưu thay đổi' })).toBeEnabled())
    expect(getMock).toHaveBeenCalledTimes(1)
    expect(treeMock).toHaveBeenCalledTimes(2)
  })

  it('FR027_AT27_empty trees explain why save is disabled', async () => {
    treeMock.mockResolvedValue({ nodes: [] })
    renderPage()
    expect(
      await screen.findByText('Chưa có nút năng lực. Cần nạp cây trước khi lưu câu hỏi.'),
    ).toBeVisible()
    expect(screen.getByRole('button', { name: 'Lưu thay đổi' })).toBeDisabled()
  })

  it.each(['abc', '0', '-1', '1.5', '9007199254740992'])(
    'FR019_AT27_invalid id %s does not request detail',
    async (id) => {
      renderPage({ entry: `/content/questions/${id}` })
      expect(await screen.findByText('Không tìm thấy câu hỏi')).toBeVisible()
      expect(getMock).not.toHaveBeenCalled()
    },
  )

  it('FR019_AT27_404 renders the missing state and preserves the full bank filter URL', async () => {
    getMock.mockRejectedValue(apiError(404, 'QUESTION_NOT_FOUND'))
    const router = renderPage({ entry: '/content/questions/901?hskLevel=2&q=水&page=2&size=10' })
    expect(await screen.findByText('Không tìm thấy câu hỏi')).toBeVisible()
    await userEvent.click(screen.getByRole('link', { name: 'Về kho câu hỏi' }))
    expect(router.state.location.pathname).toBe('/content/questions')
    expect(Object.fromEntries(new URLSearchParams(router.state.location.search))).toEqual({
      hskLevel: '2',
      q: '水',
      page: '2',
      size: '10',
    })
  })

  it('FR027_AT27_load errors support retry without claiming the question is missing', async () => {
    getMock.mockRejectedValueOnce(apiError(503, 'NETWORK_ERROR')).mockResolvedValueOnce(question())
    renderPage()
    expect(await screen.findByText('Không thể tải câu hỏi')).toBeVisible()
    expect(screen.queryByText('Không tìm thấy câu hỏi')).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
    await form()
    expect(getMock).toHaveBeenCalledTimes(2)
  })

  it('FR027_AT27_changing id aborts the old mutation and ignores its late response', async () => {
    const old = deferred<QuestionDetail>()
    updateMock.mockReturnValue(old.promise)
    const router = renderPage()
    await requestSave()
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận lưu thay đổi' }))
    await waitFor(() => expect(updateMock).toHaveBeenCalledOnce())
    const signal = updateMock.mock.calls[0]![2]!
    getMock.mockResolvedValue({
      ...question(),
      id: 902,
      code: 'TEST_902',
      stem: 'Câu mới tự soạn.',
    })
    await act(async () => router.navigate('/content/questions/902'))
    await waitFor(() => expect(field('Nội dung câu hỏi')).toHaveValue('Câu mới tự soạn.'))
    expect(signal.aborted).toBe(true)
    await act(async () => old.resolve(question()))
    expect(field('Nội dung câu hỏi')).toHaveValue('Câu mới tự soạn.')
    expect(screen.queryByText('Đã lưu thay đổi.')).not.toBeInTheDocument()
    expect(treeMock).toHaveBeenCalledTimes(1)
  })
})
