import { BookOpen, Headphones, Mic, PenLine, type LucideIcon } from 'lucide-react'
import { Link } from 'react-router-dom'

import type { PaidFeature, Wallet } from '@/features/credits/types'
import { Card } from '@/shared/ui'

type Activity = { feature?: PaidFeature; label: string; to: string }
type Skill = { activities: readonly Activity[]; hint: string; icon: LucideIcon; name: string }

/** HSK 1–3 tests listening, speaking, reading and writing, so the learning area is laid out by those four skills. */
const SKILLS: readonly Skill[] = [
  {
    activities: [{ feature: 'PRONUNCIATION', label: 'Nghe phân biệt âm', to: '/learn/pronunciation' }],
    hint: 'Nghe ra thanh mẫu, vận mẫu, thanh điệu',
    icon: Headphones,
    name: 'Nghe',
  },
  {
    activities: [{ feature: 'PRONUNCIATION', label: 'Luyện nói, giáo viên chấm', to: '/learn/pronunciation/speaking' }],
    hint: 'Đọc theo mẫu, giáo viên chấm phát âm',
    icon: Mic,
    name: 'Nói',
  },
  {
    activities: [
      { feature: 'VOCAB', label: 'Từ vựng theo chủ đề', to: '/learn/topics' },
      { feature: 'GRAMMAR', label: 'Ngữ pháp', to: '/learn/grammar' },
      { feature: 'HANZI', label: 'Nhận diện chữ Hán', to: '/learn/characters/recognition' },
    ],
    hint: 'Hiểu từ, câu và mặt chữ',
    icon: BookOpen,
    name: 'Đọc',
  },
  {
    activities: [
      { feature: 'HANZI', label: 'Tập viết chữ Hán', to: '/learn/characters/writing' },
      { label: 'Viết bài, giáo viên chấm', to: '/grading/new' },
    ],
    hint: 'Viết đúng nét, đặt câu thành đoạn',
    icon: PenLine,
    name: 'Viết',
  },
]

/** "Còn 2/3 lượt" for a free account; nothing for unlimited or unknown allowances. */
function remaining(wallet: Wallet | undefined, feature: PaidFeature | undefined): string | undefined {
  if (!wallet || !feature || wallet.plan.code !== 'FREE') return undefined
  const line = wallet.quota.find((item) => item.feature === feature)
  return line ? `Còn ${line.remaining}/${line.limit} lượt tháng này` : undefined
}

export function HskSkillsPanel({ wallet }: { wallet?: Wallet }) {
  return (
    <section aria-labelledby="hsk-skills-heading" className="space-y-3">
      <div className="flex flex-wrap items-end justify-between gap-2">
        <h2 className="text-h3 text-ink" id="hsk-skills-heading">
          4 kỹ năng HSK
        </h2>
        <p className="text-body-sm text-ink-secondary">Đề HSK 1–3 kiểm tra đủ nghe, nói, đọc, viết.</p>
      </div>
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {SKILLS.map(({ activities, hint, icon: Icon, name }) => (
          <Card className="flex min-w-0 flex-col gap-3 p-5" key={name}>
            <div className="flex items-center gap-3">
              <span className="flex size-11 shrink-0 items-center justify-center rounded-md bg-brand-50 text-brand-700">
                <Icon aria-hidden="true" className="size-6" />
              </span>
              <div className="min-w-0">
                <h3 className="text-h3 text-ink">{name}</h3>
                <p className="text-caption text-ink-secondary">{hint}</p>
              </div>
            </div>
            <ul className="space-y-1 border-t border-line pt-3">
              {activities.map((activity) => {
                const left = remaining(wallet, activity.feature)
                return (
                  <li key={activity.to}>
                    <Link
                      className="flex min-h-touch flex-col justify-center rounded-sm px-2 py-1 no-underline hover:bg-canvas focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
                      to={activity.to}
                    >
                      <span className="text-body-sm font-semibold text-ink">{activity.label}</span>
                      {left ? <span className="text-caption text-ink-muted">{left}</span> : null}
                    </Link>
                  </li>
                )
              })}
            </ul>
          </Card>
        ))}
      </div>
    </section>
  )
}
