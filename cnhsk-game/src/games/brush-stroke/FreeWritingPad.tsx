import { useRef, useState } from 'react'
import type { PointerEvent } from 'react'

import type { Point } from '../../engine/path'

/** An empty writing square: no outline, no stroke order. Reports every finished stroke with all strokes so far. */
export function FreeWritingPad({ strokes, disabled, onPenDown, onStrokes }: {
  strokes: readonly Point[][]
  disabled: boolean
  onPenDown?: () => void
  onStrokes: (strokes: Point[][]) => void
}) {
  const active = useRef<{ id: number; points: Point[]; bounds: DOMRect }>()
  const [draft, setDraft] = useState<Point[]>([])

  function point(event: { clientX: number; clientY: number }, bounds: DOMRect): Point {
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
    const bounds = event.currentTarget.getBoundingClientRect()
    const points = [point(event, bounds)]
    active.current = { id: event.pointerId, points, bounds }
    setDraft(points)
    onPenDown?.()
  }

  function move(event: PointerEvent<SVGSVGElement>) {
    const stroke = active.current
    if (disabled || stroke?.id !== event.pointerId) return
    // Browsers merge fast pen moves into one event; the merged points keep the line smooth.
    const merged = event.nativeEvent.getCoalescedEvents?.() ?? []
    const added = (merged.length ? merged : [event]).map((each) => point(each, stroke.bounds))
    stroke.points = [...stroke.points, ...added]
    setDraft(stroke.points)
  }

  function end(event: PointerEvent<SVGSVGElement>) {
    const stroke = active.current
    if (!stroke || stroke.id !== event.pointerId) return
    clear()
    if (!disabled) onStrokes([...strokes, [...stroke.points, point(event, stroke.bounds)]])
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