import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { StatusBadge, type StatusTone } from './StatusBadge'

describe('StatusBadge', () => {
  it('renders every tone with an icon and text', () => {
    const tones: StatusTone[] = ['success', 'warning', 'danger', 'info', 'neutral']

    render(
      <>
        {tones.map((tone) => (
          <StatusBadge key={tone} tone={tone}>
            {tone}
          </StatusBadge>
        ))}
      </>,
    )

    for (const tone of tones) {
      const badge = screen.getByText(tone).parentElement
      expect(badge).toBeVisible()
      expect(badge?.querySelector('svg')).not.toBeNull()
    }
  })
})
