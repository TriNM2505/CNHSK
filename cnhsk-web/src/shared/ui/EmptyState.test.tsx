import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { Button } from '@/shared/ui/Button'
import { EmptyState } from '@/shared/ui/EmptyState'

describe('EmptyState', () => {
  it('renders the reason and an optional next action', () => {
    render(
      <EmptyState
        action={<Button>Tạo mục đầu tiên</Button>}
        description="Danh sách sẽ xuất hiện sau khi bạn tạo dữ liệu."
        title="Chưa có dữ liệu"
      />,
    )

    expect(screen.getByRole('heading', { name: 'Chưa có dữ liệu' })).toBeVisible()
    expect(screen.getByText('Danh sách sẽ xuất hiện sau khi bạn tạo dữ liệu.')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Tạo mục đầu tiên' })).toBeVisible()
  })
})
