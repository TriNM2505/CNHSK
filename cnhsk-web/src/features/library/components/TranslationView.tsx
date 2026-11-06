import type { ReactNode } from 'react'

import type { ApiError } from '@/shared/api'
import {
  Button,
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
    <div className="mx-auto max-w-reading space-y-6">
      <PageHeader title="Dịch câu" subtitle="Dịch giữa tiếng Trung và tiếng Việt" />
      <form
        aria-label="Dịch câu"
        aria-busy={pending}
        className="space-y-4"
        noValidate
        onSubmit={(event) => {
          event.preventDefault()
          onSubmit()
        }}
      >
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
        <div className="flex flex-wrap items-end gap-3">
          <FormField htmlFor="translation-direction" label="Hướng dịch">
            <Select
              value={draft.from}
              disabled={pending}
              onChange={(event) => onDirectionChange(event.target.value === 'VI' ? 'VI' : 'ZH')}
            >
              <option value="ZH">Tiếng Trung → Tiếng Việt</option>
              <option value="VI">Tiếng Việt → Tiếng Trung</option>
            </Select>
          </FormField>
          <Button disabled={pending} variant="outline" onClick={() => onDirectionChange(draft.to)}>
            Đổi hướng
          </Button>
          <Button type="submit" disabled={invalid || pending} loading={pending}>
            {pending ? 'Đang dịch…' : 'Dịch'}
          </Button>
        </div>
      </form>
      {insufficient ? (
        <div role="alert" className="space-y-3 rounded-md border border-line bg-surface p-4">
          <p>Bạn đã hết lượt dịch và không đủ điểm.</p>
          <Button to="/account/credits" variant="outline">
            Xem gói dịch vụ và điểm
          </Button>
        </div>
      ) : unavailable ? (
        <p role="alert" className="text-body text-danger">
          Dịch tạm thời lỗi — lượt đã được hoàn
        </p>
      ) : error && error.status !== 422 ? (
        <ErrorState message={error.message} requestId={error.requestId} />
      ) : null}
      {pending ? <p role="status">Đang dịch nội dung…</p> : null}
      {!pending && !result && !error ? (
        <EmptyState
          title="Chưa có bản dịch"
          description="Nhập nội dung và bấm Dịch để xem kết quả."
        />
      ) : null}
      {result ? (
        <section
          aria-label="Kết quả dịch"
          className="space-y-4 rounded-md border border-line bg-surface p-4 sm:p-6"
        >
          <h2 className="text-h3 text-ink">Bản dịch</h2>
          <p
            className="whitespace-pre-wrap text-body text-ink"
            lang={draft.to === 'ZH' ? 'zh-CN' : 'vi'}
          >
            {result.translated}
          </p>
          {result.pinyin ? (
            <div>
              <h3 className="text-label text-ink">Pinyin của nội dung tiếng Trung</h3>
              <p className="whitespace-pre-wrap text-body text-brand-700">{result.pinyin}</p>
            </div>
          ) : null}
          {result.segments.length ? (
            <div>
              <h3 className="text-label text-ink">
                {draft.from === 'ZH'
                  ? 'Tra từ trong nội dung tiếng Trung'
                  : 'Tra từ trong bản dịch tiếng Trung'}
              </h3>
              <p className="whitespace-pre-wrap font-hanzi text-h3 text-ink" lang="zh-CN">
                {result.segments.map((segment, index) =>
                  segment.lexemeId != null ? (
                    <button
                      key={index}
                      type="button"
                      className="rounded-sm text-brand-700 underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
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
            </div>
          ) : null}
          {result.cached ? (
            <p role="status">Từ bộ nhớ đệm — không trừ lượt</p>
          ) : result.consumption === 'QUOTA' ? (
            <p role="status">Đã dùng 1 lượt miễn phí.</p>
          ) : result.consumption === 'CREDIT' ? (
            <p role="status">Đã dùng điểm.</p>
          ) : null}
        </section>
      ) : null}
      {popup}
    </div>
  )
}
