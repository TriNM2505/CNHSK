import { ArrowLeft, Eye, Headphones, Lock, RotateCcw, Timer, Undo2 } from 'lucide-react'
import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'

import { cn } from '@/shared/lib/cn'
import {
  Button,
  Card,
  EmptyState,
  ErrorState,
  HanziText,
  LevelBadge,
  PageHeader,
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
const CHALLENGE_PATH = '/learn/characters/writing/challenge'

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

      <PageHeader
        subtitle="Tập viết theo thứ tự nét chuẩn và chọn mức luyện phù hợp với chữ này."
        title="Luyện viết chữ Hán"
      />

      <fieldset className="space-y-3">
        <legend className="text-h3 text-ink">Chế độ luyện</legend>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
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
          <Link
            className="flex min-h-touch flex-col items-start gap-1 rounded-md border border-line bg-surface p-4 text-left transition-colors hover:border-brand-600 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-2"
            to={CHALLENGE_PATH}
          >
            <span className="text-body font-semibold text-ink">Thử thách</span>
            <span className="text-body-sm text-ink-secondary">
              Viết 10 chữ hệ thống chọn trong giới hạn 200 giây.
            </span>
            <span className="mt-1 flex items-center gap-1 text-caption text-ink-muted">
              <Timer aria-hidden="true" className="size-3.5" />
              Tối đa 3 chữ trượt
            </span>
          </Link>
        </div>
      </fieldset>

      {mode ? (
        <>
          <div className="grid gap-6 lg:grid-cols-2">
            <Card className="min-w-0 space-y-5">
              <div className="text-center">
                <p className="text-label text-ink-secondary">{WRITING_MODE_LABELS[mode].title}</p>
                {mode === 'STROKE' ? (
                  <>
                    <HanziText className="mt-2 block" size="lg">
                      {character.text}
                    </HanziText>
                    <p className="mt-2 text-body text-ink-secondary">
                      {character.pinyin ?? '—'} · {character.meaning}
                    </p>
                  </>
                ) : (
                  <p className="mt-2 text-body-sm text-ink-secondary" role="status">
                    {mode === 'RECALL'
                      ? 'Chữ mẫu chỉ hiện 5 giây trước khi bắt đầu và được ẩn trong lúc viết.'
                      : 'Nghe phát âm rồi viết; chữ và viền mẫu được ẩn trong lúc viết.'}
                  </p>
                )}
              </div>
              <HanziCanvasSlot />
              <ModeControls mode={mode} />
            </Card>

            <PracticeContext character={character} mode={mode} />
          </div>
          <p className="rounded-md bg-info-bg p-3 text-body-sm text-info">
            Viết xong, kết quả hiện "Đã ghi nhận". Viết lại cùng chữ, cùng chế độ trong 10 phút thì
            hiện "Chưa tính (luyện lại trong 10 phút)".
          </p>
        </>
      ) : (
        <EmptyState
          description="Chữ này chưa có dữ liệu nét nên chưa luyện viết được. Bạn vẫn có thể luyện nhận diện chữ."
          title="Chưa luyện viết được"
        />
      )}
    </div>
  )
}

function PracticeContext({ character, mode }: { character: CharacterDetail; mode: WritingMode }) {
  if (mode !== 'STROKE') {
    return (
      <Card className="space-y-4">
        <h2 className="text-h3 text-ink">
          {mode === 'RECALL' ? 'Viết từ trí nhớ' : 'Nghe rồi viết'}
        </h2>
        <p className="text-body text-ink-secondary">
          {mode === 'RECALL'
            ? 'Bạn có tối đa 2 lần xem mẫu thêm, mỗi lần 3 giây. Khi khung viết mở, chữ mẫu không nằm cạnh bài làm.'
            : 'Bạn có tối đa 3 lượt nghe. Nếu không nghe được, hãy chuyển về Viết theo nét mà không gửi kết quả nghe chép.'}
        </p>
        <p className="rounded-md bg-warning-bg p-3 text-body-sm text-warning">
          Chữ đích và thông tin gợi ý được giấu trong lúc trả lời.
        </p>
      </Card>
    )
  }

  return (
    <Card className="space-y-4">
      <h2 className="text-h3 text-ink">Thông tin chữ</h2>
      <div className="flex flex-wrap items-center gap-4">
        <HanziText size="md">{character.text}</HanziText>
        <div className="min-w-0 flex-1">
          <p className="text-h3 text-ink">
            {character.pinyin ?? '—'}
            {character.sinoViet ? (
              <span className="ml-2 text-body text-ink-secondary">({character.sinoViet})</span>
            ) : null}
          </p>
          <p className="text-body text-ink-secondary">{character.meaning}</p>
        </div>
      </div>
      <dl className="grid gap-3 sm:grid-cols-2">
        <Fact label="Cấp">
          {character.hskLevel ? <LevelBadge exact level={character.hskLevel} /> : '—'}
        </Fact>
        <Fact label="Số nét">{character.strokeCount ? `${character.strokeCount} nét` : '—'}</Fact>
        <Fact label="Bộ thủ">
          {character.radical ? (
            <span className="font-hanzi" lang="zh-CN">
              {character.radical}
            </span>
          ) : (
            '—'
          )}
        </Fact>
      </dl>
    </Card>
  )
}

function Fact({ children, label }: { children: ReactNode; label: string }) {
  return (
    <div className="rounded-sm bg-surface-subtle p-3">
      <dt className="text-caption text-ink-muted">{label}</dt>
      <dd className="mt-1 text-body font-semibold text-ink">{children}</dd>
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
