import { useEffect, useState } from 'react'

import { useApiMutation, useApiQuery } from '@/shared/api'
import { useCurrentUser } from '@/shared/auth'

import {
  formToProfilePatch,
  getProfileFailure,
  type ProfileFieldErrors,
  type ProfileFormValues,
  profileToForm,
  validateProfile,
} from '../accountForms'
import { getProfile, updateProfile } from '../api/account'
import type { FormNoticeValue } from '../components/FormNotice'
import { ProfileView, type ProfileViewState } from '../components/ProfileView'

const EMPTY_VALUES: ProfileFormValues = { avatarUrl: '', displayName: '', hskLevel: '' }

export default function Scr007ProfilePage() {
  const { setAuthenticatedUser, user } = useCurrentUser()
  const query = useApiQuery(getProfile, [])
  const mutation = useApiMutation(updateProfile)
  const [values, setValues] = useState<ProfileFormValues>(EMPTY_VALUES)
  const [fieldErrors, setFieldErrors] = useState<ProfileFieldErrors>({})
  const [notice, setNotice] = useState<FormNoticeValue>()

  useEffect(() => {
    if (query.data) setValues(profileToForm(query.data))
  }, [query.data])

  const state: ProfileViewState =
    query.state === 'loading' || query.state === 'error'
      ? query.state
      : mutation.pending
        ? 'saving'
        : 'ready'

  const updateField = (field: keyof ProfileFormValues, value: string) => {
    setValues((current) => ({ ...current, [field]: value }))
    setFieldErrors((current) => ({ ...current, [field]: undefined }))
    setNotice(undefined)
  }

  const submit = async () => {
    if (mutation.pending) return

    const validationErrors = validateProfile(values)
    if (Object.keys(validationErrors).length) {
      setFieldErrors(validationErrors)
      setNotice(undefined)
      return
    }

    setFieldErrors({})
    setNotice(undefined)
    try {
      const saved = await mutation.run(formToProfilePatch(values))
      setValues(profileToForm(saved))
      setNotice({
        kind: 'success',
        message: 'Tên và ảnh đại diện mới sẽ hiện trên toàn bộ trang.',
        title: 'Đã lưu hồ sơ',
      })
      // Cập nhật tại chỗ để đầu trang đổi tên/ảnh mà không đưa phiên về trạng thái đang tải.
      if (user) {
        setAuthenticatedUser({
          ...user,
          avatarUrl: saved.avatarUrl ?? null,
          displayName: saved.displayName,
          hskLevel: saved.hskLevel ?? null,
        })
      }
    } catch (error) {
      const failure = getProfileFailure(error)
      setFieldErrors(failure.fieldErrors)
      if (failure.message) {
        setNotice({
          kind: 'error',
          message: failure.message,
          requestId: failure.requestId,
          title: 'Chưa lưu được hồ sơ',
        })
      }
    }
  }

  return (
    <ProfileView
      email={query.data?.email}
      emailVerified={query.data?.emailVerified}
      fieldErrors={fieldErrors}
      loadError={
        query.error ? { message: query.error.message, requestId: query.error.requestId } : undefined
      }
      notice={notice}
      onChange={updateField}
      onRetry={query.reload}
      onSubmit={() => void submit()}
      state={state}
      values={values}
    />
  )
}
