import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { MemoryRouter, useNavigate } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { CurrentUser } from '@/features/auth/types'
import { AuthContext, type AuthContextValue } from '@/shared/auth/AuthContext'
import { LearnerLayout } from '@/shared/layout/LearnerLayout'
import { ApiError } from '@/shared/api'

import { askAssistant } from '../api/ask'
import { assistantErrors, knowledgeAnswer, systemAnswer } from '../test-fixtures'
import type { AskResult } from '../types'

vi.mock('../api/ask', () => ({ askAssistant: vi.fn() }))

const learner: CurrentUser = {
  id: 15,
  displayName: 'Người học mẫu',
  email: 'learner@example.test',
  emailVerified: true,
  roles: ['LEARNER'],
}

function Harness() {
  const [user, setUser] = useState<CurrentUser | null>(learner)
  const navigate = useNavigate()
  const value: AuthContextValue = {
    user,
    status: user ? 'authenticated' : 'guest',
    error: null,
    signOut: async () => {
      setUser(null)
    },
    reloadCurrentUser: async () => undefined,
    setAuthenticatedUser: setUser,
  }
  return (
    <AuthContext.Provider value={value}>
      <button onClick={() => navigate('/account/credits')}>Màn tín dụng</button>
      <button onClick={() => navigate('/learn/exams/sessions/12/?mode=PLACEMENT')}>
        Vào SCR-021
      </button>
      <button onClick={() => navigate('/learn/topics/12/test/?from=study')}>
        Vào kiểm tra chủ đề
      </button>
      <button onClick={() => setUser({ ...learner, id: 16 })}>Đổi tài khoản</button>
      <button onClick={() => setUser(null)}>Kết thúc phiên</button>
      <LearnerLayout>
        <main aria-label="Nội dung học tập">
          <button>Hành động chính</button>
        </main>
      </LearnerLayout>
    </AuthContext.Provider>
  )
}

function mount(path = '/learn/topics') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Harness />
    </MemoryRouter>,
  )
}

async function send(text: string) {
  const user = userEvent.setup()
  await user.type(screen.getByRole('textbox', { name: 'Câu hỏi về cách dùng CNHSK' }), text)
  await user.click(screen.getByRole('button', { name: 'Gửi' }))
  await waitFor(() => expect(screen.queryByText('Trợ lý đang trả lời…')).not.toBeInTheDocument())
}

