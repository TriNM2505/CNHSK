import { useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'

import { listLeaderboardGames } from '@/features/leaderboard/api/leaderboards'
import { useApiQuery } from '@/shared/api'

import { createGameContest } from '../api/game-contests'
import { createModeratorPost } from '../api/posts'
import type { MediaItem } from '../types'
import {
  ModeratorPostComposerView,
  type ModeratorPostErrors,
  type ModeratorPostValues,
} from '../components/ModeratorPostComposerView'

const HOUR_MS = 60 * 60 * 1000
const WEEK_MS = 7 * 24 * HOUR_MS

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
  if (values.kind === 'GAME_CONTEST') {
    const start = new Date(values.startsAt).getTime()
    const end = new Date(values.endsAt).getTime()
    if (!values.gameCode) errors.gameCode = 'Chọn trò chơi.'
    if (!values.startsAt) errors.startsAt = 'Chọn giờ mở.'
    if (!values.endsAt) errors.endsAt = 'Chọn giờ đóng.'
    else if (end <= Date.now()) errors.endsAt = 'Chọn thời điểm trong tương lai.'
    else if (values.startsAt && end <= start) errors.endsAt = 'Giờ đóng phải sau giờ mở.'
    const prizes = Number(values.prizeCount)
    if (!Number.isInteger(prizes) || prizes < 1 || prizes > 50) errors.prizeCount = 'Từ 1 đến 50 người.'
  }
  if (values.kind === 'COMMENT_CONTEST') {
    if (!values.commentsCloseAt) errors.commentsCloseAt = 'Chọn giờ đóng bình luận.'
    else if (new Date(values.commentsCloseAt).getTime() <= Date.now()) errors.commentsCloseAt = 'Chọn thời điểm trong tương lai.'
  }
  if (values.kind === 'GAME_CONTEST') return errors
  const contest = values.kind === 'COMMENT_CONTEST'
  if (values.pinned && values.pinnedUntil) {
    if (new Date(values.pinnedUntil).getTime() <= Date.now()) errors.pinnedUntil = 'Chọn thời điểm trong tương lai.'
  } else if (values.pinned && !contest) {
    errors.pinnedUntil = 'Chọn thời điểm bỏ ghim.'
  }
  return errors
}

export default function Scr080ModeratorPostComposerPage() {
  const navigate = useNavigate()
  // The results screen hands over a winners announcement to polish and post.
  const draft = (useLocation().state as { announcement?: { title: string; content: string } } | null)?.announcement
  const [values, setValues] = useState<ModeratorPostValues>(() => ({
    category: 'ANNOUNCEMENT',
    commentsCloseAt: '',
    endsAt: toLocalInput(new Date(Date.now() + 2 * HOUR_MS)),
    gameCode: '',
    prizeCount: '3',
    startsAt: toLocalInput(new Date(Date.now() + HOUR_MS)),
    kind: 'ANNOUNCEMENT',
    content: draft?.content ?? '',
    pinned: true,
    pinnedUntil: toLocalInput(new Date(Date.now() + WEEK_MS)),
    title: draft?.title ?? '',
  }))
  const [errors, setErrors] = useState<ModeratorPostErrors>({})
  const [formError, setFormError] = useState<string>()
  const [busy, setBusy] = useState(false)
  const [media, setMedia] = useState<MediaItem[]>([])
  const gameQuery = useApiQuery((signal) => listLeaderboardGames(signal), [])


  function change<K extends keyof ModeratorPostValues>(field: K, value: ModeratorPostValues[K]) {
    setValues((current) => {
      const next = { ...current, [field]: value }
      // A comment contest is pinned until it closes by default.
      if (field === 'commentsCloseAt' && typeof value === 'string' && value) next.pinnedUntil = value
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
      if (values.kind === 'GAME_CONTEST') {
        const created = await createGameContest({
          content: values.content.trim(),
          endsAt: new Date(values.endsAt).toISOString(),
          gameCode: values.gameCode,
          media,
          prizeCount: Number(values.prizeCount),
          startsAt: new Date(values.startsAt).toISOString(),
          title: values.title.trim(),
        })
        navigate(`/community/contests/${created.contestId}`)
        return
      }
      const category =
        values.kind === 'ANNOUNCEMENT' ? 'ANNOUNCEMENT' : values.kind === 'TOPIC' ? values.category : 'CONTEST'
      await createModeratorPost({
        category,
        commentsCloseAt:
          values.kind === 'COMMENT_CONTEST' && values.commentsCloseAt ? new Date(values.commentsCloseAt).toISOString() : null,
        content: values.content.trim(),
        media,
        contestId: null,
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
      errors={errors}
      formError={formError}
      games={gameQuery.data ?? []}
      media={media}
      onMediaChange={setMedia}
      onCancel={() => navigate('/moderation/posts')}
      onChange={change}
      onSubmit={() => void submit()}
      values={values}
    />
  )
}
