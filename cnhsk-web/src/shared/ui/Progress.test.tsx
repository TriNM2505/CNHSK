import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { Progress } from '@/shared/ui/Progress'

describe('Progress', () => {
  it('always exposes and renders its value as text', () => {
    render(<Progress label="7 trên 10 bài" max={10} value={7} />)

    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '7')
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuetext', '7 trên 10 bài')
    expect(screen.getByText('7 trên 10 bài')).toBeVisible()
  })

  it('clamps an out-of-range value and supports every tone', () => {
    const { container } = render(
      <>
        <Progress tone="brand" value={120} />
        <Progress tone="success" value={50} />
        <Progress tone="accent" value={25} />
      </>,
    )

    expect(screen.getAllByRole('progressbar')[0]).toHaveAttribute('aria-valuenow', '100')
    expect(screen.getByText('100%')).toBeVisible()
    expect(container.querySelector('.bg-brand-600')).toBeInTheDocument()
    expect(container.querySelector('.bg-success')).toBeInTheDocument()
    expect(container.querySelector('.bg-accent-500')).toBeInTheDocument()
  })
})
