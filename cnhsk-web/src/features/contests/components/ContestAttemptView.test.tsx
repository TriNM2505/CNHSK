import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ComponentProps } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'

import { contestSessionFixture } from '../test-fixtures'
import { ContestAttemptView } from './ContestAttemptView'

function mount(overrides: Partial<ComponentProps<typeof ContestAttemptView>> = {}) {
  const props: ComponentProps<typeof ContestAttemptView> = {
    name: 'Cuộc thi đọc hiểu',
    state: 'ready',
    session: contestSessionFixture,
    remainingMs: 60_000,
    choices: { 1: 'A' },
    saveStates: { 1: 'saved' },
    locked: false,
    submitting: false,
    submitted: false,
    onRetry: vi.fn(),
    onSelectQuestion: vi.fn(),
    onChoose: vi.fn().mockResolvedValue(false),
    onSubmit: vi.fn(),
    onRetrySubmit: vi.fn(),
    ...overrides,
  }
  render(
    <MemoryRouter>
      <ContestAttemptView {...props} />
    </MemoryRouter>,
  )
  return props
}

describe('ContestAttemptView answers', () => {
  it('keeps the accepted selection when the hook refuses a choice', async () => {
    const user = userEvent.setup()
    const props = mount()
    const options = within(screen.getByRole('group', { name: 'Các phương án' }))
    await user.click(options.getByRole('button', { name: /B.*骑自行车/ }))
    expect(props.onChoose).toHaveBeenCalledOnce()
    expect(props.onChoose).toHaveBeenCalledWith(1, 'B')
    expect(options.getByRole('button', { name: /A.*坐公共汽车/ })).toHaveAttribute(
      'aria-pressed',
      'true',
    )
    expect(options.getByRole('button', { name: /B.*骑自行车/ })).toHaveAttribute(
      'aria-pressed',
      'false',
    )
    expect(screen.queryByText(/Đáp án đúng|Giải thích/)).not.toBeInTheDocument()
  })

  it('locks every answer without changing the restored selection', async () => {
    const user = userEvent.setup()
    const props = mount({ locked: true })
    const options = within(screen.getByRole('group', { name: 'Các phương án' }))
    for (const button of options.getAllByRole('button')) expect(button).toBeDisabled()
    await user.click(options.getByRole('button', { name: /B.*骑自行车/ }))
    expect(props.onChoose).not.toHaveBeenCalled()
    expect(options.getByRole('button', { name: /A.*坐公共汽车/ })).toHaveAttribute(
      'aria-pressed',
      'true',
    )
  })

  it('preserves the optional sentence and hint when the main prompt is absent', () => {
    const session = {
      ...contestSessionFixture,
      items: [
        {
          ...contestSessionFixture.items[0]!,
          prompt: { sentence: '今天下雨了。', hint: 'Chọn phương án phù hợp với câu.' },
        },
      ],
    }
    mount({ session })
    expect(screen.getByRole('heading', { name: 'Chọn đáp án phù hợp' })).toBeVisible()
    expect(screen.getByText('今天下雨了。')).toHaveAttribute('lang', 'zh-CN')
    expect(screen.getByText('Gợi ý: Chọn phương án phù hợp với câu.')).toBeVisible()
    expect(screen.getByRole('group', { name: 'Các phương án' })).toBeVisible()
  })
})
