import { useLayoutEffect, useRef, useState } from 'react'
import { useApiMutation } from '@/shared/api'

export function useSessionAction<T = unknown>() {
  const mounted = useRef(false)
  const inFlight = useRef(false)
  const [busy, setBusy] = useState(false)
  const mutation = useApiMutation((operation: () => Promise<T>) => operation())
  useLayoutEffect(() => {
    mounted.current = true
    return () => { mounted.current = false }
  }, [])
  const run = (operation: () => Promise<T>, onSuccess: (result: T) => void, onError: (error: unknown) => void) => {
    if (inFlight.current || !mounted.current) return
    inFlight.current = true
    setBusy(true)
    void mutation.run(operation).then((result) => {
      if (mounted.current) onSuccess(result)
    }).catch((error: unknown) => {
      if (mounted.current) onError(error)
    }).finally(() => {
      inFlight.current = false
      if (mounted.current) setBusy(false)
    })
  }
  return { run, busy }
}