describe('assistant FR013 / FR014', () => {
  it.each([
    '/community/quizzes/43/play?sessionId=101',
    '/community/quizzes/43/play/?sessionId=102&retry=true',
  ])('hides both launcher and panel on quiz play %s', (path) => {
    mount(path)
    expect(screen.queryByRole('button', { name: 'Mở trợ lý CNHSK' })).not.toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Trợ lý hỗ trợ CNHSK' })).not.toBeInTheDocument()
    expect(screen.getByRole('main', { name: 'Nội dung học tập' })).toBeVisible()
    expect(askAssistant).not.toHaveBeenCalled()
  })
  it.each(['/community/quizzes', '/community/quizzes/attempts/101'])(
    'keeps launcher on %s',
    (path) => {
      mount(path)
      expect(screen.getByRole('button', { name: 'Mở trợ lý CNHSK' })).toBeVisible()
    },
  )
  beforeEach(() => {
    vi.mocked(askAssistant).mockReset().mockResolvedValue(systemAnswer)
  })

  it('AT13_opens and collapses without reducing closed content width or replacing page content', async () => {
    const user = userEvent.setup()
    const view = mount()
    const main = screen.getByRole('main', { name: 'Nội dung học tập' })
    const host = view.container.querySelector('[data-assistant-open]')!
    const closedClasses = host.className
    expect(closedClasses).not.toMatch(/grid|flex|w-80|w-96/)
    expect(within(host as HTMLElement).queryByRole('complementary')).not.toBeInTheDocument()
    expect(main.parentElement).toHaveClass('min-w-0', 'flex-1', 'pb-24')
    expect(screen.getByRole('navigation', { name: 'Học tập' })).toBeVisible()
    expect(
      screen.getByRole('navigation', { name: 'Học tập trên thiết bị di động' }),
    ).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Mở trợ lý CNHSK' }))
    expect(screen.getByRole('region', { name: 'Trợ lý hỗ trợ CNHSK' })).toBeVisible()
    expect(host).toHaveClass('lg:flex')
    await send('Chọn chủ đề thế nào?')
    await user.click(screen.getByRole('button', { name: 'Thu gọn' }))
    expect(host.className).toBe(closedClasses)
    expect(screen.getByRole('main', { name: 'Nội dung học tập' })).toBe(main)
    expect(within(host as HTMLElement).queryByRole('complementary')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Mở trợ lý CNHSK' }))
    expect(screen.getByRole('log')).toHaveTextContent(systemAnswer.answer)
  })

  it('AT13_counts 2000 characters and prevents empty or oversized requests', async () => {
    const user = userEvent.setup()
    mount()
    await user.click(screen.getByRole('button', { name: 'Mở trợ lý CNHSK' }))
    const input = screen.getByRole('textbox')
    expect(screen.getByRole('button', { name: 'Gửi' })).toBeDisabled()
    await user.type(input, '   ')
    expect(screen.getByRole('button', { name: 'Gửi' })).toBeDisabled()
    fireEvent.change(input, { target: { value: 'a'.repeat(2000) } })
    expect(screen.getByText('2000/2.000 ký tự')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Gửi' })).toBeEnabled()
    fireEvent.change(input, { target: { value: 'a'.repeat(2001) } })
    expect(screen.getByRole('button', { name: 'Gửi' })).toBeDisabled()
    expect(askAssistant).not.toHaveBeenCalled()
  })

  it('AT13_sends only ten recent messages and changes the screen code without sending the URL', async () => {
    const user = userEvent.setup()
    mount()
    await user.click(screen.getByRole('button', { name: 'Mở trợ lý CNHSK' }))
    for (let i = 0; i < 7; i++) await send('Hỏi ' + i)
    await user.click(screen.getByRole('button', { name: 'Màn tín dụng' }))
    await send('Câu cuối')
    const body = vi.mocked(askAssistant).mock.calls[7]![0]
    expect(body.question).toBe('Câu cuối')
    expect(body.pageContext).toBe('SCR-030')
    expect(body.history).toHaveLength(10)
    expect(body.history?.[0]).toEqual({ role: 'user', text: 'Hỏi 2' })
    expect(body.history?.[9]).toEqual({ role: 'assistant', text: systemAnswer.answer })
    expect(JSON.stringify(body)).not.toContain('/account/credits')
  })

  it('AT13_preserves full answers and trims outbound history to 4000 code points', async () => {
    const text = '𠀀'.repeat(4001)
    vi.mocked(askAssistant).mockResolvedValueOnce({ answer: text, outOfScope: false })
    const user = userEvent.setup()
    mount()
    await user.click(screen.getByRole('button', { name: 'Mở trợ lý CNHSK' }))
    await send('Giới thiệu hệ thống')
    expect(screen.getByRole('log')).toHaveTextContent(text)
    await send('Tiếp theo')
    const history = vi.mocked(askAssistant).mock.calls[1]![0].history!
    expect(history[1]?.text).toBe('𠀀'.repeat(4000))
    expect(Array.from(history[1]!.text)).toHaveLength(4000)
  })

  it('AT13_discards memory on remount and does not access browser storage', async () => {
    const setItem = vi.spyOn(Storage.prototype, 'setItem')
    const getItem = vi.spyOn(Storage.prototype, 'getItem')
    const user = userEvent.setup()
    const view = mount()
    await user.click(screen.getByRole('button', { name: 'Mở trợ lý CNHSK' }))
    await send('Câu hỏi riêng của trang')
    view.unmount()
    mount()
    await user.click(screen.getByRole('button', { name: 'Mở trợ lý CNHSK' }))
    expect(screen.getByRole('log')).toBeEmptyDOMElement()
    expect(screen.getByRole('textbox')).toHaveValue('')
    expect(setItem).not.toHaveBeenCalled()
    expect(getItem).not.toHaveBeenCalled()
    setItem.mockRestore()
    getItem.mockRestore()
  })

  it.each([
    '/learn/exams/sessions/12',
    '/learn/exams/sessions/12/?mode=MOCK_EXAM',
    '/learn/exams/sessions/12?mode=PLACEMENT',
    '/learn/topics/12/test',
    '/learn/topics/12/test/?from=study',
  ])('AT14_has no assistant on initial route %s', (path) => {
    mount(path)
    expect(screen.queryByRole('button', { name: 'Mở trợ lý CNHSK' })).not.toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Trợ lý hỗ trợ CNHSK' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Hành động chính' })).toBeVisible()
  })

  it.each(['Vào SCR-021', 'Vào kiểm tra chủ đề'])(
    'AT14_clears chat and draft when navigating through %s',
    async (target) => {
      const user = userEvent.setup()
      mount()
      await user.click(screen.getByRole('button', { name: 'Mở trợ lý CNHSK' }))
      await send('Câu hỏi trước bài kiểm tra')
      await user.type(screen.getByRole('textbox'), 'Bản nháp')
      await user.click(screen.getByRole('button', { name: target }))
      expect(screen.queryByRole('button', { name: 'Mở trợ lý CNHSK' })).not.toBeInTheDocument()
      expect(screen.queryByRole('region', { name: 'Trợ lý hỗ trợ CNHSK' })).not.toBeInTheDocument()
      await user.click(screen.getByRole('button', { name: 'Màn tín dụng' }))
      await user.click(screen.getByRole('button', { name: 'Mở trợ lý CNHSK' }))
      expect(screen.getByRole('log')).toBeEmptyDOMElement()
      expect(screen.getByRole('textbox')).toHaveValue('')
    },
  )

  it.each([
    '/learn/topics/12/study',
    '/learn/topics/12/practice/recognition',
    '/learn/topics/12/practice/listening',
  ])('AT14_keeps assistant on non-test topic step %s', (path) => {
    mount(path)
    expect(screen.getByRole('button', { name: 'Mở trợ lý CNHSK' })).toBeVisible()
  })

  it.each(['Cuộc trò chuyện mới', 'Đổi tài khoản', 'Kết thúc phiên', 'Vào SCR-021'])(
    'FR013_cancels an in-flight answer on %s',
    async (target) => {
      let resolve: (result: AskResult) => void = () => undefined
      vi.mocked(askAssistant).mockImplementationOnce(
        () =>
          new Promise((done) => {
            resolve = done
          }),
      )
      const user = userEvent.setup()
      mount()
      await user.click(screen.getByRole('button', { name: 'Mở trợ lý CNHSK' }))
      await user.type(screen.getByRole('textbox'), 'Câu đang gửi')
      await user.click(screen.getByRole('button', { name: 'Gửi' }))
      expect(screen.getByRole('status')).toHaveTextContent('Trợ lý đang trả lời')
      const signal = vi.mocked(askAssistant).mock.calls[0]![1]!
      await user.click(screen.getByRole('button', { name: target }))
      expect(signal.aborted).toBe(true)
      if (target === 'Vào SCR-021')
        await user.click(screen.getByRole('button', { name: 'Màn tín dụng' }))
      const launcher = screen.queryByRole('button', { name: 'Mở trợ lý CNHSK' })
      if (launcher) await user.click(launcher)
      await act(async () => resolve({ answer: 'Phản hồi của phiên cũ', outOfScope: false }))
      expect(screen.queryByText('Phản hồi của phiên cũ')).not.toBeInTheDocument()
      const log = screen.queryByRole('log')
      if (log) expect(log).toBeEmptyDOMElement()
    },
  )
})

