import { CheckCircle2, XCircle } from 'lucide-react'
import type { ReactNode } from 'react'

import type { FinishResult } from '@/features/practice/types'
import { Card, StatTile } from '@/shared/ui'

export interface SessionSummaryProps {
  actions: ReactNode
  result: FinishResult
  title?: string
}

export function SessionSummary({
  actions,
  result,
  title = 'Kết quả lượt luyện',
}: SessionSummaryProps) {
  const correctCount = result.items.filter((item) => item.correct === true).length
  const wrongItems = result.items.filter((item) => item.correct === false)
  const scoreText =
    result.score === null || result.score === undefined
      ? 'Không chấm điểm'
      : result.maxScore === null || result.maxScore === undefined
        ? String(result.score)
        : `${result.score}/${result.maxScore}`

  return (
    <section aria-labelledby="practice-summary-title" className="space-y-5">
      <div className="text-center">
        <CheckCircle2 aria-hidden="true" className="mx-auto size-10 text-success" />
        <h2 className="mt-3 text-h2 text-ink" id="practice-summary-title">
          {title}
        </h2>
      </div>

      <div className="grid gap-3 sm:grid-cols-3">
        <StatTile label="Điểm" value={scoreText} />
        <StatTile label="Câu đúng" value={correctCount} />
        <StatTile label="Câu sai" value={wrongItems.length} />
      </div>

      {wrongItems.length ? (
        <Card>
          <h3 className="flex items-center gap-2 text-h3 text-ink">
            <XCircle aria-hidden="true" className="size-5 text-danger" />
            Câu cần xem lại
          </h3>
          <ul className="mt-4 divide-y divide-line">
            {wrongItems.map((item, index) => (
              <li className="py-3 text-body-sm" key={`${item.no ?? 'wrong'}-${index}`}>
                <p className="font-semibold text-ink">Câu {item.no ?? index + 1}</p>
                {item.yourAnswer ? (
                  <p className="mt-1 text-ink-secondary">Bạn trả lời: {item.yourAnswer}</p>
                ) : null}
                {item.correctAnswer ? (
                  <p className="mt-1 text-ink">Đáp án đúng: {item.correctAnswer}</p>
                ) : null}
              </li>
            ))}
          </ul>
        </Card>
      ) : null}

      <div className="flex flex-wrap justify-center gap-3">{actions}</div>
    </section>
  )
}
