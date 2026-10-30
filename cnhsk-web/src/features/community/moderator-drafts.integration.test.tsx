import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { useState } from 'react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { CurrentUser } from '@/features/auth/types'
import { AuthContext, RequireRole, type AuthContextValue } from '@/shared/auth'
import { ApiError } from '@/shared/api'
import { ModeratorSession } from './components/ModeratorSession'
import { useModeratorAction } from './use-moderator-action'
import { useModeratorDraft } from './use-moderator-draft'
import { moderatorDraftKey, writeModeratorDraft } from './moderator-drafts'

const user: CurrentUser = { id: 100, roles: ['MODERATOR'], email: 'moderator@test.invalid', emailVerified: true, displayName: 'Moderator test' }
const identity = { userId: 100, screenId: 'SCR-061' as const, resourceId: 42, field: 'rejectionReason' }
function context(actor: CurrentUser | null = user, status: AuthContextValue['status'] = actor ? 'authenticated' : 'guest'): AuthContextValue {
  return { user: actor, status, error: status === 'error' ? new ApiError({ status: 503, errorCode: 'AUTH_ERROR', message: 'Auth failed', requestId: 'test' }) : null, reloadCurrentUser: async () => undefined, setAuthenticatedUser: () => undefined, signOut: async () => undefined }
}
function deferred<T>() { let resolve!: (value: T) => void; let reject!: (error: unknown) => void; const promise = new Promise<T>((done, fail) => { resolve = done; reject = fail }); return { promise, resolve, reject } }

// The actual resource pages use the same gated owner, hook and shared mutation lane.
function DraftOwner({ id, userId, operation, onSuccess }: { id: number; userId: number; operation: () => Promise<void>; onSuccess: () => void }) {
  const draft = useModeratorDraft(userId, 'SCR-061', id, 'rejectionReason')
  const action = useModeratorAction()
  const [confirming, setConfirming] = useState(false)
  const [error, setError] = useState(false)
  return <section>
    <label htmlFor="reason">Lý do</label><textarea id="reason" value={draft.text} onChange={(event) => draft.setText(event.target.value)} />
    <button type="button" onClick={() => setConfirming(true)}>Tiếp tục</button>
    {confirming ? <button type="button" disabled={action.busy} onClick={() => action.run(operation, () => { draft.clear(); setConfirming(false); onSuccess() }, () => setError(true))}>Xác nhận</button> : null}
    {error ? <p role="alert">Lỗi gửi</p> : null}
    <button type="button" disabled={action.busy} onClick={() => { draft.clear(); setConfirming(false) }}>Hủy</button>
  </section>
}
function GuardedDraft({ id, operation, onSuccess }: { id: number; operation: () => Promise<void>; onSuccess: () => void }) {
  return <RequireRole roles={['MODERATOR']}><ModeratorSession sessionKey={`draft:${id}`}>{(userId) => <DraftOwner id={id} userId={userId} operation={operation} onSuccess={onSuccess} />}</ModeratorSession></RequireRole>
}
function shell(auth = context(), id = 42, operation = vi.fn<() => Promise<void>>().mockResolvedValue(undefined), onSuccess = vi.fn()) {
  return <AuthContext.Provider value={auth}><MemoryRouter><Routes><Route path="/" element={<GuardedDraft id={id} operation={operation} onSuccess={onSuccess} />} /><Route path="/login" element={<h1>Login</h1>} /><Route path="/403" element={<h1>Forbidden</h1>} /></Routes></MemoryRouter></AuthContext.Provider>
}
beforeEach(() => { sessionStorage.clear() })
afterEach(() => { vi.restoreAllMocks(); sessionStorage.clear() })