describe('assistant FR015 / AT15 integration', () => {
  beforeEach(() => {
    vi.mocked(askAssistant).mockReset().mockResolvedValue(systemAnswer)
  })

  it('keeps out-of-scope actions in the UI and sends only role and text in history', async () => {
    const user = userEvent.setup()
    vi.mocked(askAssistant).mockResolvedValueOnce(knowledgeAnswer)
    mount()
    await user.click(screen.getByRole('button', { name: 'Mở trợ lý CNHSK' }))
    await send('Tôi muốn hỏi về một từ')
    expect(screen.getByRole('link', { name: 'Tra từ điển' })).toHaveAttribute('href', '/dictionary')
    expect(screen.getByRole('link', { name: 'Dịch câu' })).toHaveAttribute('href', '/translate')
    await send('Tôi chọn chủ đề ở đâu?')
    expect(vi.mocked(askAssistant).mock.calls[1]![0].history).toEqual([
      { role: 'user', text: 'Tôi muốn hỏi về một từ' },
      { role: 'assistant', text: knowledgeAnswer.answer },
    ])
  })

  it.each(assistantErrors)(
    'selects the message for $status / $errorCode and resets it with a new chat',
    async (fixture) => {
      const user = userEvent.setup()
      vi.mocked(askAssistant).mockRejectedValueOnce(new ApiError(fixture))
      mount()
      await user.click(screen.getByRole('button', { name: 'Mở trợ lý CNHSK' }))
      await send('Tôi cần hướng dẫn sử dụng')
      expect(screen.getByRole('alert').textContent).toBe(fixture.expected)
      expect(screen.queryByRole('status')).not.toBeInTheDocument()
      await user.type(screen.getByRole('textbox'), 'Bản nháp chưa gửi')
      await user.click(screen.getByRole('button', { name: 'Cuộc trò chuyện mới' }))
      expect(screen.queryByRole('alert')).not.toBeInTheDocument()
      expect(screen.getByRole('log')).toBeEmptyDOMElement()
      expect(screen.getByRole('textbox')).toHaveValue('')
      await send('Một cuộc trò chuyện mới')
      expect(vi.mocked(askAssistant).mock.calls[1]![0].history).toEqual([])
      expect(screen.getByRole('log')).toHaveTextContent(systemAnswer.answer)
    },
  )

  it('Escape keeps an active request, conversation and draft, and restores launcher focus', async () => {
    const user = userEvent.setup()
    let resolve: (result: AskResult) => void = () => undefined
    vi.mocked(askAssistant).mockImplementationOnce(
      () =>
        new Promise((done) => {
          resolve = done
        }),
    )
    mount()
    await user.click(screen.getByRole('button', { name: 'Mở trợ lý CNHSK' }))
    await user.type(screen.getByRole('textbox'), 'Câu đang gửi')
    await user.click(screen.getByRole('button', { name: 'Gửi' }))
    await user.type(screen.getByRole('textbox'), 'Câu tiếp theo')
    const signal = vi.mocked(askAssistant).mock.calls[0]![1]!
    await user.keyboard('{Escape}')
    const launcher = screen.getByRole('button', { name: 'Mở trợ lý CNHSK' })
    expect(launcher).toHaveFocus()
    expect(signal.aborted).toBe(false)
    expect(screen.queryByRole('region', { name: 'Trợ lý hỗ trợ CNHSK' })).not.toBeInTheDocument()
    await user.click(launcher)
    expect(screen.getByRole('status')).toHaveTextContent('Trợ lý đang trả lời')
    expect(screen.getByRole('textbox')).toHaveValue('Câu tiếp theo')
    expect(screen.getByRole('log')).toHaveTextContent('Câu đang gửi')
    await user.click(screen.getByRole('button', { name: 'Gửi' }))
    expect(askAssistant).toHaveBeenCalledOnce()
    await act(async () => resolve(systemAnswer))
    expect(screen.getByRole('log')).toHaveTextContent(systemAnswer.answer)
    expect(screen.getByRole('button', { name: 'Gửi' })).toBeEnabled()
  })

  it('a new chat can finish before an old cancelled request without stale content', async () => {
    const user = userEvent.setup()
    let resolveOld: (result: AskResult) => void = () => undefined
    vi.mocked(askAssistant).mockImplementationOnce(
      () =>
        new Promise((done) => {
          resolveOld = done
        }),
    )
    mount()
    await user.click(screen.getByRole('button', { name: 'Mở trợ lý CNHSK' }))
    await user.type(screen.getByRole('textbox'), 'Câu của phiên cũ')
    await user.click(screen.getByRole('button', { name: 'Gửi' }))
    const oldSignal = vi.mocked(askAssistant).mock.calls[0]![1]!
    await user.click(screen.getByRole('button', { name: 'Cuộc trò chuyện mới' }))
    expect(oldSignal.aborted).toBe(true)
    await send('Câu của phiên mới')
    await act(async () => resolveOld(knowledgeAnswer))
    expect(screen.getByRole('log')).toHaveTextContent(systemAnswer.answer)
    expect(screen.queryByText(knowledgeAnswer.answer)).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Dịch câu' })).not.toBeInTheDocument()
    expect(vi.mocked(askAssistant).mock.calls[1]![0].history).toEqual([])
  })
})
