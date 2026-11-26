import { Link } from 'react-router-dom'

import type { PaidFeature, Wallet } from '@/features/credits/types'

import type { MasteryGroup, SkillMastery } from '../api/mastery'

type Activity = { feature?: PaidFeature; label: string; to: string }
type Skill = {
  activities: readonly Activity[]
  code: string
  /** The skill's own character: 听 listen, 说 speak, 读 read, 写 write. */
  glyph: string
  hint: string
  name: string
}

const DOMAIN_NAMES: Readonly<Record<string, string>> = {
  NA: 'Ngữ âm',
  CH: 'Chữ Hán',
  TV: 'Từ vựng',
  NP: 'Ngữ pháp',
  NL: 'Giao tiếp',
  VB: 'Văn bản',
}

/** HSK 1–3 tests listening, speaking, reading and writing, so the learning area is laid out by those four skills. */
const SKILLS: readonly Skill[] = [
  {
    activities: [{ feature: 'PRONUNCIATION', label: 'Nghe phân biệt âm', to: '/learn/listening' }],
    code: 'LISTENING',
    hint: 'Nghe ra thanh mẫu, vận mẫu, thanh điệu',
    glyph: '听',
    name: 'Nghe',
  },
  {
    activities: [
      { feature: 'PRONUNCIATION', label: 'Luyện nói, giáo viên chấm', to: '/learn/speaking' },
    ],
    code: 'SPEAKING',
    hint: 'Đọc theo mẫu và nhiệm vụ nói, giáo viên chấm',
    glyph: '说',
    name: 'Nói',
  },
  {
    activities: [
      { feature: 'VOCAB', label: 'Từ vựng theo chủ đề', to: '/learn/topics' },
      { feature: 'GRAMMAR', label: 'Ngữ pháp', to: '/learn/grammar' },
      { feature: 'HANZI', label: 'Nhận diện chữ Hán', to: '/learn/characters/recognition' },
    ],
    code: 'READING',
    hint: 'Hiểu từ, câu và mặt chữ',
    glyph: '读',
    name: 'Đọc',
  },
  {
    activities: [
      { feature: 'HANZI', label: 'Tập viết chữ Hán', to: '/learn/characters/writing' },
      { label: 'Nhiệm vụ viết, giáo viên chấm', to: '/learn/writing' },
    ],
    code: 'WRITING',
    hint: 'Viết đúng nét, đặt câu thành đoạn',
    glyph: '写',
    name: 'Viết',
  },
]

/** "Còn 2/3 lượt" for a free account; nothing for unlimited or unknown allowances. */
function remaining(
  wallet: Wallet | undefined,
  feature: PaidFeature | undefined,
): string | undefined {
  if (!wallet || !feature || wallet.plan.code !== 'FREE') return undefined
  const line = wallet.quota.find((item) => item.feature === feature)
  return line ? `Còn ${line.remaining}/${line.limit} lượt tháng này` : undefined
}

/** "14/22 nút tốt" and the average mastery of the nodes that have evidence; grey nodes are only counted. */
function SkillProgress({ group }: { group?: MasteryGroup }) {
  if (!group || group.nodes === 0) return null
  const percent = group.percent ?? 0
  return (
    <div className="space-y-1.5">
      <div className="flex items-baseline justify-between gap-2 text-caption">
        <span className="font-semibold text-ink">
          {group.good}/{group.nodes} nút tốt
        </span>
        <span className="tabular-nums text-ink-secondary">
          {group.percent === null ? 'Chưa có bài' : `${percent}%`}
        </span>
      </div>
      <div
        aria-label={`Mức nắm vững ${percent}%`}
        aria-valuemax={100}
        aria-valuemin={0}
        aria-valuenow={percent}
        className="h-1.5 overflow-hidden rounded-pill bg-brand-50"
        role="progressbar"
      >
        <div className="h-full rounded-pill bg-brand-600" style={{ width: `${percent}%` }} />
      </div>
      {group.missing > 0 ? (
        <p className="text-caption text-ink-muted">{group.missing} nút cần làm thêm bài</p>
      ) : null}
    </div>
  )
}

export function HskSkillsPanel({ mastery, wallet }: { mastery?: SkillMastery; wallet?: Wallet }) {
  const skillGroups = new Map(mastery?.skills.map((group) => [group.code, group]))
  return (
    <section
      aria-labelledby="hsk-skills-heading"
      className="learn-rise space-y-3"
      style={{ animationDelay: '80ms' }}
    >
      <div className="flex flex-wrap items-end justify-between gap-2">
        <h2 className="text-h3 text-ink" id="hsk-skills-heading">
          4 kỹ năng HSK
        </h2>
        <p className="text-body-sm text-ink-secondary">
          Chuẩn HSK 3.0 cấp 1–3 yêu cầu đủ nghe, nói, đọc, viết.
        </p>
      </div>
      <div className="grid min-w-0 overflow-hidden rounded-lg border border-line bg-surface sm:grid-cols-2 xl:grid-cols-4">
        {SKILLS.map(({ activities, code, glyph, hint, name }, index) => (
          <article
            className={`flex min-w-0 flex-col gap-4 p-5 ${index > 0 ? 'border-t border-line' : ''} ${
              index % 2 === 1 ? 'sm:border-l' : ''
            } ${index === 1 ? 'sm:border-t-0' : ''} ${index >= 2 ? 'xl:border-t-0 xl:border-l' : ''}`}
            key={name}
          >
            <div className="flex items-end gap-3">
              <span aria-hidden="true" className="learn-skill-glyph">
                {glyph}
              </span>
              <div className="min-w-0 pb-0.5">
                <h3 className="text-h3 text-ink">{name}</h3>
                <p className="text-caption text-ink-secondary">{hint}</p>
              </div>
            </div>
            <SkillProgress group={skillGroups.get(code)} />
            <ul className="mt-auto space-y-0.5 border-t border-dashed border-line pt-3">
              {activities.map((activity) => {
                const left = remaining(wallet, activity.feature)
                return (
                  <li key={activity.to}>
                    <Link
                      className="group flex min-h-touch items-center justify-between gap-2 rounded-sm px-2 py-1 no-underline hover:bg-surface-subtle focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
                      to={activity.to}
                    >
                      <span className="flex min-w-0 flex-col">
                        <span className="text-body-sm font-semibold text-ink group-hover:text-brand-700">
                          {activity.label}
                        </span>
                        {left ? <span className="text-caption text-ink-muted">{left}</span> : null}
                      </span>
                      <span
                        aria-hidden="true"
                        className="text-brand-600 transition-transform motion-safe:group-hover:translate-x-0.5"
                      >
                        →
                      </span>
                    </Link>
                  </li>
                )
              })}
            </ul>
          </article>
        ))}
      </div>
      {mastery && mastery.domains.some((group) => group.nodes > 0) ? (
        <p className="text-body-sm text-ink-secondary">
          Theo miền đến HSK {mastery.hskLevel}:{' '}
          {mastery.domains
            .filter((group) => group.nodes > 0)
            .map(
              (group) => `${DOMAIN_NAMES[group.code] ?? group.code} ${group.good}/${group.nodes}`,
            )
            .join(' · ')}{' '}
          nút ở mức tốt.
        </p>
      ) : null}
    </section>
  )
}
