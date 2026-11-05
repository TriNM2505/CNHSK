import { useEffect, useRef, useState } from 'react'

import { toApiError, useApiQuery, type ApiError } from '@/shared/api'
import { useCurrentUser } from '@/shared/auth'
import { ErrorState, PageHeader } from '@/shared/ui'

import { getMyWallet, redeemCard } from '../api/wallet'
import { PlanCreditsView } from '../components/PlanCreditsView'
import type { RedeemRequest, RedeemResult } from '../types'

function PlanCreditsContent() {
  const wallet = useApiQuery((signal) => getMyWallet(signal), [])
  const [pending, setPending] = useState(false)
  const [redeemError, setRedeemError] = useState<ApiError>()
  const [result, setResult] = useState<RedeemResult>()
  const alive = useRef(true)
  const busy = useRef(false)

  useEffect(() => {
    alive.current = true
    return () => {
      alive.current = false
    }
  }, [])

  const redeem = async (body: RedeemRequest) => {
    if (busy.current) return
    busy.current = true
    setPending(true)
    setRedeemError(undefined)
    try {
      const redeemed = await redeemCard(body)
      if (!alive.current) return
      setResult(redeemed)
      wallet.reload()
    } catch (error) {
      if (alive.current) setRedeemError(toApiError(error))
    } finally {
      busy.current = false
      if (alive.current) setPending(false)
    }
  }

  return (
    <PlanCreditsView
      state={wallet.state}
      wallet={wallet.data}
      error={wallet.error}
      pending={pending}
      redeemError={redeemError}
      result={result}
      onRetry={wallet.reload}
      onRedeem={redeem}
    />
  )
}

export default function Scr030PlanCreditsPage() {
  const auth = useCurrentUser()
  if (auth.status !== 'authenticated' || !auth.user?.roles.includes('LEARNER')) {
    return (
      <div className="space-y-4">
        <PageHeader title="Gói dịch vụ và điểm" />
        {auth.status === 'loading' ? (
          <p role="status">Đang kiểm tra quyền truy cập…</p>
        ) : (
          <ErrorState message="Cần đăng nhập với quyền học viên để xem ví điểm." />
        )}
      </div>
    )
  }
  return <PlanCreditsContent key={auth.user.id} />
}
