import { Megaphone, MessageSquareText, Pin, Trophy } from 'lucide-react'
import type { FormEvent } from 'react'

import type { GameOption } from '@/features/leaderboard/types'
import { cn } from '@/shared/lib/cn'
import { Button, Card, FormField, Input, PageHeader, Select, Textarea } from '@/shared/ui'

import { POST_CATEGORIES, POST_CATEGORY_LABELS } from '../labels'
import type { MediaItem, PostCategory } from '../types'
import { GameSealPicker } from './GameSealPicker'
import { MediaPicker } from './MediaPicker'

/** What the moderator is publishing; a topic post uses category. */
export type ModeratorPostKind = 'ANNOUNCEMENT' | 'COMMENT_CONTEST' | 'GAME_CONTEST' | 'TOPIC'

export type ModeratorPostValues = {
  kind: ModeratorPostKind
  category: PostCategory
  /** Comment contests: local date-time when comments close. */
  commentsCloseAt: string
  /** Game contests: the game, the local open and close date-times and how many players win. */
  gameCode: string
  startsAt: string
  endsAt: string
  prizeCount: string
  title: string
  content: string
  pinned: boolean
  /** Local date-time as typed in the browser, `YYYY-MM-DDTHH:mm`. */
  pinnedUntil: string
}

export type ModeratorPostErrors = Partial<Record<keyof ModeratorPostValues, string>>

export type ModeratorPostComposerViewProps = {
  busy: boolean
  games: readonly GameOption[]
  errors: ModeratorPostErrors
  formError?: string
  media: readonly MediaItem[]
  onMediaChange: (media: MediaItem[]) => void
  onCancel: () => void
  onChange: <K extends keyof ModeratorPostValues>(field: K, value: ModeratorPostValues[K]) => void
  onSubmit: () => void
  values: ModeratorPostValues
}

const KINDS: readonly { hint: string; icon: typeof Megaphone; kind: ModeratorPostKind; label: string }[] = [
  { hint: 'Tin chung cho cả cộng đồng', icon: Megaphone, kind: 'ANNOUNCEMENT', label: 'Thông báo' },
  { hint: 'Trả lời trong bình luận, đóng theo giờ', icon: MessageSquareText, kind: 'COMMENT_CONTEST', label: 'Cuộc thi bình luận' },
  { hint: 'Chơi game trong khung giờ, có xếp hạng', icon: Trophy, kind: 'GAME_CONTEST', label: 'Cuộc thi game' },
]

