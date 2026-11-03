import { useRef, useState } from 'react'

import { HanziCanvas, type HanziCanvasHandle } from '@/features/hanzi/components/HanziCanvas'
import { Button } from '@/shared/ui'

export function LexemeStrokeAnimation({ character }: { character: string }) {
  const canvas = useRef<HanziCanvasHandle>(null)
  const [failed, setFailed] = useState(false)
  const [ready, setReady] = useState(false)
  const [playing, setPlaying] = useState(false)

  if (failed) return null

  return (
    <section
      aria-label="Hoạt hình nét"
      className="space-y-3 rounded-md border border-line bg-surface p-4"
    >
      <h2 className="text-h3 text-ink">Nét viết</h2>
      <HanziCanvas
        character={character}
        onDataError={() => setFailed(true)}
        onReady={() => setReady(true)}
        ref={canvas}
      />
      <Button
        disabled={!ready || playing}
        onClick={() => {
          setPlaying(true)
          canvas.current?.animateCharacter(() => setPlaying(false))
        }}
        variant="outline"
      >
        Xem hoạt hình nét
      </Button>
    </section>
  )
}
