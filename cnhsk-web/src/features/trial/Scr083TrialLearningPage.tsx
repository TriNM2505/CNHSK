import { BookOpen, Headphones, Languages, Mic, PenTool, Sparkles } from 'lucide-react'
import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'

import SpeakingPracticePage from '@/features/pronunciation/pages/SpeakingPracticePage'
import { useCurrentUser } from '@/shared/auth'
import { cn } from '@/shared/lib/cn'
import { Button, SUPPORTED_HSK_LEVELS } from '@/shared/ui'

import type { TrialFeature } from './api'
import { TrialQuiz } from './TrialQuiz'

type Part = TrialFeature | 'SPEAKING'

const PARTS: readonly { icon: typeof BookOpen; label: string; value: Part }[] = [
  { icon: BookOpen, label: 'Từ vựng', value: 'VOCAB' },
  { icon: Languages, label: 'Ngữ pháp', value: 'GRAMMAR' },
  { icon: Headphones, label: 'Nghe âm', value: 'LISTENING' },
  { icon: PenTool, label: 'Chữ Hán', value: 'HANZI' },
  { icon: Mic, label: 'Luyện nói', value: 'SPEAKING' },
]

/** Where a page of the learning area lands a visitor, so they can come back to it after signing up. */
function partFor(from: string | null): Part {
  if (!from) return 'VOCAB'
  if (from.startsWith('/learn/grammar')) return 'GRAMMAR'
  if (from.startsWith('/learn/speaking')) return 'SPEAKING'
  if (from.startsWith('/learn/listening')) return 'LISTENING'
  if (from.startsWith('/learn/characters')) return 'HANZI'
  return 'VOCAB'
}

/** SCR-083: learning without an account. Every answer is marked on the spot and nothing is saved. */
export default function Scr083TrialLearningPage() {
  const [search] = useSearchParams()
  const { user } = useCurrentUser()
  const from = search.get('from')
  const [part, setPart] = useState<Part>(() => partFor(from))
  const levels: number[] = [...SUPPORTED_HSK_LEVELS]
  const [level, setLevel] = useState<number>(levels[0] ?? 1)
  const back = from && from.startsWith('/learn') ? from : '/learn'
  const signUpTo = `/register?redirect=${encodeURIComponent(back)}`

  return (
    <main className="mx-auto w-full min-w-0 max-w-content space-y-6 px-4 py-6 pb-16 sm:px-6">
      <section className="flex flex-wrap items-center justify-between gap-4 rounded-lg bg-brand-50 p-5 sm:p-6">
        <div className="min-w-0 space-y-1">
          <p className="flex items-center gap-2 text-caption font-semibold uppercase tracking-wide text-brand-700">
            <Sparkles aria-hidden="true" className="size-4" />
            Học thử miễn phí
          </p>
          <h1 className="text-h1-sm text-ink">Thử ngay, không cần tài khoản</h1>
          <p className="text-body text-ink-secondary">
            Làm thử từng phần học. Kết quả không được lưu; đăng ký để lưu tiến độ và học theo lộ
            trình.
          </p>
        </div>
        {user ? (
          <Button to="/learn">Vào trang học</Button>
        ) : (
          <div className="flex flex-wrap gap-2">
            <Button to={`/login?redirect=${encodeURIComponent(back)}`} variant="outline">
              Đăng nhập
            </Button>
            <Button to={signUpTo}>Đăng ký miễn phí</Button>
          </div>
        )}
      </section>

      <div className="flex flex-wrap items-center justify-between gap-3">
        <div aria-label="Phần học thử" className="flex flex-wrap gap-2" role="group">
          {PARTS.map(({ icon: Icon, label, value }) => (
            <button
              aria-pressed={part === value}
              className={cn(
                'inline-flex min-h-10 items-center gap-2 rounded-pill border px-4 text-body-sm font-semibold focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus',
                part === value
                  ? 'border-brand-600 bg-brand-600 text-surface'
                  : 'border-line bg-surface text-ink-secondary hover:text-ink',
              )}
              key={value}
              onClick={() => setPart(value)}
              type="button"
            >
              <Icon aria-hidden="true" className="size-4" />
              {label}
            </button>
          ))}
        </div>
        {part === 'SPEAKING' ? null : (
          <select
            aria-label="Cấp HSK"
            className="min-h-10 rounded-md border border-line bg-surface px-3 text-body-sm text-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
            onChange={(event) => setLevel(Number(event.target.value))}
            value={level}
          >
            {levels.map((value) => (
              <option key={value} value={value}>
                HSK {value}
              </option>
            ))}
          </select>
        )}
      </div>

      {part === 'SPEAKING' ? (
        <SpeakingPracticePage guest />
      ) : (
        <TrialQuiz feature={part} level={level} signUpTo={signUpTo} />
      )}
    </main>
  )
}
