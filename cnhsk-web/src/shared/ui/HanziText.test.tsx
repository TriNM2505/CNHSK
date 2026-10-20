import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { HanziText } from './HanziText'

describe('HanziText', () => {
  it('always uses the Chinese language and Hanzi font for every size', () => {
    const { rerender } = render(<HanziText size="lg">学习</HanziText>)
    expect(screen.getByText('学习')).toHaveAttribute('lang', 'zh-CN')
    expect(screen.getByText('学习')).toHaveClass('font-hanzi', 'text-hanzi-lg')

    rerender(<HanziText size="md">汉字</HanziText>)
    expect(screen.getByText('汉字')).toHaveClass('text-hanzi-md')

    rerender(<HanziText size="sm">你好</HanziText>)
    expect(screen.getByText('你好')).toHaveClass('text-h2')
  })
})
