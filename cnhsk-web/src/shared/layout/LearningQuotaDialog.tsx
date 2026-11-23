import { Crown, X } from 'lucide-react'
import { useEffect, useState } from 'react'

import { LEARNING_QUOTA_EVENT } from '@/shared/api'
import { Button } from '@/shared/ui'

const FEATURE_NAMES: Record<string, string> = {
  GRAMMAR: 'Ngữ pháp',
  HANZI: 'Chữ Hán',
  PRONUNCIATION: 'Phát âm',
  VOCAB: 'Từ vựng',
}

type QuotaDetail = { feature?: unknown; limit?: unknown }

/** Explains an exhausted free learning allowance wherever it happens, with the way to more lessons. */
export function LearningQuotaDialog() {
  const [detail, setDetail] = useState<QuotaDetail>()

  useEffect(() => {
    function show(event: Event) {
      setDetail((event as CustomEvent<QuotaDetail>).detail ?? {})
    }
    window.addEventListener(LEARNING_QUOTA_EVENT, show)
    return () => window.removeEventListener(LEARNING_QUOTA_EVENT, show)
  }, [])

  useEffect(() => {
    if (!detail) return
    function closeWithEscape(event: KeyboardEvent) {
      if (event.key === 'Escape') setDetail(undefined)
    }
    document.addEventListener('keydown', closeWithEscape)
    return () => document.removeEventListener('keydown', closeWithEscape)
  }, [detail])

  if (!detail) return null
  const feature = typeof detail.feature === 'string' ? FEATURE_NAMES[detail.feature] : undefined
  const limit = typeof detail.limit === 'number' ? detail.limit : undefined

  return (
    <div className="fixed inset-0 z-overlay flex items-end justify-center bg-ink/50 sm:items-center sm:p-6">
      <button aria-label="Đóng" className="absolute inset-0 cursor-default" onClick={() => setDetail(undefined)} tabIndex={-1} type="button" />
      <section
        aria-labelledby="learning-quota-title"
        aria-modal="true"
        className="relative w-full min-w-0 space-y-4 bg-surface p-6 text-center shadow-overlay sm:max-w-md sm:rounded-lg"
        role="dialog"
      >
        <button
          aria-label="Đóng"
          className="absolute right-3 top-3 flex size-9 items-center justify-center rounded-full text-ink-secondary hover:bg-canvas focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
          onClick={() => setDetail(undefined)}
          type="button"
        >
          <X aria-hidden="true" className="size-5" />
        </button>
        <span className="mx-auto flex size-14 items-center justify-center rounded-full bg-warning-bg">
          <Crown aria-hidden="true" className="size-7 fill-accent-500 text-accent-700" />
        </span>
        <h2 className="text-h3 text-ink" id="learning-quota-title">
          Hết lượt học tháng này
        </h2>
        <p className="text-body text-ink-secondary">
          {feature
            ? `Bạn đã dùng hết ${limit === undefined ? '' : `${limit} `}lượt ${feature} miễn phí của tháng.`
            : 'Bạn đã dùng hết lượt học miễn phí của tháng.'}{' '}
          Nâng cấp Premium để học tiếp không bị gián đoạn, hoặc quay lại vào tháng sau.
        </p>
        <div className="flex flex-wrap justify-center gap-3">
          <Button onClick={() => setDetail(undefined)} variant="outline">
            Để sau
          </Button>
          <Button onClick={() => setDetail(undefined)} to="/account/credits">
            Nâng cấp Premium
          </Button>
        </div>
      </section>
    </div>
  )
}
