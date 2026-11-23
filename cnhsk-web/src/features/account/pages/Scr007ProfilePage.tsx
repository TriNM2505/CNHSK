import { useEffect, useState } from 'react'

import { getUploadSignature, uploadToCloudinary } from '@/features/community/api/media'
import { useApiQuery } from '@/shared/api'
import { useCurrentUser } from '@/shared/auth'

import { getProfileFailure } from '../accountForms'
import { getProfile, updateProfile } from '../api/account'
import type { FormNoticeValue } from '../components/FormNotice'
import { ProfileView, type ProfileViewProps } from '../components/ProfileView'
import type { Profile, ProfilePatch } from '../types'

const MAX_AVATAR_BYTES = 5 * 1024 * 1024

/** SCR-007: every change saves on its own — a new photo, a renamed profile or another HSK level. */
export default function Scr007ProfilePage() {
  const { setAuthenticatedUser, user } = useCurrentUser()
  const query = useApiQuery(getProfile, [])
  const [profile, setProfile] = useState<Profile>()
  const [busy, setBusy] = useState<ProfileViewProps['busy']>()
  const [progress, setProgress] = useState(0)
  const [notice, setNotice] = useState<FormNoticeValue>()

  useEffect(() => {
    if (query.data) setProfile(query.data)
  }, [query.data])

  async function save(patch: ProfilePatch, what: NonNullable<ProfileViewProps['busy']>): Promise<boolean> {
    setBusy(what)
    setNotice(undefined)
    try {
      const saved = await updateProfile(patch)
      setProfile((current) => ({ ...current, ...saved }))
      // The header shows the new name and photo at once without reloading the session.
      if (user) {
        setAuthenticatedUser({
          ...user,
          avatarUrl: saved.avatarUrl ?? null,
          displayName: saved.displayName,
          hskLevel: saved.hskLevel ?? null,
        })
      }
      return true
    } catch (error) {
      const failure = getProfileFailure(error)
      const field = Object.values(failure.fieldErrors).find(Boolean)
      setNotice({ kind: 'error', message: field ?? failure.message ?? 'Thử lại sau.', requestId: failure.requestId, title: 'Chưa lưu được' })
      return false
    } finally {
      setBusy(undefined)
    }
  }

  async function changeAvatar(file: File) {
    if (!file.type.startsWith('image/')) {
      setNotice({ kind: 'error', message: 'Chọn một tệp ảnh.', title: 'Chưa đổi được ảnh' })
      return
    }
    if (file.size > MAX_AVATAR_BYTES) {
      setNotice({ kind: 'error', message: 'Ảnh đại diện tối đa 5 MB.', title: 'Chưa đổi được ảnh' })
      return
    }
    setBusy('avatar')
    setProgress(0)
    setNotice(undefined)
    try {
      const uploaded = await uploadToCloudinary(file, await getUploadSignature('avatar'), setProgress)
      await save({ avatarUrl: uploaded.url }, 'avatar')
    } catch (error) {
      setNotice({
        kind: 'error',
        message: error instanceof Error ? error.message : 'Thử lại sau.',
        title: 'Chưa tải được ảnh lên',
      })
      setBusy(undefined)
    }
  }

  async function rename(name: string): Promise<boolean> {
    const trimmed = name.trim()
    if (trimmed === profile?.displayName) return true
    if (trimmed.length < 2 || trimmed.length > 100) {
      setNotice({ kind: 'error', message: 'Tên hiển thị cần 2–100 ký tự.', title: 'Chưa lưu được' })
      return false
    }
    return save({ displayName: trimmed }, 'name')
  }

  return (
    <ProfileView
      busy={busy}
      loadError={query.error ? { message: query.error.message, requestId: query.error.requestId } : undefined}
      notice={notice}
      onAvatarFile={(file) => void changeAvatar(file)}
      onLevelChange={(level) => void save({ hskLevel: level }, 'level')}
      onRename={rename}
      onRetry={query.reload}
      profile={profile}
      state={query.state === 'error' ? 'error' : profile ? 'ready' : 'loading'}
      uploadProgress={progress}
    />
  )
}
