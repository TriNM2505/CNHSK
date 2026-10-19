import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { Button } from './Button'
import { PageHeader } from './PageHeader'

describe('PageHeader', () => {
  it('renders its title, subtitle and actions', () => {
    render(<PageHeader actions={<Button>Lưu</Button>} subtitle="Mô tả màn hình" title="Hồ sơ" />)

    expect(screen.getByRole('heading', { level: 1, name: 'Hồ sơ' })).toHaveClass('text-h1-sm')
    expect(screen.getByText('Mô tả màn hình')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Lưu' })).toBeVisible()
  })
})
