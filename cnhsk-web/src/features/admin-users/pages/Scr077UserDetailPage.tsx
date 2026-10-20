import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'

import type { RoleCode } from '@/features/auth/types'
import { useCurrentUser } from '@/shared/auth'
import { toApiError } from '@/shared/api'

import {
  banAdminUser,
  getAdminUserDetail,
  grantAdminUserRole,
  revokeAdminUserRole,
  unbanAdminUser,
} from '../api/adminUsers'
import {
  UserDetailView,
  type UserDetailNotice,
  type UserDetailViewState,
} from '../components/UserDetailView'
import type { AdminUserDetail } from '../types'
import {
  getActionSuccessMessage,
  mapUserDetailActionError,
  type UserDetailAction,
  type UserDetailActionError,
  validateBanReason,
} from '../userDetailActions'

function parseUserId(value: string | undefined) {
  if (!value || !/^\d+$/.test(value)) return undefined
  const id = Number(value)
  return Number.isSafeInteger(id) && id > 0 ? id : undefined
}

export default function Scr077UserDetailPage() {
  const { id: idParam } = useParams()
  const id = parseUserId(idParam)
  const { setAuthenticatedUser, user: currentUser } = useCurrentUser()
  const [state, setState] = useState<UserDetailViewState>(id ? 'loading' : 'empty')
  const [detail, setDetail] = useState<AdminUserDetail>()
  const [loadError, setLoadError] = useState<{ message: string; requestId?: string }>()
  const [reloadKey, setReloadKey] = useState(0)
  const [refreshing, setRefreshing] = useState(false)
  const [action, setAction] = useState<UserDetailAction>()
  const [actionError, setActionError] = useState<UserDetailActionError>()
  const [pending, setPending] = useState(false)
  const [notice, setNotice] = useState<UserDetailNotice>()
  const [selectedRole, setSelectedRole] = useState<RoleCode | ''>('')
  const [banReason, setBanReason] = useState('')
  const [banReasonError, setBanReasonError] = useState<string>()

  useEffect(() => {
    if (!id) {
      setState('empty')
      return
    }

    const controller = new AbortController()
    setState('loading')
    setLoadError(undefined)

    getAdminUserDetail(id, controller.signal)
      .then((data) => {
        setDetail(data)
        setState('ready')
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) return
        const apiError = toApiError(error)
        if (apiError.status === 404 || apiError.errorCode === 'USER_NOT_FOUND') {
          setState('empty')
          return
        }
        setLoadError({ message: apiError.message, requestId: apiError.requestId })
        setState('error')
      })

    return () => controller.abort()
  }, [id, reloadKey])

  const refreshDetail = async () => {
    if (!id) return undefined
    setRefreshing(true)
    try {
      const data = await getAdminUserDetail(id)
      setDetail(data)
      setState('ready')
      return data
    } catch (error) {
      const apiError = toApiError(error)
      if (apiError.status === 404 || apiError.errorCode === 'USER_NOT_FOUND') {
        setAction(undefined)
        setState('empty')
      } else {
        setNotice({
          kind: 'error',
          message: 'Không tải lại được thông tin mới nhất. Vui lòng thử lại.',
          requestId: apiError.requestId,
        })
      }
      return undefined
    } finally {
      setRefreshing(false)
    }
  }

  const requestAction = (nextAction: UserDetailAction) => {
    setActionError(undefined)
    setBanReasonError(undefined)
    if (nextAction.kind === 'ban') setBanReason('')
    setAction(nextAction)
  }

  const confirmAction = async () => {
    if (!id || !action || pending) return

    let normalizedReason = ''
    if (action.kind === 'ban') {
      const validation = validateBanReason(banReason)
      if (validation.error) {
        setBanReasonError(validation.error)
        return
      }
      normalizedReason = validation.value
    }

    setPending(true)
    setActionError(undefined)
    setBanReasonError(undefined)

    try {
      switch (action.kind) {
        case 'grant':
          await grantAdminUserRole(id, { roleCode: action.role })
          break
        case 'revoke':
          await revokeAdminUserRole(id, action.role)
          break
        case 'ban':
          await banAdminUser(id, { reason: normalizedReason })
          break
        case 'unban':
          await unbanAdminUser(id)
          break
      }

      const completedAction = action
      setAction(undefined)
      setNotice({ kind: 'success', message: getActionSuccessMessage(completedAction) })
      setSelectedRole('')
      setBanReason('')

      const updated = await refreshDetail()
      if (
        updated &&
        currentUser &&
        currentUser.id === id &&
        completedAction.kind === 'revoke' &&
        completedAction.role !== 'ADMIN'
      ) {
        setAuthenticatedUser({
          ...currentUser,
          roles: updated.roles.map((role) => role.code),
        })
      }
    } catch (error) {
      const mapped = mapUserDetailActionError(error)
      if (mapped.userMissing) {
        setAction(undefined)
        setState('empty')
      } else {
        setActionError(mapped.fieldError ? undefined : mapped)
        setBanReasonError(mapped.fieldError)
        if (mapped.reloadDetail) await refreshDetail()
      }
    } finally {
      setPending(false)
    }
  }

  return (
    <UserDetailView
      action={action}
      actionError={actionError}
      banReason={banReason}
      banReasonError={banReasonError}
      data={detail}
      error={loadError}
      isSelf={Boolean(id && currentUser?.id === id)}
      notice={notice}
      onBanReasonChange={(value) => {
        setBanReason(value)
        setBanReasonError(undefined)
        setActionError(undefined)
      }}
      onCancelAction={() => {
        if (pending) return
        setAction(undefined)
        setActionError(undefined)
        setBanReasonError(undefined)
      }}
      onConfirmAction={confirmAction}
      onRequestAction={requestAction}
      onRetry={() => setReloadKey((value) => value + 1)}
      onRoleChange={setSelectedRole}
      pending={pending}
      refreshing={refreshing}
      selectedRole={selectedRole}
      state={state}
    />
  )
}
