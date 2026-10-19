import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import { PinyinInput } from '@/features/practice/components/PinyinInput'

describe('PinyinInput', () => {
  it('submits a trimmed answer with Enter and exposes its input guidance', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn()
    render(<PinyinInput hint="lời chào" onSubmit={onSubmit} prompt="你好" />)

    const input = screen.getByRole('textbox', { name: 'Pinyin' })
    expect(input).toHaveAccessibleDescription('Gõ số thanh, ví dụ ni3 hao3')
    await user.type(input, '  ni3 hao3  {Enter}')

    expect(onSubmit).toHaveBeenCalledWith('ni3 hao3')
  })
})
