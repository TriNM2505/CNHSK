import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'

import { TopicLockNotice } from './TopicLockNotice'

describe('topic lock reason', () => {
  it('shows a lock icon, readable reason and independent links to every prerequisite', () => {
    const { container } = render(
      <MemoryRouter>
        <TopicLockNotice
          lockedBy={[
            { id: 11, name: 'Chào hỏi' },
            { id: 12, name: 'Gia đình 家庭' },
          ]}
          requestId="req-lock"
        />
      </MemoryRouter>,
    )
    expect(screen.getByRole('status')).toHaveTextContent('Đang khoá')
    expect(screen.getByRole('status')).toHaveTextContent(
      'Cần đạt 90% bài kiểm tra: Chào hỏi, Gia đình 家庭',
    )
    expect(container.querySelector('svg')).toHaveAttribute('aria-hidden', 'true')
    expect(screen.getByRole('link', { name: 'Chào hỏi' })).toHaveAttribute(
      'href',
      '/learn/topics/11',
    )
    expect(screen.getByRole('link', { name: 'Gia đình 家庭' })).toHaveAttribute(
      'href',
      '/learn/topics/12',
    )
    expect(screen.getByText('Mã yêu cầu: req-lock')).toBeVisible()
    expect(container.querySelector('a a')).toBeNull()
  })
})
