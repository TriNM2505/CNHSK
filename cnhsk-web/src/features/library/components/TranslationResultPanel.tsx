import { Languages, LogIn, Search } from 'lucide-react'

import type { ApiError } from '@/shared/api'
import { Button, ErrorState, Skeleton } from '@/shared/ui'

import type { Segment, TranslateResult, TranslationLanguage } from '../types'

export type TranslationPanelState =
  | { state: 'pending' }
  | { state: 'ready'; result: TranslateResult }
  | { state: 'error'; error: ApiError; onRetry: () => void }
  /** Guests look words up but must sign in before the AI translates. */
  | { state: 'login'; loginTo: string; registerTo: string }
  /** Staff accounts read the dictionary; AI translation is a learner allowance. */
  | { state: 'learnerOnly' }

/** The AI translation inside the result panel of the "Dịch" screen. */
export function TranslationOutput({ to, panel }: { to: TranslationLanguage; panel: TranslationPanelState }) {
  if (panel.state === 'login') {
    return (
      <div className="space-y-3">
        <p className="flex items-center gap-2 text-body text-ink">
          <Languages aria-hidden="true" className="size-5 shrink-0 text-brand-600" />
          Đăng nhập để dịch cụm từ và câu bằng AI.
        </p>
        <div className="flex flex-wrap gap-3">
          <Button to={panel.loginTo}>
            <LogIn aria-hidden="true" className="mr-2 inline-block size-4 align-middle" />
            Đăng nhập
          </Button>
          <Button to={panel.registerTo} variant="outline">
            Tạo tài khoản
          </Button>
        </div>
      </div>
    )
  }
  if (panel.state === 'learnerOnly') {
    return (
      <p className="text-body text-ink" role="status">
        Dịch cụm từ và câu bằng AI chỉ dành cho tài khoản học viên.
      </p>
    )
  }
  if (panel.state === 'pending') {
    return (
      <div className="space-y-3">
        <p className="text-body-sm text-ink-secondary" role="status">
          Đang dịch nội dung…
        </p>
        <div aria-hidden="true" className="space-y-3">
          <Skeleton className="w-full" />
          <Skeleton className="w-3/5" />
        </div>
      </div>
    )
  }
  if (panel.state === 'error') {
    const { error } = panel
    if (error.status === 402 && error.errorCode === 'INSUFFICIENT_CREDITS') {
      return (
        <div className="space-y-3" role="alert">
          <p className="text-body text-ink">Bạn đã dùng hết lượt dịch miễn phí tháng này.</p>
          <Button to="/account/credits" variant="outline">
            Nâng cấp PREMIUM
          </Button>
        </div>
      )
    }
    if (error.status === 503 && error.errorCode === 'TRANSLATE_UNAVAILABLE') {
      return (
        <ErrorState
          message="Lượt đã được hoàn. Vui lòng thử lại."
          onRetry={panel.onRetry}
          requestId={error.requestId}
          title="Dịch tạm thời lỗi"
        />
      )
    }
    return <ErrorState message={error.message} onRetry={panel.onRetry} requestId={error.requestId} />
  }
  const { result } = panel
  return (
    <section aria-label="Kết quả dịch" className="space-y-4">
      <p
        className={
          to === 'ZH'
            ? 'whitespace-pre-wrap break-words font-hanzi text-h2 text-ink'
            : 'whitespace-pre-wrap break-words text-h3 text-ink'
        }
        lang={to === 'ZH' ? 'zh-CN' : 'vi'}
      >
        {result.translated}
      </p>
      {result.pinyin ? (
        <p className="whitespace-pre-wrap break-words text-body text-brand-700" title="Pinyin của nội dung tiếng Trung">
          {result.pinyin}
        </p>
      ) : null}
    </section>
  )
}

/** Dictionary words of a translated sentence; picking one opens its entry right below. */
export function SentenceWords({
  segments,
  selectedId,
  onOpen,
}: {
  segments: readonly Segment[]
  selectedId?: number
  onOpen: (id: number) => void
}) {
  const words = segments.filter((segment) => segment.lexemeId != null)
  if (!words.length) return null
  return (
    <section aria-label="Từ trong câu" className="space-y-3">
      <h2 className="inline-flex items-center gap-2 text-h3 text-ink">
        <Search aria-hidden="true" className="size-5 text-brand-600" />
        Từ trong câu
      </h2>
      <ul className="flex flex-wrap gap-2">
        {words.map((segment, index) => (
          <li key={`${segment.lexemeId}-${index}`}>
            <button
              aria-label={`Tra từ ${segment.text}`}
              aria-pressed={selectedId === segment.lexemeId}
              className={`inline-flex min-h-touch items-center gap-2 rounded-pill border px-4 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus ${
                selectedId === segment.lexemeId
                  ? 'border-brand-600 bg-brand-50 text-brand-800'
                  : 'border-line bg-surface text-ink hover:border-brand-600'
              }`}
              onClick={() => onOpen(segment.lexemeId!)}
              type="button"
            >
              <span className="font-hanzi text-h3" lang="zh-CN">
                {segment.text}
              </span>
              {segment.pinyin ? <span className="text-body-sm text-brand-700">{segment.pinyin}</span> : null}
            </button>
          </li>
        ))}
      </ul>
    </section>
  )
}
