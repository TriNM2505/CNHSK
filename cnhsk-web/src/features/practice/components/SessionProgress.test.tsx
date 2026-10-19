import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { SessionProgress } from '@/features/practice/components/SessionProgress'

describe('SessionProgress', () => {
  it('announces the current question and total', () => {
    render(<SessionProgress current={3} total={10} />)

    expect(
      screen.getByRole('progressbar', { name: 'Tiến độ lượt luyện: câu 3 trên 10' }),
    ).toHaveAttribute('aria-valuenow', '3')
    expect(screen.getByText('Câu 3/10')).toBeInTheDocument()
  })
})
