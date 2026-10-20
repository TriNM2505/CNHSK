import { useEffect, useRef, useState, type DependencyList } from 'react'

import { type ApiError, toApiError } from './errors'

export type ApiQueryState = 'loading' | 'empty' | 'error' | 'ready'

type UseApiQueryOptions<T> = {
  isEmpty?: (data: T) => boolean
}

type UseApiQueryResult<T> = {
  state: ApiQueryState
  data: T | undefined
  error: ApiError | undefined
  reload: () => void
}

export function useApiQuery<T>(
  fn: (signal: AbortSignal) => Promise<T>,
  deps: DependencyList,
  options: UseApiQueryOptions<T> = {},
): UseApiQueryResult<T> {
  const fnRef = useRef(fn)
  const isEmptyRef = useRef(options.isEmpty)
  const generationRef = useRef(0)
  const [reloadKey, setReloadKey] = useState(0)
  const [result, setResult] = useState<Omit<UseApiQueryResult<T>, 'reload'>>({
    state: 'loading',
    data: undefined,
    error: undefined,
  })

  fnRef.current = fn
  isEmptyRef.current = options.isEmpty

  useEffect(() => {
    const controller = new AbortController()
    const generation = ++generationRef.current
    setResult((current) => ({ ...current, state: 'loading', error: undefined }))

    void fnRef
      .current(controller.signal)
      .then((data) => {
        if (controller.signal.aborted || generation !== generationRef.current) return
        setResult({
          state: isEmptyRef.current?.(data) ? 'empty' : 'ready',
          data,
          error: undefined,
        })
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted || generation !== generationRef.current) return
        setResult({ state: 'error', data: undefined, error: toApiError(error) })
      })

    return () => controller.abort()
    // `deps` là API công khai của hook; thế hệ request ngăn kết quả cũ ghi đè kết quả mới.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [reloadKey, ...deps])

  return {
    ...result,
    reload: () => setReloadKey((key) => key + 1),
  }
}
