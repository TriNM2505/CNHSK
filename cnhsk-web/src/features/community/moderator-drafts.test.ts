import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanModeratorDrafts, moderatorDraftKey, MODERATOR_DRAFT_PREFIX, MODERATOR_DRAFT_TTL_MS, readModeratorDraft, removeModeratorDraft, writeModeratorDraft, type ModeratorDraftIdentity } from './moderator-drafts'

const identity: ModeratorDraftIdentity = { userId: 100, screenId: 'SCR-061', resourceId: 42, field: 'rejectionReason' }
beforeEach(() => { sessionStorage.clear(); vi.useFakeTimers(); vi.setSystemTime(new Date('2026-10-30T03:00:00Z')) })
afterEach(() => { vi.restoreAllMocks(); vi.useRealTimers(); sessionStorage.clear() })
describe('moderator typed-text draft policy', () => {
  it('FR060_persists only typed text and savedAt under owner screen resource field', () => {
    writeModeratorDraft(identity, '  Văn bản tự nhập\n中文  ')
    expect(sessionStorage.key(0)).toBe(`${MODERATOR_DRAFT_PREFIX}100:SCR-061:42:rejectionReason`)
    expect(JSON.parse(sessionStorage.getItem(moderatorDraftKey(identity))!)).toEqual({ text: '  Văn bản tự nhập\n中文  ', savedAt: Date.now() })
    expect(readModeratorDraft(identity)).toBe('  Văn bản tự nhập\n中文  ')
  })
  it.each([MODERATOR_DRAFT_TTL_MS - 1000, MODERATOR_DRAFT_TTL_MS, MODERATOR_DRAFT_TTL_MS + 1])('FR060_enforces TTL on mount/read at age %s', (age) => {
    writeModeratorDraft(identity, 'Draft')
    vi.advanceTimersByTime(age)
    cleanModeratorDrafts(identity.userId)
    expect(readModeratorDraft(identity)).toBe(age < MODERATOR_DRAFT_TTL_MS ? 'Draft' : '')
    expect(sessionStorage.getItem(moderatorDraftKey(identity)) !== null).toBe(age < MODERATOR_DRAFT_TTL_MS)
  })
  it.each(['{', '{}', '{"text":"draft"}', '{"text":"draft","savedAt":"bad"}', '{"text":"draft","savedAt":null}', '{"text":"draft","savedAt":-1}', '{"text":"draft","savedAt":9999999999999999}', '{"text":3,"savedAt":0}'])('FR060_removes malformed draft %s without touching unrelated namespace', (value) => {
    sessionStorage.setItem(moderatorDraftKey(identity), value)
    sessionStorage.setItem('unrelated', 'keep')
    cleanModeratorDrafts(identity.userId)
    expect(readModeratorDraft(identity)).toBe('')
    expect(sessionStorage.getItem(moderatorDraftKey(identity))).toBeNull()
    expect(sessionStorage.getItem('unrelated')).toBe('keep')
  })
  it('FR001_deletes other-owner keys without reading their values', () => {
    const other = { ...identity, userId: 99 }
    writeModeratorDraft(other, 'Other-user draft')
    writeModeratorDraft(identity, 'My draft')
    const read = vi.spyOn(Storage.prototype, 'getItem')
    cleanModeratorDrafts(identity.userId)
    expect(read).not.toHaveBeenCalledWith(moderatorDraftKey(other))
    expect(readModeratorDraft(identity)).toBe('My draft')
    read.mockRestore()
    expect(sessionStorage.getItem(moderatorDraftKey(other))).toBeNull()
  })
  it('FR060_isolates resources/screens/fields and clears only the selected draft', () => {
    const variants = [identity, { ...identity, resourceId: 43 }, { ...identity, screenId: 'SCR-063' as const }, { ...identity, field: 'comment-11-reason' }]
    variants.forEach((item, index) => writeModeratorDraft(item, String(index)))
    expect(new Set(variants.map(moderatorDraftKey)).size).toBe(4)
    removeModeratorDraft(identity)
    expect(readModeratorDraft(identity)).toBe('')
    variants.slice(1).forEach((item, index) => expect(readModeratorDraft(item)).toBe(String(index + 1)))
  })
  it.each(['getItem', 'setItem', 'removeItem'] as const)('FR060_storage %s blocked does not throw', (method) => {
    writeModeratorDraft(identity, 'Draft')
    vi.spyOn(Storage.prototype, method).mockImplementation(() => { throw new Error('Storage disabled') })
    expect(() => { cleanModeratorDrafts(identity.userId); writeModeratorDraft(identity, 'New'); readModeratorDraft(identity); removeModeratorDraft(identity) }).not.toThrow()
  })
})
