import { ArrowLeft, Timer } from 'lucide-react'

import type { ServedItemView } from '@/features/practice'
import { SessionProgress } from '@/features/practice'
import { Button, Card, EmptyState, ErrorState, Skeleton, StatTile } from '@/shared/ui'

import { HanziCanvasSlot } from './HanziCanvasSlot'

export const CHALLENGE_TIME_LIMIT_SECONDS = 200
export const CHALLENGE_MAX_MISTAKES = 3
export const CHALLENGE_SIZE = 10

export interface WritingChallengeViewProps {
  current?: number
  error?: { message: string; requestId?: string }
  item?: ServedItemView
  mistakes?: number
  /** FR-020: số chữ đã viết theo nét xong, khi chưa đủ 10. */
  notEnough?: { have: number; need: number }
  onRetry?: () => void
  onStart?: () => void
  pending?: boolean
  remainingSeconds?: number
  state: 'loading' | 'intro' | 'ready' | 'error'
  total?: number
}

const BACK_PATH = '/learn/characters/writing'

function formatSeconds(value: number) {
  const seconds = Math.max(0, Math.floor(value))
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`
}

export function WritingChallengeView({
  current = 1,
  error,
  item,
  mistakes = 0,
  notEnough,
  onRetry,
  onStart,
  pending = false,
  remainingSeconds = CHALLENGE_TIME_LIMIT_SECONDS,
  state,
  total = CHALLENGE_SIZE,
}: WritingChallengeViewProps) {
  const back = (
    <Button to={BACK_PATH} variant="ghost">
      <ArrowLeft aria-hidden="true" className="size-5" />
      Danh sách chữ
    </Button>
  )

  if (state === 'loading') {
    return (
      <div aria-label="Đang tải thử thách" className="space-y-4">
        <Skeleton variant="card" />
        <Skeleton variant="card" />
      </div>
    )
  }

  if (state === 'error') {
    return (
      <ErrorState
        message={error?.message ?? 'Vui lòng thử lại sau.'}
        onRetry={onRetry}
        requestId={error?.requestId}
        title="Không thể tải thử thách"
      />
    )
  }

  if (state === 'intro') {
    return (
      <div className="space-y-6">
        {back}
        <Card className="space-y-4">
          <h1 className="text-h2 text-ink">Thử thách viết chữ</h1>
          <ul className="list-disc space-y-1 pl-5 text-body text-ink-secondary">
            <li>{CHALLENGE_SIZE} chữ do hệ thống chọn từ những chữ bạn đã viết theo nét.</li>
            <li>{CHALLENGE_TIME_LIMIT_SECONDS} giây cho cả lượt, chữ chỉ hiện pinyin và nghĩa.</li>
            <li>Sai từ 3 nét ở một chữ là trượt chữ đó; trượt {CHALLENGE_MAX_MISTAKES} chữ thì kết thúc.</li>
          </ul>
          {notEnough ? (
            <p className="rounded-md bg-info-bg p-3 text-body-sm text-info" role="status">
              Bạn cần viết theo nét xong ít nhất {notEnough.need} chữ (hiện có {notEnough.have}).
            </p>
          ) : null}
          {error ? (
            <p className="rounded-md bg-danger-bg p-3 text-body-sm text-danger" role="alert">
              {error.message}
            </p>
          ) : null}
          <div className="flex justify-end">
            <Button disabled={pending || Boolean(notEnough)} onClick={onStart}>
              <Timer aria-hidden="true" className="size-5" />
              Bắt đầu thử thách
            </Button>
          </div>
        </Card>
        {notEnough ? (
          <EmptyState
            action={<Button to={BACK_PATH}>Luyện viết theo nét</Button>}
            description="Hãy chọn một chữ và hoàn thành chế độ Viết theo nét."
            title="Chưa đủ chữ để thử thách"
          />
        ) : null}
      </div>
    )
  }

  const pinyin = typeof item?.prompt.pinyin === 'string' ? item.prompt.pinyin : undefined
  const meaning = typeof item?.prompt.meaning === 'string' ? item.prompt.meaning : undefined
  const strokeCount =
    typeof item?.prompt.strokeCount === 'number' ? item.prompt.strokeCount : undefined

  return (
    <div className="space-y-5">
      {back}
      <div className="grid gap-3 sm:grid-cols-3">
        <StatTile label="Thời gian còn lại" value={formatSeconds(remainingSeconds)} />
        <StatTile label="Chữ trượt" value={`${mistakes}/${CHALLENGE_MAX_MISTAKES}`} />
        <StatTile label="Chữ" value={`${current}/${total}`} />
      </div>
      <SessionProgress current={current} total={total} />
      <Card className="space-y-5">
        <div className="text-center">
          <p className="text-h2 text-ink">{pinyin ?? '—'}</p>
          <p className="text-body text-ink-secondary">{meaning}</p>
          {strokeCount ? <p className="text-body-sm text-ink-muted">{strokeCount} nét</p> : null}
        </div>
        <HanziCanvasSlot />
      </Card>
    </div>
  )
}
