import { ArrowLeftRight, Languages, Sparkles } from 'lucide-react'
import type { ReactNode } from 'react'

import type { ApiError } from '@/shared/api'
import {
  Button,
  Card,
  EmptyState,
  ErrorState,
  FormField,
  PageHeader,
  Select,
  Textarea,
} from '@/shared/ui'

import type { TranslateRequest, TranslateResult } from '../types'

export interface TranslationViewProps {
  draft: TranslateRequest
  pending: boolean
  result?: TranslateResult
  error?: ApiError
  validation?: string
  onTextChange: (text: string) => void
  onDirectionChange: (from: TranslateRequest['from']) => void
  onSubmit: () => void
  onOpenLexeme: (id: number) => void
  popup?: ReactNode
}

export function TranslationView({
  draft,
  pending,
  result,
  error,
  validation,
  onTextChange,
  onDirectionChange,
  onSubmit,
  onOpenLexeme,
  popup,
}: TranslationViewProps) {
  const count = Array.from(draft.text).length
  const invalid = !draft.text.trim() || count > 2000
  const inputError = validation ?? (error?.status === 422 ? error.message : undefined)
  const insufficient = error?.status === 402 && error.errorCode === 'INSUFFICIENT_CREDITS'
  const unavailable = error?.status === 503 && error.errorCode === 'TRANSLATE_UNAVAILABLE'
  return (
    <main className="mx-auto w-full min-w-0 max-w-reading space-y-6 px-4 py-6 pb-20 sm:px-6">
      <PageHeader title="Dịch câu" subtitle="Dịch giữa tiếng Trung và tiếng Việt" />

      <form
        aria-label="Dịch câu"
        aria-busy={pending}
        className="space-y-4 rounded-md border border-brand-100 bg-surface-subtle p-5 shadow-sm sm:p-6"
        noValidate
        onSubmit={(event) => {
          event.preventDefault()
          onSubmit()
        }}
      >
        <div className="flex flex-wrap items-end justify-between gap-3 rounded-md border border-line bg-surface p-3.5">
          <div className="flex flex-wrap items-end gap-3">
            <FormField className="mb-0" htmlFor="translation-direction" label="Hướng dịch">
              <Select
                value={draft.from}
                disabled={pending}
                onChange={(event) => onDirectionChange(event.target.value === 'VI' ? 'VI' : 'ZH')}
              >
                <option value="ZH">Tiếng Trung → Tiếng Việt</option>
                <option value="VI">Tiếng Việt → Tiếng Trung</option>
              </Select>
            </FormField>
            <Button
              disabled={pending}
              variant="outline"
              onClick={() => onDirectionChange(draft.to)}
            >
              <span className="inline-flex items-center gap-2">
                <ArrowLeftRight aria-hidden="true" className="size-4 shrink-0" />
                <span>Đổi hướng</span>
              </span>
            </Button>
          </div>
          <span className="inline-flex items-center gap-1.5 rounded-pill border border-brand-100 bg-brand-50 px-3 py-1 text-caption font-semibold text-brand-700">
            <Sparkles aria-hidden="true" className="size-3.5 shrink-0" />
            <span>Tách từ & Pinyin tự động</span>
          </span>
        </div>

        <div className="rounded-md border border-line bg-surface p-4">
          <FormField
            htmlFor="translation-text"
            label="Nội dung cần dịch"
            required
            hint={`${count}/2.000 ký tự`}
            error={inputError}
          >
            <Textarea
              value={draft.text}
              disabled={pending}
              rows={6}
              onChange={(event) => onTextChange(event.target.value)}
            />
          </FormField>

          <div className="mt-4 flex justify-end border-t border-line pt-3">
            <Button type="submit" disabled={invalid || pending} loading={pending}>
              <span className="inline-flex items-center gap-2">
                <Languages aria-hidden="true" className="size-4 shrink-0" />
                <span>{pending ? 'Đang dịch…' : 'Dịch'}</span>
              </span>
            </Button>
          </div>
        </div>
      </form>

      {insufficient ? (
        <div
          role="alert"
          className="space-y-3 rounded-md border border-warning bg-warning-bg p-5 text-ink"
        >
          <p className="font-medium text-warning">Bạn đã hết lượt dịch và không đủ điểm.</p>
          <Button to="/account/credits" variant="outline">
            Xem gói dịch vụ và điểm
          </Button>
        </div>
      ) : unavailable ? (
        <p
          role="alert"
          className="rounded-md border border-danger bg-danger-bg p-4 text-body text-danger"
        >
          Dịch tạm thời lỗi — lượt đã được hoàn
        </p>
      ) : error && error.status !== 422 ? (
        <ErrorState message={error.message} requestId={error.requestId} />
      ) : null}

      {pending ? (
        <p
          className="rounded-md border border-line bg-surface p-4 text-body-sm text-ink-secondary"
          role="status"
        >
          Đang dịch nội dung…
        </p>
      ) : null}

      {!pending && !result && !error ? (
        <EmptyState
          title="Chưa có bản dịch"
          description="Nhập nội dung và bấm Dịch để xem kết quả."
        />
      ) : null}

      {result ? (
        <section
          aria-label="Kết quả dịch"
          className="space-y-4 rounded-md border border-brand-100 bg-surface p-5 shadow-sm sm:p-6"
        >
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div className="flex items-center gap-2">
              <Languages aria-hidden="true" className="size-5 shrink-0 text-brand-700" />
              <h2 className="text-h3 text-ink">Bản dịch</h2>
            </div>
            {result.cached ? (
              <p
                className="rounded-pill border border-line bg-canvas px-3 py-0.5 text-caption text-ink-secondary"
                role="status"
              >
                Từ bộ nhớ đệm — không trừ lượt
              </p>
            ) : result.consumption === 'QUOTA' ? (
              <p
                className="rounded-pill border border-brand-100 bg-brand-50 px-3 py-0.5 text-caption font-medium text-brand-700"
                role="status"
              >
                Đã dùng 1 lượt miễn phí.
              </p>
            ) : result.consumption === 'CREDIT' ? (
              <p
                className="rounded-pill border border-brand-100 bg-brand-50 px-3 py-0.5 text-caption font-medium text-brand-700"
                role="status"
              >
                Đã dùng điểm.
              </p>
            ) : null}
          </div>

          <p
            className="whitespace-pre-wrap break-words rounded-md border border-brand-100 bg-surface-subtle p-4 text-body text-ink"
            lang={draft.to === 'ZH' ? 'zh-CN' : 'vi'}
          >
            {result.translated}
          </p>

          {result.pinyin ? (
            <Card className="space-y-1 bg-canvas p-4">
              <h3 className="text-label text-ink">Pinyin của nội dung tiếng Trung</h3>
              <p className="whitespace-pre-wrap break-words text-body font-medium text-brand-700">
                {result.pinyin}
              </p>
            </Card>
          ) : null}

          {result.segments.length ? (
            <Card className="space-y-2 border-brand-100 bg-surface-subtle p-4">
              <h3 className="text-label text-ink">
                {draft.from === 'ZH'
                  ? 'Tra từ trong nội dung tiếng Trung'
                  : 'Tra từ trong bản dịch tiếng Trung'}
              </h3>
              <p
                className="whitespace-pre-wrap break-words font-hanzi text-h3 leading-relaxed text-ink"
                lang="zh-CN"
              >
                {result.segments.map((segment, index) =>
                  segment.lexemeId != null ? (
                    <button
                      key={index}
                      type="button"
                      className="rounded-sm px-1 py-0.5 text-brand-700 underline decoration-brand-100 underline-offset-4 transition-colors hover:bg-surface focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-1"
                      aria-label={`Tra từ ${segment.text}`}
                      onClick={() => onOpenLexeme(segment.lexemeId!)}
                    >
                      {segment.text}
                    </button>
                  ) : (
                    <span key={index}>{segment.text}</span>
                  ),
                )}
              </p>
            </Card>
          ) : null}
        </section>
      ) : null}
      {popup}
    </main>
  )
}
