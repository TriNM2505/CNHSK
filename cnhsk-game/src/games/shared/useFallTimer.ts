import { useEffect, useRef, useState } from 'react'

import { fallDurationMs } from '../../engine/arcade'

export function itemFallDuration(index: number, config: Record<string, unknown>) {
  return fallDurationMs(index, {
    fallStartMs: typeof config.fallStartMs === 'number' ? config.fallStartMs : 6000,
    fallStepMs: typeof config.fallStepMs === 'number' ? config.fallStepMs : 150,
    fallMinMs: typeof config.fallMinMs === 'number' ? config.fallMinMs : 3000,
  })
}

export function useFallTimer(duration: number, running: boolean, onExpire: () => void) {
  // Each target is keyed by item number, so only a new item resets the clock.
  const left = useRef(duration)
  const deadline = useRef<number>()
  const expire = useRef(onExpire)
  const [remaining, setRemaining] = useState(duration)

  useEffect(() => { expire.current = onExpire }, [onExpire])
  useEffect(() => {
    if (!running) {
      setRemaining(left.current)
      return
    }
    if (left.current === 0) return
    const end = performance.now() + left.current
    deadline.current = end
    let frame: number
    function draw(now: number) {
      setRemaining(Math.max(0, end - now))
      if (now < end) frame = window.requestAnimationFrame(draw)
    }
    frame = window.requestAnimationFrame(draw)
    const timeout = window.setTimeout(() => {
      left.current = 0
      setRemaining(0)
      window.cancelAnimationFrame(frame)
      expire.current()
    }, left.current)
    return () => {
      window.clearTimeout(timeout)
      window.cancelAnimationFrame(frame)
      deadline.current = undefined
      left.current = Math.max(0, end - performance.now())
    }
  }, [running])

  return {
    remaining,
    expired: () => left.current === 0 || (deadline.current !== undefined && performance.now() >= deadline.current),
  }
}
