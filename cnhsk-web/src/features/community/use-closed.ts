import { useEffect, useState } from 'react'

/** True once the moment has passed; re-checks every 20 seconds so a page left open closes on time. */
export function useClosed(closeAt: string | null | undefined): boolean {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    if (!closeAt) return
    const timer = window.setInterval(() => setNow(Date.now()), 20_000)
    return () => window.clearInterval(timer)
  }, [closeAt])
  return closeAt ? new Date(closeAt).getTime() <= now : false
}
