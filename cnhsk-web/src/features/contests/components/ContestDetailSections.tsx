import { CalendarDays, FileText, Hourglass, Layers, Trophy, type LucideIcon } from 'lucide-react'
import type { ReactNode } from 'react'

import { Card } from '@/shared/ui'

import prizePanda from '../assets/contest-prizes-panda.webp'
import { formatVietnamDate } from '../countdown'
import type { ContestDetail } from '../types'
import { ContestBambooArt, ContestPrizeBackdrop, ContestPrizeMedal } from './ContestDetailArt'
import { ScheduleBadge } from './ContestListView'

function DetailFact({
  icon: Icon,
  label,
  children,
}: {
  icon: LucideIcon
  label: string
  children: ReactNode
}) {
  return (
    <div className="flex min-w-0 items-start gap-3">
      <Icon aria-hidden="true" className="mt-1 size-6 shrink-0 text-brand-600" />
      <div className="min-w-0">
        <dt className="text-ink-secondary">{label}</dt>
        <dd className="break-words font-bold text-ink">{children}</dd>
      </div>
    </div>
  )
}

export function ContestOverviewCard({ contest }: { contest: ContestDetail }) {
  return (
    <Card className="order-1 min-w-0 space-y-3 break-words rounded-lg p-4 sm:p-5 lg:col-span-2 lg:self-stretch">
      <ScheduleBadge state={contest.state} />
      {contest.description ? (
        <p className="whitespace-pre-wrap text-body-sm text-ink">{contest.description}</p>
      ) : null}
      <dl className="relative isolate grid min-w-0 gap-4 overflow-hidden rounded-lg bg-brand-50/80 p-4 text-body-sm sm:grid-cols-2 sm:gap-5">
        <ContestBambooArt position="bottom" />
        <div className="relative z-nav min-w-0 space-y-4 sm:border-r sm:border-brand-100 sm:pr-5">
          <DetailFact icon={CalendarDays} label="Bắt đầu · giờ Việt Nam">
            {formatVietnamDate(contest.startsAt)}
          </DetailFact>
          <DetailFact icon={FileText} label="Đề thi">
            {contest.examTitle}
          </DetailFact>
          <DetailFact icon={Hourglass} label="Thời lượng">
            {contest.durationMinutes > 0
              ? contest.durationMinutes + ' phút, tối đa đến giờ kết thúc'
              : 'Đến giờ kết thúc cuộc thi'}
          </DetailFact>
        </div>
        <div className="relative z-nav min-w-0 space-y-4 border-t border-brand-100 pt-4 sm:border-t-0 sm:pt-0">
          <DetailFact icon={CalendarDays} label="Kết thúc · giờ Việt Nam">
            {formatVietnamDate(contest.endsAt)}
          </DetailFact>
          <DetailFact icon={Layers} label="Số câu">
            {contest.questionCount} câu
          </DetailFact>
        </div>
      </dl>
    </Card>
  )
}

export function ContestPrizesCard({ contest }: { contest: ContestDetail }) {
  return (
    <Card className="relative isolate order-3 min-w-0 overflow-hidden rounded-lg border-brand-100 bg-gradient-to-br from-brand-50 via-surface to-brand-50 p-4 sm:p-5 lg:col-span-2">
      <ContestPrizeBackdrop />
      <div className="relative z-nav space-y-3">
        <h2 className="flex items-center gap-2.5 text-h2 font-bold text-ink">
          <Trophy aria-hidden="true" className="size-7 shrink-0 text-brand-700" />
          Giải thưởng
        </h2>
        {contest.prizes.length ? (
          <div className="grid min-w-0 items-end gap-3 sm:grid-cols-3">
            <ul className="min-w-0 space-y-3 sm:col-span-2">
              {contest.prizes.map((prize, index) => (
                <li
                  key={index}
                  className="flex min-w-0 items-center gap-3 rounded-md border border-brand-100 bg-surface/95 px-3 py-3"
                >
                  <ContestPrizeMedal firstPlace={prize.fromRank === 1} />
                  <div className="min-w-0 space-y-0.5">
                    <p className="break-words text-h3 font-bold text-brand-700">
                      Hạng {prize.fromRank}
                      {prize.toRank !== prize.fromRank ? '–' + prize.toRank : ''}
                    </p>
                    <p className="break-words text-body-sm text-ink">{prize.description}</p>
                  </div>
                </li>
              ))}
            </ul>
            <div className="pointer-events-none relative mx-auto w-48 max-w-full sm:w-full">
              <img
                alt=""
                aria-hidden="true"
                className="block h-auto w-full object-contain"
                decoding="async"
                height="194"
                src={prizePanda}
                width="284"
              />
            </div>
          </div>
        ) : (
          <p className="text-body-sm text-ink-secondary">Chưa có thông tin giải thưởng.</p>
        )}
      </div>
    </Card>
  )
}
