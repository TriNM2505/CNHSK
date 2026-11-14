import { createElement } from 'react'
import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it } from 'vitest'
import { previewRegistry } from '@/app/preview/registry'
import { contestSessionFixture, unpublishedFixture } from './test-fixtures'

const previews = previewRegistry.filter(({ id }) => id.startsWith('contests-'))

afterEach(cleanup)
describe('contest preview catalog', () => {
  it('registers the four canonical screens and their states without duplicate preview ids', () => {
    expect(new Set(previews.map(({ scr }) => scr))).toEqual(
      new Set(['SCR-048', 'SCR-049', 'SCR-050', 'SCR-051']),
    )
    for (const entry of previews)
      expect(previewRegistry.some(({ id }) => id === entry.id)).toBe(true)
    expect(new Set(previews.map(({ id }) => id)).size).toBe(previews.length)
  })
  for (const entry of previews) {
    for (const state of entry.states ?? []) {
      it('renders ' + entry.id + ' / ' + state + ' without a backend', () => {
        const view = render(createElement(MemoryRouter, null, entry.render(state)))
        expect(view.container.querySelector('main')).toBeTruthy()
      })
    }
  }
  it('keeps answer keys out of served items and uses null unpublished score fields', () => {
    for (const item of contestSessionFixture.items) {
      expect(item).not.toHaveProperty('answer')
      expect(item).not.toHaveProperty('accept')
      expect(item).not.toHaveProperty('correct')
    }
    expect(unpublishedFixture.mine).toMatchObject({
      score: null,
      maxScore: null,
      rank: null,
      durationMs: null,
      excluded: null,
    })
    expect(unpublishedFixture.ranking).toBeNull()
    const entry = previews.find(({ id }) => id === 'contests-results-unpublished')!
    render(createElement(MemoryRouter, null, entry.render('ready')))
    expect(screen.getByText('Đang chờ công bố kết quả')).toBeVisible()
    expect(screen.queryByText('Điểm của bạn')).not.toBeInTheDocument()
  })
})
