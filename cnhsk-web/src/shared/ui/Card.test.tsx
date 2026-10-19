import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { Card } from './Card'

describe('Card', () => {
  it('renders default, interactive and selected variants', () => {
    render(
      <>
        <Card data-testid="default">Mặc định</Card>
        <Card data-testid="interactive" variant="interactive">
          Tương tác
        </Card>
        <Card data-testid="selected" variant="selected">
          Đã chọn
        </Card>
      </>,
    )

    expect(screen.getByTestId('default')).toHaveClass('bg-surface')
    expect(screen.getByTestId('interactive')).toHaveClass('cursor-pointer')
    expect(screen.getByTestId('selected')).toHaveClass('bg-brand-50')
  })
})
