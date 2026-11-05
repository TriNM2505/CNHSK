import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'

import { knowledgeAnswer, markupAnswer, systemAnswer } from '../test-fixtures'
import { AssistantPanel, type AssistantMessage } from './AssistantPanel'

function mount(messages: AssistantMessage[], pending = false) {
  const props = {
    draft: 'Tôi cần hướng dẫn',
    messages,
    pending,
    onDraftChange: vi.fn(),
    onSubmit: vi.fn(),
    onCollapse: vi.fn(),
    onNewConversation: vi.fn(),
  }
  const view = render(
    <MemoryRouter>
      <AssistantPanel {...props} />
    </MemoryRouter>,
  )
  return { ...view, props }
}

describe('assistant FR015 / AT15', () => {
  it('shows the two navigation links only beside an out-of-scope assistant answer', () => {
    mount([
      { role: 'assistant', text: systemAnswer.answer },
      { role: 'assistant', text: knowledgeAnswer.answer, outOfScope: true },
      { role: 'user', text: 'Câu hỏi của người học', outOfScope: true },
    ])
    expect(screen.getAllByRole('link')).toHaveLength(2)
    expect(screen.getByRole('link', { name: 'Tra từ điển' })).toHaveAttribute('href', '/dictionary')
    expect(screen.getByRole('link', { name: 'Dịch câu' })).toHaveAttribute('href', '/translate')
    const answer = screen.getByText(knowledgeAnswer.answer).parentElement!
    expect(within(answer).getAllByRole('link')).toHaveLength(2)
    expect(
      within(screen.getByText(systemAnswer.answer).parentElement!).queryByRole('link'),
    ).not.toBeInTheDocument()
    expect(
      within(screen.getByText('Câu hỏi của người học').parentElement!).queryByRole('link'),
    ).not.toBeInTheDocument()
  })

  it('renders HTML-like responses as plain text', () => {
    const { container } = mount([{ role: 'assistant', text: markupAnswer.answer }])
    expect(screen.getByRole('log').textContent).toContain(markupAnswer.answer)
    expect(container.querySelector('b, script, img, [onerror]')).toBeNull()
  })

  it('announces pending, disables submit and allows a new conversation', async () => {
    const user = userEvent.setup()
    const { props } = mount([{ role: 'user', text: 'Tôi cần hướng dẫn' }], true)
    expect(screen.getByRole('status')).toHaveTextContent('Trợ lý đang trả lời')
    await user.click(screen.getByRole('button', { name: 'Gửi' }))
    expect(props.onSubmit).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: 'Cuộc trò chuyện mới' }))
    expect(props.onNewConversation).toHaveBeenCalledOnce()
  })

  it('collapses on Escape from the panel input', async () => {
    const user = userEvent.setup()
    const { props } = mount([])
    expect(screen.getByRole('textbox')).toHaveFocus()
    await user.keyboard('{Escape}')
    expect(props.onCollapse).toHaveBeenCalledOnce()
    expect(props.onNewConversation).not.toHaveBeenCalled()
  })
})
