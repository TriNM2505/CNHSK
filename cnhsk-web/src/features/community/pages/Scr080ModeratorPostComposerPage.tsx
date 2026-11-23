import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'

import { listContests } from '@/features/contests/api/contests'
import { useApiQuery } from '@/shared/api'

import { createModeratorPost } from '../api/posts'
import {
  ModeratorPostComposerView,
  type ModeratorPostErrors,
  type ModeratorPostValues,
} from '../components/ModeratorPostComposerView'

const WEEK_MS = 7 * 24 * 60 * 60 * 1000

/** `YYYY-MM-DDTHH:mm` in the browser's own time zone, the format a datetime-local input expects. */
function toLocalInput(date: Date): string {
  const shifted = new Date(date.getTime() - date.getTimezoneOffset() * 60_000)
  return shifted.toISOString().slice(0, 16)
}

function validate(values: ModeratorPostValues): ModeratorPostErrors {
  const errors: ModeratorPostErrors = {}
  if (!values.title.trim()) errors.title = 'Nhập tiêu đề.'
  else if (values.title.trim().length > 200) errors.title = 'Tiêu đề tối đa 200 ký tự.'
  if (!values.content.trim()) errors.content = 'Nhập nội dung.'
  if (values.category === 'CONTEST' && !values.contestId) errors.contestId = 'Chọn cuộc thi.'
  if (values.pinned && values.pinnedUntil) {
    if (new Date(values.pinnedUntil).getTime() <= Date.now()) errors.pinnedUntil = 'Chọn thời điểm trong tương lai.'
  } else if (values.pinned && values.category !== 'CONTEST') {
    errors.pinnedUntil = 'Chọn thời điểm bỏ ghim.'
  }
  return errors
}

export default function Scr080ModeratorPostComposerPage() {
  const navigate = useNavigate()
  const [values, setValues] = useState<ModeratorPostValues>(() => ({
    category: 'ANNOUNCEMENT',
    contestId: '',
    content: '',
    pinned: true,
    pinnedUntil: toLocalInput(new Date(Date.now() + WEEK_MS)),
    title: '',
  }))
  const [errors, setErrors] = useState<ModeratorPostErrors>({})
  const [formError, setFormError] = useState<string>()
  const [busy, setBusy] = useState(false)
  const contestQuery = useApiQuery((signal) => listContests({ size: 50 }, signal), [])
  const contests = useMemo(
    () => (contestQuery.data?.items ?? []).filter((contest) => contest.state !== 'ENDED'),
    [contestQuery.data],
  )

  function change<K extends keyof ModeratorPostValues>(field: K, value: ModeratorPostValues[K]) {
    setValues((current) => {
      const next = { ...current, [field]: value }
      // A contest guide stays pinned until the contest ends unless the moderator picks another moment.
      if (field === 'contestId') {
        const contest = contests.find((item) => String(item.id) === value)
        if (contest) next.pinnedUntil = toLocalInput(new Date(contest.endsAt))
      }
      return next
    })
    setErrors((current) => ({ ...current, [field]: undefined }))
  }

  async function submit() {
    const found = validate(values)
    setErrors(found)
    if (Object.keys(found).length > 0) return
    setBusy(true)
    setFormError(undefined)
    try {
      await createModeratorPost({
        category: values.category,
        content: values.content.trim(),
        contestId: values.category === 'CONTEST' && values.contestId ? Number(values.contestId) : null,
        pinnedUntil: values.pinned && values.pinnedUntil ? new Date(values.pinnedUntil).toISOString() : null,
        title: values.title.trim(),
      })
      navigate('/community')
    } catch (error) {
      setFormError(error instanceof Error ? error.message : 'Chưa đăng được bài, thử lại.')
      setBusy(false)
    }
  }

  return (
    <ModeratorPostComposerView
      busy={busy}
      contests={contests}
      errors={errors}
      formError={formError}
      onCancel={() => navigate('/moderation/posts')}
      onChange={change}
      onSubmit={() => void submit()}
      values={values}
    />
  )
}
