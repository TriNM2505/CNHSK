import { Medal } from 'lucide-react'
import { cn } from '@/shared/lib/cn'
import { Card, HanziText } from '@/shared/ui'
import { quizDuration, type TopicRankEntry } from '../types'

export function QuizResultText({ text }: { text: string }) {
  return text.split(/([\p{Script=Han}\u3000-\u303f\uff00-\uffef]+)/u).map((part, index) =>
    /[\p{Script=Han}\u3000-\u303f\uff00-\uffef]/u.test(part) ? (
      <HanziText key={index} size="sm" className="quiz-result-text">
        {part}
      </HanziText>
    ) : (
      part
    ),
  )
}

export function QuizRankingList({ entries }: { entries: readonly TopicRankEntry[] }) {
  return (
    <ol className="grid min-w-0 gap-3 sm:grid-cols-2">
      {entries.slice(0, 10).map((entry) => (
        <li key={entry.userId} className="min-w-0">
          <Card
            className={cn(
              'flex h-full min-w-0 flex-wrap items-center justify-between gap-3 p-4',
              entry.rank <= 3 ? 'border-brand-100 bg-brand-50' : 'border-line',
            )}
          >
            <div className="flex min-w-0 items-start gap-2">
              {entry.rank <= 3 ? (
                <Medal aria-hidden="true" className="size-5 shrink-0 text-accent-700" />
              ) : null}
              <p className="min-w-0 text-label text-ink">
                #{entry.rank} · <QuizResultText text={entry.displayName} />
              </p>
            </div>
            <p className="text-body-sm tabular-nums text-ink-secondary">
              {entry.score} điểm
              {entry.durationMs === null ? '' : ` · ${quizDuration(entry.durationMs)}`}
            </p>
          </Card>
        </li>
      ))}
    </ol>
  )
}
