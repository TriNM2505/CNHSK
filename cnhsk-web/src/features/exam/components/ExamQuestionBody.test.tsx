import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import { speakChinese } from '@/shared/lib/speech'

import { ExamQuestionBody, type AttemptQuestion } from './ExamQuestionBody'

vi.mock('@/shared/lib/speech', () => ({ speakChinese: vi.fn() }))

function renderBody(question: AttemptQuestion, answer: string | null = null, plays = 0) {
  const onAnswer = vi.fn()
  const onPlayed = vi.fn()
  render(
    <ExamQuestionBody
      answer={answer}
      disabled={false}
      onAnswer={onAnswer}
      onPlayed={onPlayed}
      plays={plays}
      question={question}
    />,
  )
  return { onAnswer, onPlayed }
}

describe('HSK paper question body', () => {
  it('reads a listening question aloud without showing what is said', async () => {
    vi.mocked(speakChinese).mockResolvedValue()
    const user = userEvent.setup()
    const { onPlayed } = renderBody(
      {
        no: 1,
        text: '',
        options: [
          { key: 'A', text: 'Đúng' },
          { key: 'B', text: 'Sai' },
        ],
        imageUrl: 'https://img.example/tea.png',
        listening: { speakText: '我喜欢喝茶。', audioUrl: null, maxPlays: 2 },
      },
      null,
      1,
    )
    expect(screen.queryByText('我喜欢喝茶。')).not.toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'Hình của câu 1' })).toHaveAttribute('src', 'https://img.example/tea.png')
    expect(screen.getByText('Còn 1 lượt nghe')).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Nghe' }))
    expect(speakChinese).toHaveBeenCalledWith('我喜欢喝茶。')
    expect(onPlayed).toHaveBeenCalledOnce()
  })

  it('stops playing once the plays are used up', () => {
    renderBody(
      { no: 1, text: '', options: [], listening: { speakText: '你好', audioUrl: null, maxPlays: 2 } },
      null,
      2,
    )
    expect(screen.getByRole('button', { name: 'Nghe' })).toBeDisabled()
    expect(screen.getByText('Đã hết lượt nghe')).toBeVisible()
  })

  it('offers picture options by their letter', async () => {
    const user = userEvent.setup()
    const { onAnswer } = renderBody({
      no: 2,
      text: '他在做什么？',
      options: [
        { key: 'A', text: '', imageUrl: 'https://img.example/a.png' },
        { key: 'B', text: '', imageUrl: 'https://img.example/b.png' },
      ],
    })
    await user.click(screen.getByRole('button', { name: 'Hình B' }))
    expect(onAnswer).toHaveBeenCalledWith('B')
  })

  it('draws emoji pictures large and offers them by letter', async () => {
    const user = userEvent.setup()
    const { onAnswer } = renderBody({
      no: 5,
      text: '🐶 小狗',
      options: [
        { key: 'A', text: '🐱' },
        { key: 'B', text: '🐶' },
      ],
    })
    expect(screen.getByRole('img', { name: 'Hình minh hoạ' })).toHaveTextContent('🐶')
    expect(screen.getByText('小狗')).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Hình A' }))
    expect(onAnswer).toHaveBeenCalledWith('A')
  })

  it('sends the fragment order once every fragment is placed', async () => {
    const user = userEvent.setup()
    const { onAnswer } = renderBody({
      no: 3,
      text: '',
      kind: 'order',
      options: [
        { key: 'A', text: '喜欢' },
        { key: 'B', text: '我' },
        { key: 'C', text: '看书' },
      ],
    })
    await user.click(screen.getByRole('button', { name: '我' }))
    await user.click(screen.getByRole('button', { name: '喜欢' }))
    expect(onAnswer).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: '看书' }))
    expect(onAnswer).toHaveBeenCalledWith('BAC')
    expect(screen.getByLabelText('Câu bạn ghép')).toHaveTextContent('我喜欢看书')
  })

  it('saves the typed characters of a fill-in question', async () => {
    const user = userEvent.setup()
    const { onAnswer } = renderBody({ no: 4, text: '我们一起 ___（xué）习吧。', kind: 'fill', options: [] })
    await user.type(screen.getByLabelText('Chữ Hán cần điền'), ' 学 ')
    await user.click(screen.getByRole('button', { name: 'Lưu đáp án' }))
    expect(onAnswer).toHaveBeenCalledWith('学')
  })
})
