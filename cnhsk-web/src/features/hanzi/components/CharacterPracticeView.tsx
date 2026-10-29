import { ArrowLeft, Eye, Headphones, Lock, RotateCcw, Undo2 } from 'lucide-react'

import { cn } from '@/shared/lib/cn'
import {
  Button,
  Card,
  EmptyState,
  ErrorState,
  HanziText,
  LevelBadge,
  Skeleton,
} from '@/shared/ui'

import type { CharacterDetail, WritingMode } from '../types'
import { WRITING_MODE_LABELS, WRITING_MODES, writingModeAvailability } from '../writingModes'
import { HanziCanvasSlot } from './HanziCanvasSlot'

export interface CharacterPracticeViewProps {
  character?: CharacterDetail
  error?: { message: string; requestId?: string }
  mode?: WritingMode
  onModeChange?: (mode: WritingMode) => void
  onRetry: () => void
  state: 'loading' | 'ready' | 'not-found' | 'error'
}

const BACK_PATH = '/learn/characters/writing'

export function CharacterPracticeView({
  character,
  error,
  mode,
  onModeChange,
  onRetry,
  state,
}: CharacterPracticeViewProps) {
  if (state === 'loading') {
    return (
      <div aria-label="Đang tải chữ" className="space-y-4">
        <Skeleton variant="card" />
        <Skeleton variant="card" />
      </div>
    )
  }

  if (state === 'not-found') {
    return (
      <EmptyState
        action={<Button to={BACK_PATH}>Chọn chữ khác</Button>}
        description="Chữ này không tồn tại hoặc không phải chữ đơn."
        title="Không tìm thấy chữ"
      />
    )
  }

  if (state === 'error' || !character) {
    return (
      <ErrorState
        message={error?.message ?? 'Vui lòng thử lại sau.'}
        onRetry={onRetry}
        requestId={error?.requestId}
        title="Không thể tải chữ"
      />
    )
  }

  return (
    <div className="space-y-6">
      <Button to={BACK_PATH} variant="ghost">
        <ArrowLeft aria-hidden="true" className="size-5" />
        Danh sách chữ
      </Button>

      <Card className="flex flex-wrap items-center gap-6">
        <HanziText size="lg">{character.text}</HanziText>
        <div className="min-w-0 flex-1 space-y-1">
          <p className="text-h3 text-ink">
            {character.pinyin ?? '—'}
            {character.sinoViet ? (
              <span className="ml-2 text-body text-ink-secondary">({character.sinoViet})</span>
            ) : null}
          </p>
          <p className="text-body text-ink-secondary">{character.meaning}</p>
          <p className="flex flex-wrap items-center gap-2 text-body-sm text-ink-muted">
            {character.hskLevel ? <LevelBadge exact level={character.hskLevel} /> : null}
            {character.strokeCount ? <span>{character.strokeCount} nét</span> : null}
            {character.radical ? (
              <span>
                Bộ thủ{' '}
                <span className="font-hanzi" lang="zh-CN">
                  {character.radical}
                </span>
              </span>
            ) : null}
          </p>
        </div>
      </Card>

      <fieldset className="space-y-3">
        <legend className="text-h3 text-ink">Chế độ luyện</legend>
        <div className="grid gap-3 sm:grid-cols-3">
          {WRITING_MODES.map((option) => {
            const availability = writingModeAvailability(character, option)
            const selected = option === mode
            return (
              <button
                aria-pressed={selected}
                className={cn(
                  'flex min-h-touch flex-col items-start gap-1 rounded-md border bg-surface p-4 text-left transition-colors',
                  'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2',
                  'disabled:cursor-not-allowed disabled:opacity-70',
                  selected ? 'border-brand-600 bg-brand-50' : 'border-line hover:border-brand-600',
                )}
                disabled={!availability.available}
                key={option}
                onClick={() => onModeChange?.(option)}
                type="button"
              >
                <span className="text-body font-semibold text-ink">
                  {WRITING_MODE_LABELS[option].title}
                </span>
                <span className="text-body-sm text-ink-secondary">
                  {WRITING_MODE_LABELS[option].description}
                </span>
                {availability.reason ? (
                  <span className="mt-1 flex items-center gap-1 text-caption text-ink-muted">
                    <Lock aria-hidden="true" className="size-3.5" />
                    {availability.reason}
                  </span>
                ) : null}
              </button>
            )
          })}
        </div>
      </fieldset>

      {mode ? (
        <Card className="space-y-5">
          <h2 className="text-h3 text-ink">{WRITING_MODE_LABELS[mode].title}</h2>
          {mode === 'RECALL' ? (
            <p className="text-body-sm text-ink-secondary" role="status">
              Chữ mẫu hiện trong 5 giây rồi ẩn đi.
            </p>
          ) : null}
          <HanziCanvasSlot />
          <ModeControls mode={mode} />
          <p className="rounded-md bg-info-bg p-3 text-body-sm text-info">
            Viết xong, kết quả hiện "Đã ghi nhận". Viết lại cùng chữ, cùng chế độ trong 10 phút thì
            hiện "Chưa tính (luyện lại trong 10 phút)".
          </p>
        </Card>
      ) : (
        <EmptyState
          description="Chữ này chưa có dữ liệu nét nên chưa luyện viết được. Bạn vẫn có thể luyện nhận diện chữ."
          title="Chưa luyện viết được"
        />
      )}
    </div>
  )
}

/** Nút điều khiển từng chế độ (FR-031…FR-033); bật khi khung viết có thư viện chấm nét. */
function ModeControls({ mode }: { mode: WritingMode }) {
  return (
    <div className="flex flex-wrap justify-center gap-3">
      {mode === 'STROKE' ? (
        <Button disabled variant="outline">
          <RotateCcw aria-hidden="true" className="size-5" />
          Xem lại nét
        </Button>
      ) : null}
      {mode === 'RECALL' ? (
        <>
          <Button disabled variant="outline">
            <Eye aria-hidden="true" className="size-5" />
            Xem mẫu thêm (còn 2 lần)
          </Button>
          <Button disabled variant="ghost">
            <Undo2 aria-hidden="true" className="size-5" />
            Chưa nhớ — về viết theo nét
          </Button>
        </>
      ) : null}
      {mode === 'DICTATION' ? (
        <>
          <Button disabled variant="outline">
            <Headphones aria-hidden="true" className="size-5" />
            Nghe phát âm (tối đa 3 lần)
          </Button>
          <Button disabled variant="ghost">
            Không nghe được
          </Button>
        </>
      ) : null}
    </div>
  )
}
