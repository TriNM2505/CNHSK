import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { clearPostEditorDraft, postEditorDraftKey, readPostEditorDraft, writePostEditorDraft } from './post-editor-drafts'

describe('community editor tab drafts', () => {
  beforeEach(() => sessionStorage.clear())
  afterEach(() => vi.restoreAllMocks())

  it('stores only writable changed fields and a safe origin under the current user and resource', () => {
    const input = { fields: { title: '', content: '中文\n ', category: 'QUESTION' as const, status: 'APPROVED', submit: true, token: 'untrusted' }, origin: '/community/posts/42?view=discussion#comments', status: 'APPROVED', intent: 'submit' }
    expect(writePostEditorDraft(7, 42, input)).toBe(true)
    expect(postEditorDraftKey(7, 42)).toBe('cnhsk:private-session:7:SCR-041:42')
    expect(JSON.parse(sessionStorage.getItem(postEditorDraftKey(7, 42))!)).toEqual({ fields: { title: '', content: '中文\n ', category: 'QUESTION' }, origin: '/community/posts/42?view=discussion' })
    expect(readPostEditorDraft(7, 42)).toEqual({ fields: { title: '', content: '中文\n ', category: 'QUESTION' }, origin: '/community/posts/42?view=discussion' })
    expect(readPostEditorDraft(8, 42)).toBeUndefined()
    expect(readPostEditorDraft(7, 'new')).toBeUndefined()
    expect(localStorage.getItem(postEditorDraftKey(7, 42))).toBeNull()
  })

  it('ignores malformed storage and strips unrecognized fields and unsafe origins', () => {
    const key = postEditorDraftKey(7, 'new')
    sessionStorage.setItem(key, 'not-json')
    expect(readPostEditorDraft(7, 'new')).toBeUndefined()
    sessionStorage.setItem(key, JSON.stringify({ fields: { title: 2, category: 'INVALID', content: '', userId: 8 }, origin: 'https://example.invalid/community', response: { title: 'stale' }, expiresAt: 0 }))
    expect(readPostEditorDraft(7, 'new')).toEqual({ fields: { content: '' } })
  })

  it('clears only the matching draft without expiring another resource', () => {
    writePostEditorDraft(7, 'new', { fields: { title: 'new' } })
    writePostEditorDraft(7, 42, { fields: { title: 'edit' } })
    writePostEditorDraft(8, 'new', { fields: { title: 'other user' } })
    clearPostEditorDraft(7, 'new')
    expect(readPostEditorDraft(7, 'new')).toBeUndefined()
    expect(readPostEditorDraft(7, 42)?.fields.title).toBe('edit')
    expect(readPostEditorDraft(8, 'new')?.fields.title).toBe('other user')
  })

  it('reports blocked writes and safely handles inaccessible storage', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new DOMException('Quota exceeded', 'QuotaExceededError') })
    expect(writePostEditorDraft(7, 'new', { fields: { title: 'keep input' } })).toBe(false)
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new DOMException('Blocked', 'SecurityError') })
    expect(readPostEditorDraft(7, 'new')).toBeUndefined()
    vi.spyOn(Storage.prototype, 'removeItem').mockImplementation(() => { throw new DOMException('Blocked', 'SecurityError') })
    expect(() => clearPostEditorDraft(7, 'new')).not.toThrow()
  })
})
