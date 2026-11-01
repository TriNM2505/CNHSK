import { ArrowLeft, Timer } from 'lucide-react'
import type { RefObject } from 'react'

import type { FinishResult, ServedItemView } from '@/features/practice'
import { SessionProgress } from '@/features/practice'
import { Button, Card, EmptyState, ErrorState, Skeleton, StatTile } from '@/shared/ui'

import { HanziCanvas, type HanziCanvasHandle } from './HanziCanvas'

export const CHALLENGE_TIME_LIMIT_SECONDS = 200
export const CHALLENGE_MAX_MISTAKES = 3
export const CHALLENGE_SIZE = 10

export interface WritingChallengeViewProps {
  canvasRef?: RefObject<HanziCanvasHandle>
  completed?: number
  current?: number
  currentMistakes?: number
  dataError?: boolean
  error?: { errorCode?: string; message: string; requestId?: string }
  failed?: number
  mistakes?: number
  finishResult?: FinishResult | null
  item?: ServedItemView
  notEnough?: { have: number; need: number }
  onCanvasReady?: () => void
  onDataError?: () => void
  onRetry?: () => void
  onRetryFinish?: () => void
  onStart?: () => void
  pending?: boolean
  processed?: number
  retryAfterSeconds?: number
  remainingSeconds?: number
  state: 'loading' | 'intro' | 'ready' | 'error' | 'unknown' | 'expired'
  total?: number
  notice?: string
}

const BACK_PATH = '/learn/characters/writing'

