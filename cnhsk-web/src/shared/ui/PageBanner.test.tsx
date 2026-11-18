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

  it('hides decorative SVG below lg so 200% zoom viewports keep text clear, while rendering actions', () => {
    const { container } = render(
      <PageBanner
        actions={<button type="button">Thao tác</button>}
        subtitle="Mô tả ngắn gọn."
        title="Tiêu đề"
      />,
    )

    const svg = container.querySelector('svg')
    expect(svg).not.toBeNull()
    expect(svg).toHaveClass('hidden', 'lg:block')
    expect(svg).not.toHaveClass('sm:block')
    expect(container.firstElementChild).toHaveClass('isolate')
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Tiêu đề')
    expect(screen.getByText('Mô tả ngắn gọn.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Thao tác' })).toBeInTheDocument()
  })
})
