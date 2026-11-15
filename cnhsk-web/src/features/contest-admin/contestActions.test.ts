import { describe, expect, it } from 'vitest'
import { actionReason, editReason } from './contestActions'
import { contestFixture, endedFixture, publishedFixture } from './test-fixtures'

describe('contest action restrictions', () => {
  it('locks cancelled and published started contests while keeping even past drafts editable', () => {
    expect(editReason({ ...contestFixture, status: 'CANCELLED' })).toBeTruthy()
    expect(editReason(endedFixture)).toBeTruthy()
    expect(editReason({ ...contestFixture, status: 'PUBLISHED', state: 'ONGOING' })).toBeTruthy()
    expect(editReason({ ...endedFixture, status: 'DRAFT' })).toBeUndefined()
    expect(editReason({ ...contestFixture, status: 'PUBLISHED' })).toBeUndefined()
  })
  it('AT21 blocks ranking before endsAt, permits it exactly at endsAt with ended server state', () => {
    const end = Date.parse(endedFixture.endsAt)
    expect(actionReason(endedFixture, 'ranking', end - 1)).toBeTruthy()
    expect(actionReason(endedFixture, 'ranking', end)).toBeUndefined()
    expect(actionReason({ ...endedFixture, state: 'ONGOING' }, 'ranking', end + 1)).toBeTruthy()
    expect(actionReason(publishedFixture, 'ranking', end + 1)).toBeTruthy()
    expect(actionReason({ ...endedFixture, status: 'CANCELLED' }, 'ranking', end + 1)).toBeTruthy()
  })
  it('requires future publication from DRAFT and an exam, and never cancels a contest with attempts', () => {
    const start = Date.parse(contestFixture.startsAt)
    expect(actionReason(contestFixture, 'publish', start - 1)).toBeUndefined()
    expect(actionReason(contestFixture, 'publish', start)).toBeTruthy()
    expect(actionReason({ ...contestFixture, examId: null }, 'publish', start - 1)).toBeTruthy()
    expect(
      actionReason({ ...contestFixture, status: 'PUBLISHED' }, 'publish', start - 1),
    ).toBeTruthy()
    expect(actionReason(contestFixture, 'cancel', start)).toBeUndefined()
    expect(actionReason(endedFixture, 'cancel', start)).toBeTruthy()
  })
})