/** SCR-080: a moderator writes a post that goes live at once, optionally pinned and tied to a contest. */
export function ModeratorPostComposerView({
  busy,
  errors,
  games,
  formError,
  media,
  onMediaChange,
  onCancel,
  onChange,
  onSubmit,
  values,
}: ModeratorPostComposerViewProps) {
  const isContest = values.kind === 'GAME_CONTEST'
  const isCommentContest = values.kind === 'COMMENT_CONTEST'
  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    onSubmit()
  }

  return (
    <main className="mx-auto w-full min-w-0 max-w-reading space-y-5 px-4 py-6 pb-16 sm:px-6">
      <PageHeader
        subtitle="Bài của kiểm duyệt viên đăng ngay, không qua hàng chờ duyệt."
        title="Đăng bài cộng đồng"
      />

      <Card className="p-5 sm:p-6">
        <form className="space-y-5" noValidate onSubmit={handleSubmit}>
          <fieldset className="space-y-2">
            <legend className="text-label text-ink">Loại bài</legend>
            <div className="grid gap-2 sm:grid-cols-3">
              {KINDS.map(({ hint, icon: Icon, kind, label }) => {
                const selected = values.kind === kind
                return (
                  <button
                    aria-pressed={selected}
                    className={cn(
                      'flex min-h-touch items-center gap-3 rounded-md border p-3 text-left transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus',
                      selected ? 'border-brand-600 bg-brand-50' : 'border-line bg-surface hover:border-brand-600',
                    )}
                    disabled={busy}
                    key={kind}
                    onClick={() => onChange('kind', kind)}
                    type="button"
                  >
                    <span
                      className={cn(
                        'flex size-10 shrink-0 items-center justify-center rounded-full',
                        selected ? 'bg-brand-600 text-surface' : 'bg-canvas text-brand-700',
                      )}
                    >
                      <Icon aria-hidden="true" className="size-5" />
                    </span>
                    <span className="min-w-0">
                      <span className="block text-label text-ink">{label}</span>
                      <span className="block text-caption text-ink-secondary">{hint}</span>
                    </span>
                  </button>
                )
              })}
            </div>
            <FormField className="mb-0" htmlFor="moderator-post-category" label="Hoặc đăng vào chủ đề thường">
              <Select
                disabled={busy}
                id="moderator-post-category"
                onChange={(event) => {
                  onChange('category', event.target.value as PostCategory)
                  onChange('kind', 'TOPIC')
                }}
                value={values.kind === 'TOPIC' ? values.category : ''}
              >
                <option value="">—</option>
                {POST_CATEGORIES.map((category) => (
                  <option key={category} value={category}>
                    {POST_CATEGORY_LABELS[category]}
                  </option>
                ))}
              </Select>
            </FormField>
          </fieldset>

          {isCommentContest ? (
            <FormField
              error={errors.commentsCloseAt}
              hint="Tới giờ này hệ thống tự khoá bình luận; bài ghim tới lúc đó."
              htmlFor="moderator-post-comments-close"
              label="Đóng bình luận lúc"
              required
            >
              <Input
                disabled={busy}
                id="moderator-post-comments-close"
                onChange={(event) => onChange('commentsCloseAt', event.target.value)}
                type="datetime-local"
                value={values.commentsCloseAt}
              />
            </FormField>
          ) : null}

          {isContest ? (
            <div className="space-y-4 rounded-md border border-line p-4">
              <FormField error={errors.gameCode} htmlFor="moderator-contest-game" label="Trò chơi" required>
                <div id="moderator-contest-game">
                  <GameSealPicker
                    disabled={busy}
                    games={games}
                    label="Trò chơi của cuộc thi"
                    onChange={(code) => onChange('gameCode', code)}
                    value={values.gameCode}
                  />
                </div>
              </FormField>
              <div className="grid gap-4 sm:grid-cols-2">
                <FormField error={errors.startsAt} htmlFor="moderator-contest-start" label="Mở lúc" required>
                  <Input
                    disabled={busy}
                    id="moderator-contest-start"
                    onChange={(event) => onChange('startsAt', event.target.value)}
                    type="datetime-local"
                    value={values.startsAt}
                  />
                </FormField>
                <FormField error={errors.endsAt} htmlFor="moderator-contest-end" label="Đóng lúc" required>
                  <Input
                    disabled={busy}
                    id="moderator-contest-end"
                    onChange={(event) => onChange('endsAt', event.target.value)}
                    type="datetime-local"
                    value={values.endsAt}
                  />
                </FormField>
              </div>
              <FormField
                className="mb-0"
                error={errors.prizeCount}
                hint="Câu hỏi trộn HSK 1–3. Mỗi người chơi không giới hạn lượt, lấy lượt điểm cao nhất."
                htmlFor="moderator-contest-prizes"
                label="Số người nhận giải"
                required
              >
                <Input
                  disabled={busy}
                  id="moderator-contest-prizes"
                  inputMode="numeric"
                  max={50}
                  min={1}
                  onChange={(event) => onChange('prizeCount', event.target.value)}
                  type="number"
                  value={values.prizeCount}
                />
              </FormField>
            </div>
          ) : null}

          <FormField error={errors.title} htmlFor="moderator-post-title" label="Tiêu đề" required>
            <Input
              disabled={busy}
              id="moderator-post-title"
              maxLength={200}
              onChange={(event) => onChange('title', event.target.value)}
              value={values.title}
            />
          </FormField>

          <FormField
            error={errors.content}
            htmlFor="moderator-post-content"
            label={isContest ? 'Hướng dẫn cuộc thi' : isCommentContest ? 'Đề bài và thể lệ' : 'Nội dung'}
            required
          >
            <Textarea
              disabled={busy}
              id="moderator-post-content"
              onChange={(event) => onChange('content', event.target.value)}
              placeholder={
                isContest
                  ? 'Thời gian thi, trò chơi, cách tính điểm, giải thưởng…'
                  : isCommentContest
                    ? 'Câu hỏi của cuộc thi, cách trả lời trong bình luận, giải thưởng…'
                    : 'Nội dung thông báo'
              }
              rows={10}
              value={values.content}
            />
          </FormField>

          <div className="space-y-2">
            <p className="text-label text-ink">Ảnh/video</p>
            <MediaPicker disabled={busy} media={media} onChange={onMediaChange} />
          </div>

          {isContest ? null : (
          <div className="space-y-3 rounded-md bg-canvas p-4">
            <label className="flex items-center gap-3 text-label text-ink">
              <input
                checked={values.pinned}
                className="size-5 accent-brand-600"
                disabled={busy}
                onChange={(event) => onChange('pinned', event.target.checked)}
                type="checkbox"
              />
              <Pin aria-hidden="true" className="size-4 text-accent-700" />
              Ghim đầu bảng tin
            </label>
            {values.pinned ? (
              <FormField
                className="mb-0"
                error={errors.pinnedUntil}
                hint={isCommentContest ? 'Để trống thì ghim tới khi đóng bình luận.' : undefined}
                htmlFor="moderator-post-pinned-until"
                label="Ghim đến"
              >
                <Input
                  disabled={busy}
                  id="moderator-post-pinned-until"
                  onChange={(event) => onChange('pinnedUntil', event.target.value)}
                  type="datetime-local"
                  value={values.pinnedUntil}
                />
              </FormField>
            ) : null}
          </div>
          )}

          {formError ? (
            <p className="text-body-sm text-danger" role="alert">
              {formError}
            </p>
          ) : null}

          <div className="flex flex-wrap justify-end gap-3">
            <Button disabled={busy} onClick={onCancel} variant="ghost">
              Huỷ
            </Button>
            <Button disabled={busy} type="submit">
              {busy ? 'Đang đăng…' : 'Đăng ngay'}
            </Button>
          </div>
        </form>
      </Card>
    </main>
  )
}
