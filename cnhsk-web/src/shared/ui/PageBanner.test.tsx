import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { PageBanner } from './PageBanner'

describe('PageBanner', () => {
  it('renders a single h1 matching title, subtitle, and decorative landscape SVG', () => {
    const { container } = render(
      <PageBanner
        subtitle="Cập nhật tên hiển thị, ảnh đại diện và cấp HSK mục tiêu."
        title="Thông tin cá nhân"
      />,
    )

    const headings = screen.getAllByRole('heading', { level: 1 })
    expect(headings).toHaveLength(1)
    expect(headings[0]).toHaveTextContent('Thông tin cá nhân')
    expect(
      screen.getByText('Cập nhật tên hiển thị, ảnh đại diện và cấp HSK mục tiêu.'),
    ).toBeInTheDocument()

    const svg = container.querySelector('svg')
    expect(svg).not.toBeNull()
    expect(svg).toHaveAttribute('aria-hidden', 'true')

    expect(container.firstElementChild).toHaveClass('isolate')
  })
})
