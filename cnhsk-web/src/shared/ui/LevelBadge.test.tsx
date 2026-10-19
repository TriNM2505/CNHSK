import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { LevelBadge, type HskLevel } from './LevelBadge'

describe('LevelBadge', () => {
  it('renders levels 1 through 9 with fixed color groups', () => {
    const levels: HskLevel[] = [1, 2, 3, 4, 5, 6, 7, 8, 9]

    render(
      <>
        {levels.map((level) => (
          <LevelBadge exact key={level} level={level} />
        ))}
      </>,
    )

    expect(screen.getByText('HSK 1')).toHaveClass('bg-success-bg')
    expect(screen.getByText('HSK 3')).toHaveClass('bg-info-bg')
    expect(screen.getByText('HSK 5')).toHaveClass('bg-warning-bg')
    expect(screen.getByText('HSK 8')).toHaveClass('bg-brand-50')
  })

  it('groups levels 7 to 9 unless exact is requested', () => {
    const { rerender } = render(<LevelBadge level={8} />)
    expect(screen.getByText('HSK 7–9')).toBeVisible()

    rerender(<LevelBadge exact level={8} />)
    expect(screen.getByText('HSK 8')).toBeVisible()
  })
})
