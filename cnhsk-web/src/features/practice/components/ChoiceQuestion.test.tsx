import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import { ChoiceQuestion } from '@/features/practice/components/ChoiceQuestion'
import type { ServedItemView } from '@/features/practice/types'

const item: ServedItemView = {
  no: 1,
  options: [
    { key: 'A', text: 'chúng tôi' },
    { key: 'B', text: 'một phương án rất dài để kiểm tra khả năng xuống dòng trên màn hình nhỏ' },
  ],
  prompt: { hint: 'Đại từ số nhiều', text: '我们' },
  rules: {},
  skill: 'VOCAB',
  type: 'MEANING_CHOICE',
}

describe('ChoiceQuestion', () => {
  it('FR043_does not reveal supplied grading feedback in DEFERRED mode', () => {
    render(
      <ChoiceQuestion
        feedbackMode="DEFERRED"
        item={item}
        onAnswer={vi.fn()}
        result={{ no: 1, correct: false, correctAnswer: 'B', explanation: 'Server explanation' }}
      />,
    )

    expect(screen.queryByText('Chưa chính xác')).not.toBeInTheDocument()
    expect(screen.queryByText(/Đáp án đúng/)).not.toBeInTheDocument()
    expect(screen.queryByText('Server explanation')).not.toBeInTheDocument()
  })

  it('FR043_allows retry after an unsuccessful IMMEDIATE answer', async () => {
    const user = userEvent.setup()
    const onAnswer = vi
      .fn()
      .mockRejectedValueOnce(new Error('network'))
      .mockResolvedValueOnce(undefined)
    render(<ChoiceQuestion feedbackMode="IMMEDIATE" item={item} onAnswer={onAnswer} />)

    await user.click(screen.getByRole('button', { name: /chúng tôi/i }))
    await user.click(screen.getByRole('button', { name: /một phương án/i }))

    expect(onAnswer).toHaveBeenNthCalledWith(2, 'B')
  })

  it('locks IMMEDIATE choices after the first selection', async () => {
    const user = userEvent.setup()
    const onAnswer = vi.fn().mockResolvedValue(undefined)
    render(<ChoiceQuestion feedbackMode="IMMEDIATE" item={item} onAnswer={onAnswer} />)

    await user.click(screen.getByRole('button', { name: /chúng tôi/i }))
    await user.click(screen.getByRole('button', { name: /một phương án/i }))

    expect(onAnswer).toHaveBeenCalledOnce()
    expect(onAnswer).toHaveBeenCalledWith('A')
    expect(screen.getByRole('button', { name: /một phương án/i })).toBeDisabled()
  })

  it('allows changing a DEFERRED draft and shows server feedback with text and icon', async () => {
    const user = userEvent.setup()
    const onAnswer = vi.fn().mockResolvedValue(undefined)
    const { rerender } = render(
      <ChoiceQuestion feedbackMode="DEFERRED" item={item} onAnswer={onAnswer} />,
    )

    await user.click(screen.getByRole('button', { name: /chúng tôi/i }))
    await user.click(screen.getByRole('button', { name: /một phương án/i }))
    expect(onAnswer).toHaveBeenNthCalledWith(1, 'A')
    expect(onAnswer).toHaveBeenNthCalledWith(2, 'B')

    rerender(
      <ChoiceQuestion
        feedbackMode="IMMEDIATE"
        item={item}
        onAnswer={onAnswer}
        result={{
          correct: false,
          correctAnswer: 'A',
          explanation: '“我们” nghĩa là “chúng tôi”.',
          no: 1,
        }}
        selectedAnswer="B"
      />,
    )
    expect(screen.getByText('Chưa chính xác')).toBeInTheDocument()
    expect(screen.getByText('Đáp án đúng: A — chúng tôi')).toBeInTheDocument()
    expect(screen.getByText('“我们” nghĩa là “chúng tôi”.')).toBeInTheDocument()
  })
})
