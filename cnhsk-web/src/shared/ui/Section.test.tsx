import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { Button } from './Button'
import { Section } from './Section'

describe('Section', () => {
  it('labels the region and renders subtitle, action and content', () => {
    render(
      <Section actions={<Button>Thêm</Button>} subtitle="Nội dung phụ" title="Chủ đề">
        <p>Danh sách</p>
      </Section>,
    )

    expect(screen.getByRole('region', { name: 'Chủ đề' })).toBeVisible()
    expect(screen.getByText('Nội dung phụ')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Thêm' })).toBeVisible()
    expect(screen.getByText('Danh sách')).toBeVisible()
  })
})
