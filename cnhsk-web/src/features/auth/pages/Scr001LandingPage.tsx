import { useEffect } from 'react'
import { useLocation } from 'react-router-dom'

import { LandingView, type LandingViewState } from '../components/LandingView'
import { useCurrentUser } from '@/shared/auth'

export default function Scr001LandingPage() {
  const { reloadCurrentUser, status, user } = useCurrentUser()
  const { hash, key } = useLocation()
  const state: LandingViewState =
    status === 'loading' ? 'loading' : status === 'error' ? 'error' : 'ready'

  useEffect(() => {
    // Menu links point at sections of this page; the router does not scroll to anchors by itself.
    if (hash) document.getElementById(hash.slice(1))?.scrollIntoView?.()
  }, [hash, key])

  return (
    <LandingView
      displayName={status === 'authenticated' ? user?.displayName : undefined}
      onRetry={() => void reloadCurrentUser()}
      state={state}
    />
  )
}
