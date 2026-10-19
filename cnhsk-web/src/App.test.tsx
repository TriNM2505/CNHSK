import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import App from '@/App'

describe('App', () => {
  it('renders the temporary CNHSK token preview', () => {
    render(<App />)

    expect(screen.getByRole('heading', { name: 'CNHSK' })).toBeVisible()
    expect(screen.getByText('汉字')).toHaveAttribute('lang', 'zh-CN')
  })
})
