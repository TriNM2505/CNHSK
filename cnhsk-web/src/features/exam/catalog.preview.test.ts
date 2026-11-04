import { describe, expect, it } from 'vitest'

import { previewRegistry } from '@/app/preview/registry'

describe('exam preview catalog', () => {
  it('FR024_AT25_registers three placement states and all page states', () => {
    const entries = previewRegistry.filter((entry) => entry.scr === 'SCR-020')
    expect(entries.map((entry) => entry.id)).toEqual([
      'scr-020-new',
      'scr-020-active',
      'scr-020-result',
    ])
    for (const entry of entries) {
      expect(entry.states).toEqual(['ready', 'loading', 'empty', 'error'])
      for (const state of entry.states ?? []) expect(entry.render(state)).toBeTruthy()
    }
  })
})