function formatSeconds(value: number) {
  const seconds = Math.max(0, Math.floor(value))
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`
}

function countFromResult(result: FinishResult, key: string): number {
  const value = result.extra?.[key]
  return typeof value === 'number' ? value : 0
}

function reasonFromResult(result: FinishResult): string {
  const value = result.extra?.finishedReason
  if (value === 'MAX_MISTAKES') return 'Kết thúc do trượt 3 chữ.'
  if (value === 'ALL_DONE') return 'Bạn đã hoàn thành thử thách.'
  return 'Thời gian thử thách đã hết.'
}

export function WritingChallengeView({
  canvasRef,
  completed = 0,
  current = 1,
  currentMistakes = 0,
  dataError = false,
  error,
  failed: failedProp,
  mistakes,
  finishResult,
  item,
  notEnough,
  notice,
  onCanvasReady,
  onDataError,
  onRetry,
  onRetryFinish,
  onStart,
  pending = false,
  processed,
  retryAfterSeconds,
  remainingSeconds = CHALLENGE_TIME_LIMIT_SECONDS,
  state,
  total = CHALLENGE_SIZE,
}: WritingChallengeViewProps) {
  const failed = failedProp ?? mistakes ?? 0
  const processedCount = processed ?? completed + failed
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

  if (state === 'expired') {
    return (
      <div className="space-y-5">
        {back}
        <EmptyState description="Lượt thử thách đã hết hạn. Hãy bắt đầu lượt mới." title="Đã hết hạn" />
      </div>
    )
  }

  if (finishResult) {
    return (
      <section aria-labelledby="challenge-summary-title" className="space-y-5">
        {back}
        <Card className="space-y-5 text-center">
          <h1 className="text-h2 text-ink" id="challenge-summary-title">Kết quả thử thách</h1>
          <p className="text-body text-ink-secondary">{reasonFromResult(finishResult)}</p>
          <div className="grid gap-3 sm:grid-cols-3">
            <StatTile label="Hoàn thành" value={countFromResult(finishResult, 'completedChars')} />
            <StatTile label="Chữ trượt" value={countFromResult(finishResult, 'failedChars')} />
            <StatTile label="Chưa hoàn thành" value={countFromResult(finishResult, 'incompleteChars')} />
          </div>
          <Button to={BACK_PATH}>Luyện lượt mới</Button>
        </Card>
      </section>
    )
  }

  if (state === 'unknown') {
    return (
      <div className="space-y-5">
        {back}
        <Card className="space-y-4">
          <h1 className="text-h2 text-ink">{error?.errorCode === 'RATE_LIMITED' ? 'Kết quả đang chờ gửi lại' : 'Chưa xác nhận được kết quả'}</h1>
          <p className="text-body text-ink-secondary">
            {error?.errorCode === 'RATE_LIMITED'
              ? `Máy chủ giới hạn lượt gửi. Thử lại thủ công${retryAfterSeconds ? ` sau ${retryAfterSeconds} giây` : ''}.`
              : 'Máy chủ có thể đã nhận lượt nộp. Kiểm tra trạng thái trước khi gửi lại.'}
          </p>
          {error ? <p role="alert" className="text-body-sm text-danger">{error.message}</p> : null}
          <Button disabled={pending} onClick={onRetryFinish}>{pending ? 'Đang kiểm tra…' : 'Kiểm tra / gửi lại'}</Button>
        </Card>
      </div>
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
            <li>{CHALLENGE_TIME_LIMIT_SECONDS} giây cho cả lượt, chỉ hiện pinyin và nghĩa.</li>
            <li>Sai từ 3 nét ở một chữ là trượt chữ đó; trượt {CHALLENGE_MAX_MISTAKES} chữ thì kết thúc.</li>
          </ul>
          <p className="text-body-sm text-ink-muted">Đồng hồ vẫn chạy khi bạn rời trang.</p>
          {notEnough ? (
            <p className="rounded-md bg-info-bg p-3 text-body-sm text-info" role="status">
              Bạn cần viết theo nét xong ít nhất {notEnough.need} chữ (hiện có {notEnough.have}).
            </p>
          ) : null}
          {error ? <p className="rounded-md bg-danger-bg p-3 text-body-sm text-danger" role="alert">{error.message}</p> : null}
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

  const promptText = item?.prompt.text
  const character = typeof promptText === 'string' ? promptText : undefined
  const pinyin = typeof item?.prompt.pinyin === 'string' ? item.prompt.pinyin : undefined
  const meaning = typeof item?.prompt.meaning === 'string' ? item.prompt.meaning : undefined
  const strokeCount = typeof item?.prompt.strokeCount === 'number' ? item.prompt.strokeCount : undefined

  return (
    <div className="space-y-5">
      {back}
      <p className="text-body-sm text-ink-muted">Đồng hồ vẫn chạy khi bạn rời trang.</p>
      {error ? <p role="alert" className="rounded-md bg-danger-bg p-3 text-body-sm text-danger">{error.message}</p> : null}
      {notice ? <p role="status" className="rounded-md bg-info-bg p-3 text-body-sm text-info">{notice}</p> : null}
      <div className="grid gap-3 sm:grid-cols-3">
        <StatTile label="Thời gian còn lại" value={formatSeconds(remainingSeconds)} />
        <StatTile label="Chữ trượt" value={`${failed}/${CHALLENGE_MAX_MISTAKES}`} />
        <StatTile label="Chữ" value={`${current}/${total}`} />
      </div>
      <p className="text-body-sm text-ink-secondary">Đã xử lý {processedCount} / {total} chữ · Lỗi nét chữ này: {currentMistakes}/3</p>
      <SessionProgress current={current} total={total} />
      <Card className="space-y-5">
        <div className="text-center">
          <p className="text-h2 text-ink">{pinyin ?? '—'}</p>
          <p className="text-body text-ink-secondary">{meaning}</p>
          {strokeCount ? <p className="text-body-sm text-ink-muted">{strokeCount} nét</p> : null}
        </div>
        {character ? (
          <HanziCanvas
            character={character}
            key={item?.no}
            onDataError={onDataError}
            onReady={onCanvasReady}
            ref={canvasRef}
            showCharacter={false}
            showOutline={false}
          />
        ) : item ? (
          <p role="alert" className="text-body-sm text-danger">Không thể tải câu hỏi thử thách.</p>
        ) : null}
        {dataError ? <p role="status" className="text-body-sm text-danger">Chữ này chưa có dữ liệu nét</p> : null}
        {pending ? <p role="status" className="text-body-sm text-ink-secondary">Đang gửi kết quả…</p> : null}
      </Card>
    </div>
  )
}
