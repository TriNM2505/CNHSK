import { act, renderHook, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { ApiError } from './errors'
import { useApiMutation } from './useApiMutation'

describe('useApiMutation', () => {
  it('chặn gửi lặp khi request đang pending', async () => {
    let finish!: (value: string) => void
    const mutation = vi.fn(
      () =>
        new Promise<string>((resolve) => {
          finish = resolve
        }),
    )
    const { result } = renderHook(() => useApiMutation(mutation))

    let first!: Promise<string>
    let second!: Promise<string>
    await act(async () => {
      first = result.current.run('first')
      second = result.current.run('second')
      await Promise.resolve()
    })

    expect(first).toBe(second)
    expect(mutation).toHaveBeenCalledTimes(1)
    expect(result.current.pending).toBe(true)

    await act(async () => finish('done'))
    await expect(first).resolves.toBe('done')
    await waitFor(() => expect(result.current.pending).toBe(false))
  })

  it('giữ lỗi API và cho phép xoá lỗi', async () => {
    const apiError = new ApiError({
      status: 422,
      errorCode: 'VALIDATION_ERROR',
      message: 'Dữ liệu chưa hợp lệ.',
    })
    const { result } = renderHook(() => useApiMutation(() => Promise.reject(apiError)))

    await act(async () => {
      await expect(result.current.run(undefined)).rejects.toBe(apiError)
    })
    expect(result.current.error).toBe(apiError)

    act(() => result.current.reset())
    expect(result.current.error).toBeUndefined()
  })
})
