import { useEffect, useRef, useState } from 'react'
import { Button, Card } from '@/shared/ui'
import { duration, errorMessage, isConflict } from '../contestActions'
import type { Winner } from '../types'

export function ContestWinnersPanel({
  onReveal,
  onConflict,
}: {
  onReveal: (signal: AbortSignal) => Promise<Winner[]>
  onConflict: (failure?: unknown) => void
}) {
  const [winners, setWinners] = useState<Winner[]>()
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string>()
  const request = useRef<AbortController>()
  useEffect(
    () => () => {
      request.current?.abort()
    },
    [],
  )
  function hide() {
    request.current?.abort()
    request.current = undefined
    setWinners(undefined)
    setPending(false)
    setError(undefined)
  }
  function reveal() {
    if (request.current) return
    const controller = new AbortController()
    request.current = controller
    setPending(true)
    setError(undefined)
    setWinners(undefined)
    void onReveal(controller.signal)
      .then((rows) => {
        if (!controller.signal.aborted) setWinners(rows)
      })
      .catch((failure: unknown) => {
        if (controller.signal.aborted) return
        setError(errorMessage(failure))
        if (isConflict(failure)) onConflict(failure)
      })
      .finally(() => {
        if (request.current === controller) {
          request.current = undefined
          setPending(false)
        }
      })
  }
  return (
    <Card className="min-w-0 space-y-4">
      <h2 className="text-h3">Liên hệ người đạt giải</h2>
      <p className="text-body-sm text-ink-secondary">
        Mỗi lần hiện email đều được ghi nhật ký. Dùng thông tin này để liên hệ trao giải ngoài hệ
        thống.
      </p>
      {winners === undefined && !pending && <Button onClick={reveal}>Hiện email</Button>}
      {(pending || winners !== undefined) && (
        <Button variant="outline" onClick={hide}>
          Ẩn email
        </Button>
      )}
      {pending && <p role="status">Đang tải liên hệ…</p>}
      {error && <p role="alert">{error}</p>}
      {winners?.length === 0 && <p>Không có người đạt giải trong các khoảng hạng đã đặt.</p>}
      {winners && (
        <ul className="space-y-3">
          {winners.map((winner) => (
            <li
              key={winner.userId}
              className="min-w-0 space-y-1 rounded-md border border-line p-4 [overflow-wrap:anywhere]"
            >
              <p className="font-semibold">
                Hạng {winner.rank} · {winner.displayName ?? `Người dùng #${winner.userId}`}
              </p>
              <p>{winner.prize}</p>
              <p>
                Điểm: {winner.score} · {duration(winner.durationMs)}
              </p>
              <p>
                Email:{' '}
                <a className="text-brand-700 underline" href={`mailto:${winner.email}`}>
                  {winner.email}
                </a>
              </p>
            </li>
          ))}
        </ul>
      )}
    </Card>
  )
}
