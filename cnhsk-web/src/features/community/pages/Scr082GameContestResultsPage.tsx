import { Download, Megaphone, UserX, Undo2 } from 'lucide-react'
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'

import { useApiQuery } from '@/shared/api'
import { downloadBlob } from '@/shared/lib/downloadBlob'
import { Button, Card, PageHeader } from '@/shared/ui'

import {
  decideGameContestExclusion,
  exportGameContestResults,
  getGameContestResults,
  type GameContestResults,
} from '../api/game-contests'

const TIME = new Intl.DateTimeFormat('vi-VN', {
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  month: '2-digit',
  second: '2-digit',
  timeZone: 'Asia/Ho_Chi_Minh',
})

function seconds(durationMs: number | null) {
  return durationMs === null ? '—' : `${(durationMs / 1000).toFixed(1)} giây`
}

/** The announcement draft handed to SCR-080, so the moderator only polishes and posts it. */
function announcementDraft(results: GameContestResults): { title: string; content: string } {
  const lines = results.winners.map(
    (winner) => `${winner.rank}. ${winner.displayName ?? 'Người chơi'} — ${winner.points} điểm (${seconds(winner.durationMs)})`,
  )
  return {
    content: [
      `Cuộc thi "${results.contest.title}" đã khép lại với ${results.contest.participants} người chơi.`,
      '',
      'Xin chúc mừng những người chiến thắng:',
      ...lines,
      '',
      'Ban tổ chức sẽ liên hệ qua email để trao quà. Cảm ơn mọi người đã tham gia!',
    ].join('\n'),
    title: `Công bố kết quả: ${results.contest.title}`,
  }
}

/** SCR-082: winners with contact emails, CSV export, excluding cheaters and drafting the announcement. */
export default function Scr082GameContestResultsPage() {
  const { id } = useParams()
  const contestId = Number(id)
  const navigate = useNavigate()
  const query = useApiQuery((signal) => getGameContestResults(contestId, signal), [contestId])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string>()

  async function exclude(userId: number, excluded: boolean) {
    const note = excluded ? window.prompt('Lý do loại người chơi này?')?.trim() : 'Khôi phục'
    if (!note) return
    setBusy(true)
    setError(undefined)
    try {
      await decideGameContestExclusion(contestId, userId, excluded, note)
      query.reload()
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Chưa lưu được, thử lại.')
    } finally {
      setBusy(false)
    }
  }

  async function exportCsv() {
    setBusy(true)
    setError(undefined)
    try {
      const file = await exportGameContestResults(contestId)
      if (file) downloadBlob(file.blob, file.fileName ?? `cuoc-thi-${contestId}-ket-qua.csv`)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Chưa tải được CSV.')
    } finally {
      setBusy(false)
    }
  }

  if (query.state === 'loading') return <p className="px-4 py-10 text-center text-body-sm text-ink-secondary" role="status">Đang tải kết quả…</p>
  if (query.state === 'error' || !query.data) {
    return (
      <main className="mx-auto max-w-reading space-y-3 px-4 py-10 text-center">
        <p className="text-body text-ink" role="alert">{query.error?.message ?? 'Không tải được kết quả.'}</p>
        <Button onClick={query.reload} variant="outline">Thử lại</Button>
      </main>
    )
  }

  const results = query.data
  return (
    <main className="mx-auto w-full min-w-0 max-w-content space-y-5 px-4 py-6 pb-16 sm:px-6">
      <PageHeader subtitle={`${results.contest.participants} người chơi · top ${results.contest.prizeCount} nhận giải`} title={`Kết quả: ${results.contest.title}`} />

      <div className="flex flex-wrap gap-3">
        <Button disabled={busy} onClick={() => navigate('/moderation/posts/new', { state: { announcement: announcementDraft(results) } })}>
          <Megaphone aria-hidden="true" className="size-4" />
          Soạn bài công bố
        </Button>
        <Button disabled={busy} onClick={() => void exportCsv()} variant="outline">
          <Download aria-hidden="true" className="size-4" />
          Tải CSV
        </Button>
        <Link className="inline-flex min-h-touch items-center px-3 text-label text-brand-700" to={`/community/contests/${contestId}`}>
          Xem trang cuộc thi
        </Link>
      </div>
      {error ? <p className="text-body-sm text-danger" role="alert">{error}</p> : null}

      <Card className="overflow-x-auto p-0">
        <table className="w-full min-w-[640px] text-left text-body-sm">
          <caption className="sr-only">Người đạt giải</caption>
          <thead className="bg-canvas text-label text-ink-secondary">
            <tr>
              <th className="px-4 py-3" scope="col">Hạng</th>
              <th className="px-4 py-3" scope="col">Người chơi</th>
              <th className="px-4 py-3" scope="col">Email</th>
              <th className="px-4 py-3 text-right" scope="col">Điểm</th>
              <th className="px-4 py-3" scope="col">Thời gian</th>
              <th className="px-4 py-3" scope="col">Đạt lúc</th>
              <th className="px-4 py-3" scope="col"><span className="sr-only">Thao tác</span></th>
            </tr>
          </thead>
          <tbody>
            {results.winners.length === 0 ? (
              <tr><td className="px-4 py-6 text-center text-ink-secondary" colSpan={7}>Không có người chơi hợp lệ.</td></tr>
            ) : (
              results.winners.map((winner) => (
                <tr className="border-t border-line" key={winner.userId}>
                  <td className="px-4 py-3 font-semibold text-brand-700">{winner.rank}</td>
                  <td className="px-4 py-3">{winner.displayName ?? 'Người chơi'}</td>
                  <td className="px-4 py-3 select-all">{winner.email}</td>
                  <td className="px-4 py-3 text-right tabular-nums">{winner.points}</td>
                  <td className="px-4 py-3 tabular-nums">{seconds(winner.durationMs)}</td>
                  <td className="px-4 py-3 tabular-nums">{TIME.format(new Date(winner.achievedAt))}</td>
                  <td className="px-4 py-3">
                    <Button disabled={busy} onClick={() => void exclude(winner.userId, true)} size="sm" variant="ghost">
                      <UserX aria-hidden="true" className="size-4" />
                      Loại
                    </Button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </Card>

      {results.excluded.length > 0 ? (
        <Card className="space-y-3 p-4">
          <h2 className="text-h3 text-ink">Đã loại</h2>
          <ul className="space-y-2">
            {results.excluded.map((row) => (
              <li className="flex flex-wrap items-center justify-between gap-3 rounded-md bg-canvas px-3 py-2" key={row.userId}>
                <span className="text-body-sm">
                  <strong>{row.displayName ?? 'Người chơi'}</strong> — {row.note}
                </span>
                <Button disabled={busy} onClick={() => void exclude(row.userId, false)} size="sm" variant="ghost">
                  <Undo2 aria-hidden="true" className="size-4" />
                  Khôi phục
                </Button>
              </li>
            ))}
          </ul>
        </Card>
      ) : null}
    </main>
  )
}
