import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'

import { Button } from './Button'

describe('Button', () => {
  it('renders every visual variant and size', () => {
    const variants = ['primary', 'secondary', 'outline', 'ghost', 'danger'] as const

    render(
      <>
        {variants.map((variant) => (
          <Button key={variant} size={variant === 'primary' ? 'sm' : 'md'} variant={variant}>
            {variant}
          </Button>
        ))}
      </>,
    )

    expect(screen.getAllByRole('button')).toHaveLength(5)
    expect(screen.getByRole('button', { name: 'primary' })).toHaveClass('bg-brand-600')
    expect(screen.getByRole('button', { name: 'danger' })).toHaveClass('bg-danger')
  })

  it('renders a native button by default and allows submit type', () => {
    render(<Button type="submit">Lưu</Button>)

    expect(screen.getByRole('button', { name: 'Lưu' })).toHaveAttribute('type', 'submit')
  })

  it('renders internal navigation with Link', () => {
    render(
      <MemoryRouter future={{ v7_relativeSplatPath: true, v7_startTransition: true }}>
        <Button to="/ho-so">Hồ sơ</Button>
      </MemoryRouter>,
    )

    expect(screen.getByRole('link', { name: 'Hồ sơ' })).toHaveAttribute('href', '/ho-so')
  })

  it('renders an external anchor and protects a new tab', () => {
    render(
      <Button href="https://game.cnhsk.com" rel="noopener" target="_blank">
        Chơi game
      </Button>,
    )

    expect(screen.getByRole('link', { name: 'Chơi game' })).toHaveAttribute(
      'rel',
      'noopener noreferrer',
    )
  })

  it('keeps its label and blocks interaction while loading', async () => {
    const user = userEvent.setup()
    const onClick = vi.fn()
    render(
      <Button loading onClick={onClick}>
        Đăng nhập
      </Button>,
    )

    const button = screen.getByRole('button', { name: 'Đăng nhập' })
    expect(button).toBeDisabled()
    expect(button).toHaveAttribute('aria-busy', 'true')
    await user.click(button)
    expect(onClick).not.toHaveBeenCalled()
  })
})
