import { act, cleanup, renderHook } from '@testing-library/react'
import { StrictMode } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/shared/api'
import * as api from './api/contests'
import { contestSessionFixture } from './test-fixtures'
import { useContestAttempt } from './useContestAttempt'

vi.mock('./api/contests', () => ({
  enterContest: vi.fn(),
  getContestDraft: vi.fn(),
  saveContestAnswer: vi.fn(),
  submitContest: vi.fn(),
  reportContestTabHidden: vi.fn(),
}))

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (error: unknown) => void
  const promise = new Promise<T>((yes, no) => {
    resolve = yes
    reject = no
  })
  return { promise, resolve, reject }
}
async function mount() {
  const hook = renderHook(() => useContestAttempt(2101), { wrapper: StrictMode })
  await act(async () => {})
  return hook
}
const unavailable = new ApiError({ status: 0, errorCode: 'NETWORK_ERROR', message: 'Mất kết nối.' })

describe('FR016_AT20 contest attempt lifecycle', () => {
  beforeEach(() => {
    vi.useFakeTimers({
      toFake: ['Date', 'performance', 'setTimeout', 'clearTimeout', 'setInterval', 'clearInterval'],
    })
    vi.resetAllMocks()
    vi.mocked(api.enterContest).mockResolvedValue(structuredClone(contestSessionFixture))
    vi.mocked(api.getContestDraft).mockResolvedValue({
      sessionId: 2151,
      status: 'IN_PROGRESS',
      answered: [],
    })
    vi.mocked(api.saveContestAnswer).mockResolvedValue({ no: 1, accepted: true })
    vi.mocked(api.submitContest).mockResolvedValue({ submittedAt: '2026-11-14T12:20:00Z' })
    vi.mocked(api.reportContestTabHidden).mockResolvedValue(undefined)
  })
  afterEach(() => {
    cleanup()
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  it('resumes one entry request under StrictMode and restores server answers and deadline', async () => {
    vi.mocked(api.enterContest).mockResolvedValue({ ...contestSessionFixture, resumed: true })
    vi.mocked(api.getContestDraft).mockResolvedValue({
      sessionId: 2151,
      status: 'IN_PROGRESS',
      answered: [
        { no: 2, answer: 'B' },
        { no: 99, answer: 'A' },
      ],
    })
    const { result } = await mount()
    expect(api.enterContest).toHaveBeenCalledOnce()
    expect(api.getContestDraft).toHaveBeenCalledWith(2151)
    expect(result.current.session?.deadline).toBe(contestSessionFixture.deadline)
    expect(result.current.choices).toEqual({ 2: 'B' })
    expect(result.current.remainingMs).toBe(1_200_000)
    vi.setSystemTime(new Date('2030-01-01T00:00:00Z'))
    act(() => vi.advanceTimersByTime(1000))
    expect(result.current.remainingMs).toBe(1_199_000)
    expect(api.submitContest).not.toHaveBeenCalled()
  })

  it('freezes at expiry, drains pending saves and coalesces timer with button submission', async () => {
    vi.mocked(api.enterContest).mockResolvedValue({
      ...contestSessionFixture,
      deadline: '2026-11-14T12:00:01Z',
    })
    const save = deferred<{ no: number; accepted: boolean }>()
    vi.mocked(api.saveContestAnswer).mockReturnValue(save.promise)
    const { result } = await mount()
    let saving!: Promise<boolean>
    act(() => {
      saving = result.current.choose(1, 'A')
    })
    await act(async () => {})
    expect(api.saveContestAnswer).toHaveBeenCalledOnce()
    act(() => vi.advanceTimersByTime(1000))
    expect(result.current.locked).toBe(true)
    expect(api.submitContest).not.toHaveBeenCalled()
    act(() => {
      void result.current.submit()
    })
    await act(async () => {
      save.resolve({ no: 1, accepted: true })
      await saving
    })
    expect(api.submitContest).toHaveBeenCalledOnce()
    expect(result.current.submitted).toBe(true)
    act(() => vi.advanceTimersByTime(30_000))
    await act(async () => {
      await result.current.submit()
    })
    expect(api.submitContest).toHaveBeenCalledOnce()
  })

  it('shares the same pending request for manual double-click and expiry', async () => {
    vi.mocked(api.enterContest).mockResolvedValue({
      ...contestSessionFixture,
      deadline: '2026-11-14T12:00:01Z',
    })
    const submit = deferred<{ submittedAt: string }>()
    vi.mocked(api.submitContest).mockReturnValue(submit.promise)
    const { result } = await mount()
    let first!: Promise<boolean>
    let second!: Promise<boolean>
    act(() => {
      first = result.current.submit()
      second = result.current.submit()
    })
    expect(first).toBe(second)
    await act(async () => {})
    act(() => vi.advanceTimersByTime(1000))
    expect(api.submitContest).toHaveBeenCalledOnce()
    await act(async () => {
      submit.resolve({ submittedAt: '2026-11-14T12:00:01Z' })
      await first
    })
    expect(result.current.submitted).toBe(true)
  })

  it('shows an unsubmitted failure without automatic retries and allows explicit retry', async () => {
    vi.mocked(api.enterContest).mockResolvedValue({
      ...contestSessionFixture,
      deadline: '2026-11-14T12:00:01Z',
    })
    vi.mocked(api.submitContest).mockRejectedValueOnce(unavailable)
    const { result } = await mount()
    await act(async () => {
      vi.advanceTimersByTime(1000)
    })
    expect(result.current.submitted).toBe(false)
    expect(result.current.submitError?.message).toBe('Mất kết nối.')
    expect(result.current.locked).toBe(true)
    await act(async () => {
      vi.advanceTimersByTime(15_000)
    })
    expect(api.submitContest).toHaveBeenCalledOnce()
    await act(async () => {
      await result.current.retrySubmit()
    })
    expect(api.submitContest).toHaveBeenCalledTimes(2)
    expect(result.current.submitted).toBe(true)
  })

  it('serializes edits so an older answer cannot overwrite the latest choice', async () => {
    const save = deferred<{ no: number; accepted: boolean }>()
    vi.mocked(api.saveContestAnswer).mockReturnValueOnce(save.promise)
    const { result } = await mount()
    let first!: Promise<boolean>
    let second!: Promise<boolean>
    act(() => {
      first = result.current.choose(1, 'A')
      second = result.current.choose(1, 'B')
    })
    await act(async () => {})
    expect(api.saveContestAnswer).toHaveBeenCalledTimes(1)
    expect(result.current.choices[1]).toBe('B')
    await act(async () => {
      save.resolve({ no: 1, accepted: true })
      await first
      await second
    })
    expect(vi.mocked(api.saveContestAnswer).mock.calls.map(([, answer]) => answer.answer)).toEqual([
      'A',
      'B',
    ])
    expect(result.current.saveStates[1]).toBe('saved')
    expect(vi.mocked(api.saveContestAnswer).mock.calls[0]![1]).not.toHaveProperty('score')
  })

  it('retains a failed local choice and still submits the server-saved answers', async () => {
    vi.mocked(api.saveContestAnswer).mockRejectedValueOnce(unavailable)
    const { result } = await mount()
    await act(async () => {
      await result.current.choose(1, 'A')
    })
    expect(result.current.choices[1]).toBe('A')
    expect(result.current.saveStates[1]).toBe('failed')
    await act(async () => {
      await result.current.submit()
    })
    expect(result.current.submitted).toBe(true)
    expect(api.submitContest).toHaveBeenCalledWith(2101)
  })

  it('records hidden-tab events at most once per ten seconds and stops after submission', async () => {
    const { result } = await mount()
    vi.spyOn(document, 'hidden', 'get').mockReturnValue(true)
    act(() => {
      document.dispatchEvent(new Event('visibilitychange'))
      document.dispatchEvent(new Event('visibilitychange'))
    })
    expect(api.reportContestTabHidden).toHaveBeenCalledOnce()
    act(() => vi.advanceTimersByTime(10_000))
    act(() => document.dispatchEvent(new Event('visibilitychange')))
    expect(api.reportContestTabHidden).toHaveBeenCalledTimes(2)
    await act(async () => {
      await result.current.submit()
    })
    act(() => document.dispatchEvent(new Event('visibilitychange')))
    expect(api.reportContestTabHidden).toHaveBeenCalledTimes(2)
    cleanup()
    act(() => document.dispatchEvent(new Event('visibilitychange')))
    expect(api.reportContestTabHidden).toHaveBeenCalledTimes(2)
  })

  it('rejects unknown questions/options and prevents new writes once submission starts', async () => {
    const { result } = await mount()
    await act(async () => {
      await result.current.choose(99, 'A')
      await result.current.choose(1, 'Z')
    })
    expect(api.saveContestAnswer).not.toHaveBeenCalled()
    let submitted!: Promise<boolean>
    act(() => {
      submitted = result.current.submit()
    })
    await act(async () => {
      await result.current.choose(1, 'A')
      await submitted
    })
    expect(api.saveContestAnswer).not.toHaveBeenCalled()
  })

  it('retries entry only on explicit reload and never invents a submitted result on error', async () => {
    vi.mocked(api.enterContest).mockRejectedValueOnce(unavailable)
    const { result } = await mount()
    expect(result.current.load).toBe('error')
    expect(result.current.submitted).toBe(false)
    act(() => result.current.reload())
    await act(async () => {})
    expect(result.current.load).toBe('ready')
    expect(api.enterContest).toHaveBeenCalledTimes(2)
  })

  it('does not start a countdown or submit when server timestamps are invalid', async () => {
    vi.mocked(api.enterContest).mockResolvedValue({ ...contestSessionFixture, deadline: 'invalid' })
    const { result } = await mount()
    expect(result.current.load).toBe('error')
    expect(result.current.loadError?.errorCode).toBe('INVALID_RESPONSE')
    act(() => vi.advanceTimersByTime(30_000))
    expect(api.submitContest).not.toHaveBeenCalled()
  })
})
