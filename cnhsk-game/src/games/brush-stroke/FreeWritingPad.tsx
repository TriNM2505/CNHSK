import { useRef, useState } from 'react'
import type { PointerEvent } from 'react'

import type { Point } from '../../engine/path'

/** An empty writing square: no outline, no stroke order. Reports every finished stroke with all strokes so far. */
export function FreeWritingPad({ strokes, disabled, onStrokes }: {
  strokes: readonly Point[][]
  disabled: boolean
  onStrokes: (strokes: Point[][]) => void
}) {
  const active = useRef<{ id: number; points: Point[] }>()
  const [draft, setDraft] = useState<Point[]>([])

  function point(event: PointerEvent<SVGSVGElement>): Point {
    const bounds = event.currentTarget.getBoundingClientRect()
    return {
      x: 1000 * (event.clientX - bounds.left) / (bounds.width || 1),
      y: 1000 * (event.clientY - bounds.top) / (bounds.height || 1),
    }
  }

  function clear() {
    active.current = undefined
    setDraft([])
  }

  function start(event: PointerEvent<SVGSVGElement>) {
    if (disabled || active.current || event.button !== 0) return
    event.currentTarget.setPointerCapture?.(event.pointerId)
    const points = [point(event)]
    active.current = { id: event.pointerId, points }
    setDraft(points)
  }

  function move(event: PointerEvent<SVGSVGElement>) {
    if (disabled || active.current?.id !== event.pointerId) return
    const points = [...active.current!.points, point(event)]
    active.current = { id: event.pointerId, points }
    setDraft(points)
  }

  function end(event: PointerEvent<SVGSVGElement>) {
    const stroke = active.current
    if (!stroke || stroke.id !== event.pointerId) return
    clear()
    if (!disabled) onStrokes([...strokes, [...stroke.points, point(event)]])
  }

  return (
    <svg aria-label="Ô viết chữ" role="img" viewBox="0 0 1000 1000" className="stroke-board mx-auto max-w-sm"
      onPointerDown={start} onPointerMove={move} onPointerUp={end}
      onPointerCancel={(event) => { if (active.current?.id === event.pointerId) clear() }}
      onLostPointerCapture={(event) => { if (active.current?.id === event.pointerId) clear() }}
    >
      <path className="board-guide" d="M500 0V1000 M0 500H1000 M0 0L1000 1000 M1000 0L0 1000" />
      {[...strokes, draft].map((points, no) => (
        <polyline key={no} points={points.map(({ x, y }) => `${x},${y}`).join(' ')}
          fill="none" strokeWidth={24} strokeLinecap="round" strokeLinejoin="round" className="stroke-ink" />
      ))}
    </svg>
  )
}
