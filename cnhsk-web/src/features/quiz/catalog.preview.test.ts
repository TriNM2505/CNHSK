import { createElement } from 'react'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { apiRequest } from '@/shared/api'
import { previewRegistry } from '@/app/preview/registry'

vi.mock('@/shared/api', async (importOriginal) => ({
  ...(await importOriginal<object>()),
  apiRequest: vi.fn(),
}))
describe('quiz attempt/result previews', () => {
  it.each(['quiz-attempt', 'quiz-result'])(
    '%s renders ready content and four isolated states without requests',
    (id) => {
      const entry = previewRegistry.find((value) => value.id === id)
      if (!entry) throw new Error('Missing quiz preview')
      for (const state of ['loading', 'empty', 'error', 'ready'] as const) {
        const view = render(createElement(MemoryRouter, null, entry.render(state)))
        if (state === 'empty') expect(screen.getByText('Chưa có nội dung quiz')).toBeVisible()
        if (state === 'error')
          expect(screen.getByText('Không thể tải quiz. Vui lòng thử lại.')).toBeVisible()
        if (state === 'ready') expect(screen.getByRole('heading', { level: 1 })).toBeVisible()
        view.unmount()
      }
      expect(apiRequest).not.toHaveBeenCalled()
    },
  )
})
