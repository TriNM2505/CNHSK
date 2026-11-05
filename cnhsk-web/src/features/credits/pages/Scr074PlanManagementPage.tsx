import { useEffect, useRef, useState } from 'react'

import { invalidResponseError, toApiError, useApiQuery, type ApiError } from '@/shared/api'
import { useCurrentUser } from '@/shared/auth'
import { ErrorState, PageHeader } from '@/shared/ui'

import { listPlans, updatePlan } from '../api/plans'
import { PlanManagementView } from '../components/PlanManagementView'
import type { PlanCode, PlanUpdateRequest, PlanView } from '../types'

function PlanManagementContent() {
  const plans = useApiQuery(async (signal) => {
    const result = await listPlans(signal)
    if (
      result.length !== 3 ||
      ['FREE', 'PREMIUM', 'PREMIUM_PLUS'].some(
        (code) => result.filter((plan) => plan.code === code).length !== 1,
      )
    )
      throw invalidResponseError()
    return result
  }, [])
  const [pending, setPending] = useState<PlanCode>()
  const [saved, setSaved] = useState<PlanView>()
  const [failure, setFailure] = useState<{ code: PlanCode; error: ApiError }>()
  const busy = useRef(false)
  const alive = useRef(true)

  useEffect(() => {
    alive.current = true
    return () => {
      alive.current = false
    }
  }, [])

  const save = async (code: PlanCode, body: PlanUpdateRequest) => {
    if (busy.current || code === 'PREMIUM_PLUS') return
    busy.current = true
    setPending(code)
    setSaved(undefined)
    setFailure(undefined)
    try {
      const result = await updatePlan(code, body)
      if (!alive.current) return
      setSaved(result)
      plans.reload()
    } catch (error) {
      if (alive.current) setFailure({ code, error: toApiError(error) })
    } finally {
      busy.current = false
      if (alive.current) setPending(undefined)
    }
  }

  return (
    <PlanManagementView
      state={plans.state}
      data={plans.data}
      error={plans.error}
      pending={pending}
      saved={saved}
      failure={failure}
      onSave={save}
      onRetry={plans.reload}
    />
  )
}

export default function Scr074PlanManagementPage() {
  const auth = useCurrentUser()
  if (auth.status !== 'authenticated' || !auth.user?.roles.includes('ADMIN'))
    return (
      <div className="space-y-4">
        <PageHeader title="Quản lý gói dịch vụ" />
        {auth.status === 'loading' ? (
          <p role="status">Đang kiểm tra quyền truy cập…</p>
        ) : (
          <ErrorState message="Cần đăng nhập với quyền quản trị để quản lý gói dịch vụ." />
        )}
      </div>
    )
  return <PlanManagementContent key={auth.user.id} />
}