describe('moderator draft lifetime under the real role guard', () => {
  it('FR060_same-user refresh restores only text, no confirmation or mutation', () => {
    const operation = vi.fn<() => Promise<void>>().mockResolvedValue(undefined)
    const view = render(shell(context(), 42, operation))
    fireEvent.change(screen.getByRole('textbox', { name: 'Lý do' }), { target: { value: 'Typed by moderator' } })
    fireEvent.click(screen.getByRole('button', { name: 'Tiếp tục' }))
    expect(screen.getByRole('button', { name: 'Xác nhận' })).toBeVisible()
    view.unmount()
    render(shell(context(), 42, operation))
    expect(screen.getByRole('textbox', { name: 'Lý do' })).toHaveValue('Typed by moderator')
    expect(screen.queryByRole('button', { name: 'Xác nhận' })).not.toBeInTheDocument()
    expect(operation).not.toHaveBeenCalled()
  })
  it('FR001_other user never sees old draft and lazy mount deletes only its namespace', () => {
    const view = render(shell())
    fireEvent.change(screen.getByRole('textbox'), { target: { value: 'User A text' } })
    sessionStorage.setItem('unrelated', 'preserve')
    view.rerender(shell(context({ ...user, id: 101 })))
    expect(screen.getByRole('textbox')).toHaveValue('')
    expect(sessionStorage.getItem(moderatorDraftKey(identity))).toBeNull()
    expect(sessionStorage.getItem('unrelated')).toBe('preserve')
  })
  it.each(['loading', 'error'] as const)('FR001_auth %s does not read storage or mount the draft owner', (status) => {
    writeModeratorDraft(identity, 'Private draft')
    const read = vi.spyOn(Storage.prototype, 'getItem')
    render(shell(context(user, status)))
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
    expect(read).not.toHaveBeenCalled()
  })
  it.each([null, { ...user, roles: ['LEARNER'] as const }, { ...user, roles: ['ADMIN'] as const }])('FR001_unauthorized actor cannot hydrate or render a draft', (actor) => {
    writeModeratorDraft(identity, 'Private draft')
    const read = vi.spyOn(Storage.prototype, 'getItem')
    const normalized = actor ? { ...actor, roles: [...actor.roles] } : null
    render(shell(context(normalized)))
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
    expect(read).not.toHaveBeenCalled()
  })
  it.each(['guest', 'downgrade'] as const)('FR001_%s removes privileged DOM immediately; physical draft retention is allowed', (mode) => {
    const view = render(shell())
    fireEvent.change(screen.getByRole('textbox'), { target: { value: 'Retained within TTL' } })
    view.rerender(shell(mode === 'guest' ? context(null) : context({ ...user, roles: ['LEARNER'] })))
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
    expect(sessionStorage.getItem(moderatorDraftKey(identity))).toContain('Retained within TTL')
    view.unmount()
    render(shell())
    expect(screen.getByRole('textbox')).toHaveValue('Retained within TTL')
  })
  it('FR060_resource changes isolate text and cancel clears only the selected resource', () => {
    const view = render(shell())
    fireEvent.change(screen.getByRole('textbox'), { target: { value: 'Resource A' } })
    view.rerender(shell(context(), 43))
    expect(screen.getByRole('textbox')).toHaveValue('')
    fireEvent.change(screen.getByRole('textbox'), { target: { value: 'Resource B' } })
    fireEvent.click(screen.getByRole('button', { name: 'Hủy' }))
    expect(screen.getByRole('textbox')).toHaveValue('')
    expect(sessionStorage.getItem(moderatorDraftKey({ ...identity, resourceId: 43 }))).toBeNull()
    expect(sessionStorage.getItem(moderatorDraftKey(identity))).toContain('Resource A')
    view.unmount(); render(shell(context(), 43))
    expect(screen.getByRole('textbox')).toHaveValue('')
  })
  it.each([7_199_000, 7_200_000, 7_200_001])('FR060_mount enforces two-hour TTL at age %s before displaying a draft', (age) => {
    const now = Date.now()
    vi.spyOn(Date, 'now').mockReturnValue(now)
    const view = render(shell())
    fireEvent.change(screen.getByRole('textbox'), { target: { value: 'TTL text' } })
    view.unmount()
    vi.mocked(Date.now).mockReturnValue(now + age)
    render(shell())
    expect(screen.getByRole('textbox')).toHaveValue(age < 7_200_000 ? 'TTL text' : '')
    expect(sessionStorage.getItem(moderatorDraftKey(identity)) !== null).toBe(age < 7_200_000)
  })
  it('FR060_success clears draft before callback; failure preserves it', async () => {
    const operation = vi.fn<() => Promise<void>>().mockRejectedValueOnce(new Error('Offline')).mockResolvedValueOnce(undefined)
    const onSuccess = vi.fn(() => expect(sessionStorage.getItem(moderatorDraftKey(identity))).toBeNull())
    render(shell(context(), 42, operation, onSuccess))
    fireEvent.change(screen.getByRole('textbox'), { target: { value: 'Do not lose' } })
    fireEvent.click(screen.getByRole('button', { name: 'Tiếp tục' })); fireEvent.click(screen.getByRole('button', { name: 'Xác nhận' }))
    await screen.findByRole('alert')
    expect(screen.getByRole('textbox')).toHaveValue('Do not lose')
    expect(sessionStorage.getItem(moderatorDraftKey(identity))).toContain('Do not lose')
    await waitFor(() => expect(screen.getByRole('button', { name: 'Xác nhận' })).toBeEnabled())
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận' }))
    await waitFor(() => expect(onSuccess).toHaveBeenCalledTimes(1))
  })
  it('FR001_late old-session mutation cannot clear a new-user draft or fire a callback', async () => {
    const pending = deferred<void>()
    const operation = vi.fn(() => pending.promise)
    const onSuccess = vi.fn()
    const view = render(shell(context(), 42, operation, onSuccess))
    fireEvent.change(screen.getByRole('textbox'), { target: { value: 'A' } })
    fireEvent.click(screen.getByRole('button', { name: 'Tiếp tục' })); fireEvent.click(screen.getByRole('button', { name: 'Xác nhận' }))
    await waitFor(() => expect(operation).toHaveBeenCalledTimes(1))
    view.rerender(shell(context({ ...user, id: 101 }), 42, operation, onSuccess))
    fireEvent.change(screen.getByRole('textbox'), { target: { value: 'B' } })
    await act(async () => pending.resolve())
    expect(screen.getByRole('textbox')).toHaveValue('B')
    expect(sessionStorage.getItem(moderatorDraftKey({ ...identity, userId: 101 }))).toContain('B')
    expect(onSuccess).not.toHaveBeenCalled()
  })
  it.each(['getItem', 'setItem', 'removeItem'] as const)('FR060_blocked %s keeps form usable and never submits automatically', async (method) => {
    const operation = vi.fn<() => Promise<void>>().mockResolvedValue(undefined)
    vi.spyOn(Storage.prototype, method).mockImplementation(() => { throw new Error('Blocked') })
    render(shell(context(), 42, operation))
    fireEvent.change(screen.getByRole('textbox'), { target: { value: 'Memory-only text' } })
    expect(screen.getByRole('textbox')).toHaveValue('Memory-only text')
    expect(operation).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: 'Hủy' }))
    expect(screen.getByRole('textbox')).toHaveValue('')
  })
})
