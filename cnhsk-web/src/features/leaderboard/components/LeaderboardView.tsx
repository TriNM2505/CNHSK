import { useState } from 'react'

import type { ApiError } from '@/shared/api'
import { Button, Card, FormField, PageHeader, Select, Skeleton } from '@/shared/ui'

import { formatDuration, formatPeriodRange } from '../formatters'
import { LEADERBOARD_PERIODS } from '../query'
import type { GameOption, Leaderboard, LeaderboardPeriod, TopicOption } from '../types'

type Props = {
  kind: 'topic' | 'game'
  title: string
  topics?: readonly TopicOption[]
  games?: readonly GameOption[]
  selectedId?: string
  hsk?: number
  period: LeaderboardPeriod
  data?: Leaderboard
  state: 'loading' | 'empty' | 'error' | 'ready'
  error?: ApiError
  guest: boolean
  topicMissing?: boolean
  onSelect: (value: string) => void
  onHskChange?: (value: string) => void
  onPeriodChange: (period: LeaderboardPeriod) => void
  onRetry: () => void
}

function errorMessage(error?: ApiError, topicMissing = false): string {
  if (topicMissing || error?.errorCode === 'TOPIC_NOT_FOUND')
    return 'Chủ đề không tồn tại. Hãy chọn chủ đề khác.'
  if (error?.status === 422) return error.message || 'Thông tin bảng xếp hạng không hợp lệ.'
  if (error?.status === 429) return 'Bạn đã xem bảng xếp hạng quá nhiều lần. Vui lòng thử lại sau.'
  if (error?.status === 0) return 'Không có kết nối mạng. Kiểm tra kết nối rồi thử lại.'
  return 'Không tải được bảng xếp hạng. Vui lòng thử lại.'
}

function RankLabel({ rank }: { rank: number }) {
  const medal =
    rank === 1 ? '🥇 Hạng 1' : rank === 2 ? '🥈 Hạng 2' : rank === 3 ? '🥉 Hạng 3' : `Hạng ${rank}`
  return <span className="font-semibold tabular-nums">{medal}</span>
}

function PlayerName({ name, avatarUrl }: { name: string; avatarUrl: string | null }) {
  return (
    <span className="flex min-w-0 items-center gap-3">
      {avatarUrl ? (
        <img alt="" className="size-10 shrink-0 rounded-full object-cover" src={avatarUrl} />
      ) : (
        <span
          aria-hidden="true"
          className="grid size-10 shrink-0 place-items-center rounded-full bg-brand-50 font-bold text-brand-700"
        >
          {name.trim().slice(0, 1) || '?'}
        </span>
      )}
      <span className="min-w-0 break-words">{name}</span>
    </span>
  )
}

