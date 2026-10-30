import { describe, expect, it } from 'vitest'
import { canEditComment, canModerateComment } from './comment-permissions'
const created = Date.parse('2026-10-30T00:00:00Z')
const comment = { author: { id: 7 }, status: 'VISIBLE' as const, createdAt: new Date(created).toISOString() }
describe('comment eligibility', () => {
  it.each([[899999, true], [900000, false], [900001, false]])('FR024_UI conservative edit cutoff at %i milliseconds (AT-25/49)', (elapsed, allowed) => {
    expect(canEditComment(comment, 7, created + elapsed)).toBe(allowed)
  })
  it('FR024_requires owner, visible state and a valid nonfuture timestamp', () => {
    expect(canEditComment(comment, 8, created)).toBe(false)
    expect(canEditComment(comment, undefined, created)).toBe(false)
    expect(canEditComment({ ...comment, author: null }, 7, created)).toBe(false)
    expect(canEditComment({ ...comment, status: 'HIDDEN' }, 7, created)).toBe(false)
    expect(canEditComment({ ...comment, createdAt: 'invalid' }, 7, created)).toBe(false)
    expect(canEditComment(comment, 7, created - 1)).toBe(false)
  })
  it('FR054_blocks own moderation but allows another moderator author (AT-45)', () => {
    const target = { ...comment, moderationReason: null }
    expect(canModerateComment(target, 7, 'HIDE')).toBe(false)
    expect(canModerateComment(target, 8, 'HIDE')).toBe(true)
    expect(canModerateComment(target, 8, 'RESTORE')).toBe(false)
  })
  it('FR053_AUTHOR_DELETED cannot restore, while moderated hidden/deleted can (AT-46)', () => {
    expect(canModerateComment({ ...comment, status: 'DELETED', moderationReason: 'AUTHOR_DELETED' }, 8, 'RESTORE')).toBe(false)
    expect(canModerateComment({ ...comment, status: 'HIDDEN', moderationReason: 'Reason' }, 8, 'RESTORE')).toBe(true)
  })
})
