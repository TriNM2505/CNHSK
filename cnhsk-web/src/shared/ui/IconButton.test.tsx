import { render, screen } from '@testing-library/react'
import { Trash2, X } from 'lucide-react'
import { describe, expect, it } from 'vitest'

import { IconButton } from './IconButton'

describe('IconButton', () => {
  it('requires an accessible label and renders both variants', () => {
    render(
      <>
        <IconButton label="Đóng">
          <X aria-hidden="true" />
        </IconButton>
        <IconButton label="Xóa" variant="danger">
          <Trash2 aria-hidden="true" />
        </IconButton>
      </>,
    )

    expect(screen.getByRole('button', { name: 'Đóng' })).toBeVisible()
    expect(screen.getByRole('button', { name: 'Xóa' })).toHaveClass('hover:text-danger')
  })

  it('forwards the disabled state', () => {
    render(
      <IconButton disabled label="Đóng">
        <X aria-hidden="true" />
      </IconButton>,
    )

    expect(screen.getByRole('button', { name: 'Đóng' })).toBeDisabled()
  })
})
