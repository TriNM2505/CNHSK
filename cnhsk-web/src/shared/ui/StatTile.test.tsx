import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { StatTile } from './StatTile'

describe('StatTile', () => {
  it('renders a label and tabular value', () => {
    render(<StatTile label="Đang học" value={3} />)

    expect(screen.getByText('Đang học')).toBeVisible()
    expect(screen.getByText('3')).toHaveClass('tabular-nums')
  })
})