export function LeaderboardView({
  kind,
  title,
  topics = [],
  games = [],
  selectedId = '',
  hsk,
  period,
  data,
  state,
  error,
  guest,
  topicMissing = false,
  onSelect,
  onHskChange,
  onPeriodChange,
  onRetry,
}: Props) {
  const [expanded, setExpanded] = useState(false)
  const entries = data?.entries ?? []
  const shown = entries.slice(0, expanded ? 20 : 5)
  const myEntry = guest ? null : (data?.myEntry ?? null)
  const myEntryVisible = !!myEntry && shown.some((entry) => entry.userId === myEntry.userId)
  const hasMore = entries.length > 5 && !expanded
  const selectedTopic = topics.find((topic) => String(topic.id) === selectedId)
  const selectedGame = games.find((game) => game.code === selectedId)

  return (
    <main className="mx-auto w-full min-w-0 max-w-content space-y-6 break-words p-4 [overflow-wrap:anywhere] sm:p-6">
      <PageHeader title={title} subtitle="Xem thành tích học tập và trò chơi của cộng đồng." />
      <Card className="space-y-5">
        <div className="grid gap-4 sm:grid-cols-2">
          {kind === 'topic' ? (
            <>
              <FormField htmlFor="leaderboard-hsk" label="Cấp độ HSK">
                <Select
                  onChange={(event) => {
                    setExpanded(false)
                    onHskChange?.(event.target.value)
                  }}
                  value={hsk === undefined ? '' : String(hsk)}
                >
                  <option value="">Tất cả cấp độ</option>
                  {[1, 2, 3, ...(hsk && hsk > 3 ? [hsk] : [])].map((level) => (
                    <option key={level} value={level}>
                      HSK {level}
                    </option>
                  ))}
                </Select>
              </FormField>
              <FormField htmlFor="leaderboard-topic" label="Chủ đề">
                <Select
                  onChange={(event) => {
                    setExpanded(false)
                    onSelect(event.target.value)
                  }}
                  value={selectedId}
                >
                  {!selectedId ? <option value="">Chọn chủ đề</option> : null}
                  {selectedId && !selectedTopic ? (
                    <option value={selectedId}>Chủ đề đã chọn</option>
                  ) : null}
                  {topics.map((topic) => (
                    <option key={topic.id} value={topic.id}>
                      HSK {topic.hskLevel} · {topic.name}
                    </option>
                  ))}
                </Select>
              </FormField>
            </>
          ) : (
            <FormField className="sm:col-span-2" htmlFor="leaderboard-game" label="Trò chơi">
              <Select
                onChange={(event) => {
                  setExpanded(false)
                  onSelect(event.target.value)
                }}
                value={selectedId}
              >
                {!selectedId ? <option value="">Chọn trò chơi</option> : null}
                {selectedId && !selectedGame ? (
                  <option value={selectedId}>Trò chơi đã chọn</option>
                ) : null}
                {games.map((game) => (
                  <option key={game.code} value={game.code}>
                    {game.name}
                  </option>
                ))}
              </Select>
            </FormField>
          )}
        </div>
        <div aria-label="Kỳ xếp hạng" className="flex flex-wrap gap-2" role="group">
          {LEADERBOARD_PERIODS.map((option) => (
            <Button
              aria-pressed={period === option.value}
              key={option.value}
              onClick={() => {
                setExpanded(false)
                onPeriodChange(option.value)
              }}
              size="sm"
              variant={period === option.value ? 'primary' : 'outline'}
            >
              {option.label}
            </Button>
          ))}
        </div>
        {data ? (
          <p className="text-body-sm text-ink-secondary">
            {formatPeriodRange(data.periodStart, data.periodEnd)}
          </p>
        ) : null}
      </Card>

      {state === 'loading' ? (
        <section aria-label="Đang tải bảng xếp hạng" className="space-y-3" role="status">
          <Skeleton variant="card" />
          <Skeleton variant="card" />
        </section>
      ) : state === 'error' ? (
        <Card className="space-y-3" role="alert">
          <h2 className="text-h3 text-ink">{errorMessage(error, topicMissing)}</h2>
          <Button onClick={onRetry} variant="outline">
            Thử lại
          </Button>
        </Card>
      ) : state === 'empty' || !data || entries.length === 0 ? (
        <div className="space-y-4">
          <Card className="text-center">
            <h2 className="text-h3 text-ink">Chưa có ai trong kỳ này</h2>
            <p className="mt-2 text-body text-ink-secondary">
              Hãy quay lại sau khi có thêm kết quả.
            </p>
          </Card>
          {guest ? (
            <Card>
              <p className="text-body text-ink-secondary">Đăng nhập để xem hạng của bạn.</p>
              <Button className="mt-3" to="/login" variant="outline">
                Đăng nhập
              </Button>
            </Card>
          ) : (
            <Card>
              <p className="text-body text-ink-secondary">Bạn chưa có lượt hợp lệ trong kỳ này.</p>
            </Card>
          )}
        </div>
      ) : (
        <Card className="space-y-4">
          <div className="hidden overflow-hidden min-[641px]:block">
            <table className="w-full table-fixed text-left text-body-sm">
              <thead>
                <tr className="border-b border-line text-ink-secondary">
                  <th className="w-24 px-3 py-3">Hạng</th>
                  <th className="px-3 py-3">Người chơi</th>
                  <th className="w-28 px-3 py-3 text-right">Điểm</th>
                  <th className="w-28 px-3 py-3 text-right">Thời gian</th>
                </tr>
              </thead>
              <tbody>
                {shown.map((entry) => (
                  <tr
                    className={`border-b border-line last:border-0 ${myEntry?.userId === entry.userId ? 'bg-brand-50' : ''}`}
                    key={entry.userId}
                  >
                    <td className="px-3 py-3">
                      <RankLabel rank={entry.rank} />
                    </td>
                    <td className="px-3 py-3">
                      <PlayerName name={entry.displayName} avatarUrl={entry.avatarUrl} />
                      {myEntry?.userId === entry.userId ? (
                        <span className="sr-only"> Của bạn</span>
                      ) : null}
                    </td>
                    <td className="px-3 py-3 text-right font-semibold tabular-nums">
                      {entry.score}
                    </td>
                    <td className="px-3 py-3 text-right tabular-nums">
                      {formatDuration(entry.durationMs)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <ol aria-label="Bảng xếp hạng" className="space-y-3 min-[641px]:hidden">
            {shown.map((entry) => (
              <li key={entry.userId}>
                <Card
                  className={myEntry?.userId === entry.userId ? 'border-brand-600 bg-brand-50' : ''}
                >
                  <div className="flex flex-wrap items-center justify-between gap-3">
                    <RankLabel rank={entry.rank} />
                    <span className="font-bold tabular-nums">{entry.score} điểm</span>
                  </div>
                  <div className="mt-3">
                    <PlayerName name={entry.displayName} avatarUrl={entry.avatarUrl} />
                  </div>
                  <p className="mt-2 text-body-sm text-ink-secondary">
                    Thời gian: {formatDuration(entry.durationMs)}
                  </p>
                  {myEntry?.userId === entry.userId ? (
                    <p className="mt-2 font-semibold text-brand-700">Của bạn</p>
                  ) : null}
                </Card>
              </li>
            ))}
          </ol>
          {hasMore ? (
            <Button onClick={() => setExpanded(true)} variant="outline">
              Xem thêm
            </Button>
          ) : null}
          {expanded && entries.length > 5 ? (
            <Button onClick={() => setExpanded(false)} variant="outline">
              Thu gọn
            </Button>
          ) : null}
          {!guest && myEntry && !myEntryVisible ? (
            <Card aria-label="Hạng của bạn" className="border-brand-600 bg-brand-50">
              <p className="font-semibold text-brand-800">Hạng của bạn: #{myEntry.rank}</p>
              <p className="mt-1 text-body-sm">
                {myEntry.score} điểm · {formatDuration(myEntry.durationMs)}
              </p>
            </Card>
          ) : null}
          {guest ? (
            <Card>
              <p className="text-body text-ink-secondary">Đăng nhập để xem hạng của bạn.</p>
              <Button className="mt-3" to="/login" variant="outline">
                Đăng nhập
              </Button>
            </Card>
          ) : !myEntry ? (
            <Card>
              <p className="text-body text-ink-secondary">Bạn chưa có lượt hợp lệ trong kỳ này.</p>
            </Card>
          ) : null}
        </Card>
      )}
    </main>
  )
}
