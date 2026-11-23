import { Megaphone, Pin, Trophy } from 'lucide-react'
import type { FormEvent } from 'react'

import type { ContestSummary } from '@/features/contests/types'
import { cn } from '@/shared/lib/cn'
import { Button, Card, FormField, Input, PageHeader, Select, Textarea } from '@/shared/ui'

import { POST_CATEGORIES, POST_CATEGORY_LABELS } from '../labels'
import type { PostCategory } from '../types'

export type ModeratorPostValues = {
  category: PostCategory
  contestId: string
  title: string
  content: string
  pinned: boolean
  /** Local date-time as typed in the browser, `YYYY-MM-DDTHH:mm`. */
  pinnedUntil: string
}

export type ModeratorPostErrors = Partial<Record<keyof ModeratorPostValues, string>>

export type ModeratorPostComposerViewProps = {
  busy: boolean
  contests: readonly ContestSummary[]
  errors: ModeratorPostErrors
  formError?: string
  onCancel: () => void
  onChange: <K extends keyof ModeratorPostValues>(field: K, value: ModeratorPostValues[K]) => void
  onSubmit: () => void
  values: ModeratorPostValues
}

const KINDS: readonly { category: PostCategory; hint: string; icon: typeof Megaphone; label: string }[] = [
  { category: 'ANNOUNCEMENT', hint: 'Tin chung cho cả cộng đồng', icon: Megaphone, label: 'Thông báo' },
  { category: 'CONTEST', hint: 'Hướng dẫn kèm nút Vào thi', icon: Trophy, label: 'Cuộc thi' },
]

/** SCR-080: a moderator writes a post that goes live at once, optionally pinned and tied to a contest. */
export function ModeratorPostComposerView({
  busy,
  contests,
  errors,
  formError,
  onCancel,
  onChange,
  onSubmit,
  values,
}: ModeratorPostComposerViewProps) {
  const isContest = values.category === 'CONTEST'
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
            <div className="grid gap-2 sm:grid-cols-2">
              {KINDS.map(({ category, hint, icon: Icon, label }) => {
                const selected = values.category === category
                return (
                  <button
                    aria-pressed={selected}
                    className={cn(
                      'flex min-h-touch items-center gap-3 rounded-md border p-3 text-left transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus',
                      selected ? 'border-brand-600 bg-brand-50' : 'border-line bg-surface hover:border-brand-600',
                    )}
                    disabled={busy}
                    key={category}
                    onClick={() => onChange('category', category)}
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
                onChange={(event) => onChange('category', event.target.value as PostCategory)}
                value={KINDS.some((kind) => kind.category === values.category) ? '' : values.category}
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

          {isContest ? (
            <FormField error={errors.contestId} htmlFor="moderator-post-contest" label="Cuộc thi" required>
              <Select
                disabled={busy}
                id="moderator-post-contest"
                onChange={(event) => onChange('contestId', event.target.value)}
                value={values.contestId}
              >
                <option value="">Chọn cuộc thi đã công bố</option>
                {contests.map((contest) => (
                  <option key={contest.id} value={String(contest.id)}>
                    {contest.name} · {contest.state === 'ONGOING' ? 'đang diễn ra' : 'sắp diễn ra'}
                  </option>
                ))}
              </Select>
            </FormField>
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
            label={isContest ? 'Hướng dẫn cuộc thi' : 'Nội dung'}
            required
          >
            <Textarea
              disabled={busy}
              id="moderator-post-content"
              onChange={(event) => onChange('content', event.target.value)}
              placeholder={
                isContest ? 'Thời gian thi, trò chơi, cách tính điểm, giải thưởng…' : 'Nội dung thông báo'
              }
              rows={10}
              value={values.content}
            />
          </FormField>

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
                hint={isContest ? 'Để trống thì ghim tới khi cuộc thi kết thúc.' : undefined}
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
