import { useCallback, useEffect, useRef, useState } from 'react'

import { type ApiError, toApiError } from './errors'

type UseApiMutationResult<TInput, TResult> = {
  run: (input: TInput) => Promise<TResult>
  pending: boolean
  error: ApiError | undefined
  reset: () => void
}

export function useApiMutation<TInput, TResult>(
  fn: (input: TInput) => Promise<TResult>,
): UseApiMutationResult<TInput, TResult> {
  const fnRef = useRef(fn)
  const mountedRef = useRef(true)
  const inFlightRef = useRef<Promise<TResult> | null>(null)
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<ApiError>()

  fnRef.current = fn

  useEffect(() => {
    mountedRef.current = true
    return () => {
      mountedRef.current = false
    }
  }, [])

  const run = useCallback((input: TInput): Promise<TResult> => {
    if (inFlightRef.current) return inFlightRef.current

    setPending(true)
    setError(undefined)

    const request = Promise.resolve()
      .then(() => fnRef.current(input))
      .catch((requestError: unknown) => {
        if (mountedRef.current) setError(toApiError(requestError))
        throw requestError
      })
      .finally(() => {
        inFlightRef.current = null
        if (mountedRef.current) setPending(false)
      })

    inFlightRef.current = request
    return request
  }, [])

  return { run, pending, error, reset: () => setError(undefined) }
}
