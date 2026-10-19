import { describe, expect, it } from 'vitest'

import { cn } from './cn'

describe('cn', () => {
  it('keeps custom typography separate from semantic text colors', () => {
    expect(cn('text-h1', 'text-ink')).toBe('text-h1 text-ink')
    expect(cn('text-body-sm', 'text-danger')).toBe('text-body-sm text-danger')
  })

  it('merges conflicting custom typography classes', () => {
    expect(cn('text-body', 'text-h2')).toBe('text-h2')
    expect(cn('text-h1-sm', 'sm:text-h1', 'text-ink')).toBe('text-h1-sm sm:text-h1 text-ink')
  })
})
