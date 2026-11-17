import { describe, expect, it } from 'vitest'

import {
  parseReviewQueueQuery,
  rejectionReasonError,
  reviewDetailTo,
  reviewQueueBackTo,
  reviewQueueParams,
  reviewQueueSearch,
  validReviewDate,
} from './reviewQueueQuery'

describe('queue query and safe detail context', () => {
  it('normalizes invalid page, size, level, KID length and dates', () => {
    expect(
      parseReviewQueueQuery(
        new URLSearchParams({
          page: '-1',
          size: '51',
          hsk: '9',
          kid: '词'.repeat(21),
          from: '2026-02-30',
          to: 'garbage',
        }),
      ),
    ).toEqual({ kid: '词'.repeat(20), hsk: '', from: '', to: '', page: 0, size: 20 })
  })
  it.each(['1', '2', '3'])('keeps approved UI level %s', (hsk) => {
    expect(parseReviewQueueQuery(new URLSearchParams({ hsk })).hsk).toBe(hsk)
  })
  it('rejects reversed date range and accepts leap day only when valid', () => {
    expect(validReviewDate('2024-02-29')).toBe(true)
    expect(validReviewDate('2026-02-29')).toBe(false)
    expect(
      parseReviewQueueQuery(new URLSearchParams({ from: '2026-11-17', to: '2026-11-16' })).to,
    ).toBe('')
  })
  it('converts Vietnam calendar bounds into explicit inclusive UTC instants', () => {
    expect(
      reviewQueueParams(
        parseReviewQueueQuery(
          new URLSearchParams({ from: '2026-11-17', to: '2026-11-17', hsk: '2', kid: 'TV1-901' }),
        ),
      ),
    ).toEqual({
      kid: 'TV1-901',
      hsk: 2,
      from: '2026-11-16T17:00:00.000Z',
      to: '2026-11-17T16:59:59.999Z',
      page: 0,
      size: 20,
    })
  })
  it('constructs internal queue returns with only whitelisted parameters', () => {
    const query = parseReviewQueueQuery(new URLSearchParams('hsk=2&page=3&size=50&kid=TV1-901'))
    expect(reviewDetailTo(901, query)).toBe(
      `/content/questions/901?reviewQueue=1&${reviewQueueSearch(query)}`,
    )
    const back = reviewQueueBackTo(
      new URLSearchParams(
        'reviewQueue=1&hsk=2&page=3&returnTo=https://evil.test&redirect=//evil.test',
      ),
    )!
    expect(back).toBe('/content/ai-review?hsk=2&page=3&size=20')
    expect(reviewQueueBackTo(new URLSearchParams('returnTo=/content/ai-review'))).toBeUndefined()
  })
  it.each(['', '  \u2003 ', '词'.repeat(1001), '😀'.repeat(1001)])(
    'rejects invalid rejection reason length',
    (value) => {
      expect(rejectionReasonError(value)).toBeTruthy()
    },
  )
  it.each([' 词 ', '词'.repeat(1000), '😀'.repeat(1000)])(
    'counts valid reason boundaries in Unicode code points',
    (value) => {
      expect(rejectionReasonError(value)).toBeUndefined()
    },
  )
})
