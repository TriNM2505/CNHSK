import { CircleAlert, Headphones, Volume2 } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'

import { Button } from '@/shared/ui'

export interface AudioFailurePayload {
  audioFailed: true
  replayCount: number
}

export interface AudioButtonProps {
  audioUrl: string
  maxReplays: number
  disabled?: boolean
  onAudioFailed: (payload: AudioFailurePayload) => Promise<unknown> | unknown
  onReplayCountChange?: (replayCount: number) => void
}

export function AudioButton({
  audioUrl,
  maxReplays,
  disabled = false,
  onAudioFailed,
  onReplayCountChange,
}: AudioButtonProps) {
  const audioRef = useRef<HTMLAudioElement>(null)
  const onReplayCountChangeRef = useRef(onReplayCountChange)
  const replayCountRef = useRef(0)
  const playingRef = useRef(false)
  const [replayCount, setReplayCount] = useState(0)
  const [playing, setPlaying] = useState(false)
  const [loadError, setLoadError] = useState(false)
  const safeMaximum = Math.max(0, maxReplays)
  const exhausted = replayCount >= safeMaximum

  onReplayCountChangeRef.current = onReplayCountChange

  useEffect(() => {
    replayCountRef.current = 0
    playingRef.current = false
    setReplayCount(0)
    setPlaying(false)
    setLoadError(false)
    onReplayCountChangeRef.current?.(0)
  }, [audioUrl, maxReplays])

  const play = async () => {
    if (
      disabled ||
      playingRef.current ||
      replayCountRef.current >= safeMaximum ||
      !audioRef.current
    )
      return
    playingRef.current = true
    setPlaying(true)
    setLoadError(false)
    try {
      audioRef.current.currentTime = 0
      await audioRef.current.play()
      const next = Math.min(replayCountRef.current + 1, safeMaximum)
      replayCountRef.current = next
      setReplayCount(next)
      onReplayCountChangeRef.current?.(next)
    } catch {
      setLoadError(true)
    } finally {
      playingRef.current = false
      setPlaying(false)
    }
  }

  return (
    <div className="space-y-2">
      <audio onError={() => setLoadError(true)} preload="none" ref={audioRef} src={audioUrl} />
      <div className="flex flex-wrap items-center gap-3">
        <Button
          disabled={disabled || exhausted}
          loading={playing}
          onClick={() => void play()}
          variant="secondary"
        >
          <Volume2 aria-hidden="true" className="size-5" />
          {exhausted ? 'Đã hết lượt nghe' : 'Nghe từ'}
        </Button>
        <span className="text-body-sm text-ink-secondary">
          Đã nghe {replayCount}/{safeMaximum} lần
        </span>
        <Button
          disabled={disabled}
          onClick={() => void onAudioFailed({ audioFailed: true, replayCount })}
          variant="ghost"
        >
          <Headphones aria-hidden="true" className="size-5" />
          Không nghe được
        </Button>
      </div>
      {loadError ? (
        <p className="flex items-start gap-2 text-body-sm text-danger" role="alert">
          <CircleAlert aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
          <span>Không tải được âm thanh. Bạn có thể bấm “Không nghe được” để tiếp tục.</span>
        </p>
      ) : null}
    </div>
  )
}
