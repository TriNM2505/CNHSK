import { ArrowLeft } from 'lucide-react'
import type { ReactNode } from 'react'
import { Button, Card as CardPanel, PageBanner } from '@/shared/ui'
import { flashcardDate } from '../flashcardForms'
import type { Card } from '../types'
import './DeckDetailView.css'

export function DeckDetailText({ text }: { text: string }) {
  return <span className={/\p{Script=Han}/u.test(text) ? 'font-hanzi' : undefined}>{text}</span>
}

export function DeckDetailBanner({
  name,
  actions,
  backTo = '/library/decks',
}: {
  name?: string
  actions?: ReactNode
  backTo?: string
}) {
  return (
    <PageBanner
      title={<DeckDetailText text={name ?? 'Chi tiết bộ thẻ'} />}
      subtitle="Mỗi lần ôn, thêm một điều được ghi nhớ."
      actions={
        <div className="flex flex-wrap gap-2">
          <Button to={backTo} variant="outline">
            <ArrowLeft aria-hidden="true" className="mr-2 inline-block size-4 align-middle" />
            Về danh sách bộ thẻ
          </Button>
          {actions}
        </div>
      }
    />
  )
}

export function DeckCardContent({
  card,
  showSchedule = true,
}: {
  card: Card
  showSchedule?: boolean
}) {
  return (
    <CardPanel className="min-w-0 space-y-3 border-brand-100 p-4 shadow-sm">
      <p className="whitespace-pre-wrap break-words text-h3 text-ink">
        <DeckDetailText text={card.front} />
      </p>
      <p className="whitespace-pre-wrap break-words text-body text-ink-secondary">
        <DeckDetailText text={card.back} />
      </p>
      {showSchedule ? (
        <p className="text-caption text-ink-secondary">
          {card.dueAt ? (
            <>
              Hạn ôn: <time dateTime={card.dueAt}>{flashcardDate(card.dueAt)}</time>
            </>
          ) : (
            'Thẻ mới'
          )}{' '}
          · {card.reviewCount} lượt ôn · {card.lapseCount} lần quên
        </p>
      ) : null}
    </CardPanel>
  )
}
