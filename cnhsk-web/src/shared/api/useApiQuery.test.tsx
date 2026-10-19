import { act, renderHook, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { useApiQuery } from './useApiQuery'

type Deferred<T> = {
  promise: Promise<T>
  resolve: (value: T) => void
}

function deferred<T>(): Deferred<T> {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((done) => {
    resolve = done
  })
  return { promise, resolve }
}

describe('useApiQuery', () => {
  it('huỷ request cũ và không cho kết quả cũ ghi đè kết quả mới', async () => {
    const first = deferred<string>()
    const second = deferred<string>()
    const signals: AbortSignal[] = []
    const query = vi.fn((id: number, signal: AbortSignal) => {
      signals.push(signal)
      return id === 1 ? first.promise : second.promise
    })
    const { result, rerender } = renderHook(
      ({ id }) => useApiQuery((signal) => query(id, signal), [id]),
      { initialProps: { id: 1 } },
    )

    rerender({ id: 2 })
    expect(signals[0]?.aborted).toBe(true)

    await act(async () => second.resolve('mới'))
    await waitFor(() => expect(result.current.data).toBe('mới'))
    expect(result.current.state).toBe('ready')

    await act(async () => first.resolve('cũ'))
    expect(result.current.data).toBe('mới')
  })

  it('hỗ trợ trạng thái rỗng và tải lại', async () => {
    const query = vi.fn<() => Promise<string[]>>().mockResolvedValue([])
    const { result } = renderHook(() =>
      useApiQuery(() => query(), [], { isEmpty: (data) => !data.length }),
    )

    await waitFor(() => expect(result.current.state).toBe('empty'))
    act(() => result.current.reload())
    await waitFor(() => expect(query).toHaveBeenCalledTimes(2))
  })
})
