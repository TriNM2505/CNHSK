import { useCallback, useEffect, useRef, useState } from 'react'

export const MAX_RECORDING_SECONDS = 30

export type RecorderState = 'idle' | 'recording' | 'recorded' | 'unsupported' | 'denied'

/** Microphone recording in the browser; the recording stops by itself after {@link MAX_RECORDING_SECONDS}. */
export function useVoiceRecorder() {
  const supported =
    typeof window !== 'undefined' && typeof MediaRecorder !== 'undefined' && Boolean(navigator.mediaDevices?.getUserMedia)
  const [state, setState] = useState<RecorderState>(supported ? 'idle' : 'unsupported')
  const [seconds, setSeconds] = useState(0)
  const [recording, setRecording] = useState<Blob>()
  const [previewUrl, setPreviewUrl] = useState<string>()
  const recorder = useRef<MediaRecorder>()
  const timer = useRef<number>()

  const releaseMicrophone = useCallback(() => {
    window.clearInterval(timer.current)
    recorder.current?.stream.getTracks().forEach((track) => track.stop())
  }, [])

  useEffect(() => () => releaseMicrophone(), [releaseMicrophone])
  useEffect(() => () => {
    if (previewUrl) URL.revokeObjectURL(previewUrl)
  }, [previewUrl])

  const stop = useCallback(() => {
    if (recorder.current?.state === 'recording') recorder.current.stop()
  }, [])

  const start = useCallback(async () => {
    if (!supported) return
    let stream: MediaStream
    try {
      stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    } catch {
      setState('denied')
      return
    }
    const chunks: Blob[] = []
    const next = new MediaRecorder(stream)
    next.ondataavailable = (event) => {
      if (event.data.size > 0) chunks.push(event.data)
    }
    next.onstop = () => {
      releaseMicrophone()
      const blob = new Blob(chunks, { type: next.mimeType || 'audio/webm' })
      setRecording(blob)
      setPreviewUrl(URL.createObjectURL(blob))
      setState('recorded')
    }
    recorder.current = next
    setRecording(undefined)
    setPreviewUrl(undefined)
    setSeconds(0)
    setState('recording')
    next.start()
    const startedAt = Date.now()
    timer.current = window.setInterval(() => {
      const elapsed = Math.floor((Date.now() - startedAt) / 1000)
      setSeconds(elapsed)
      if (elapsed >= MAX_RECORDING_SECONDS) stop()
    }, 250)
  }, [releaseMicrophone, stop, supported])

  const reset = useCallback(() => {
    stop()
    setRecording(undefined)
    setPreviewUrl(undefined)
    setSeconds(0)
    setState(supported ? 'idle' : 'unsupported')
  }, [stop, supported])

  return { previewUrl, recording, reset, seconds, start, state, stop }
}

/** Plays the recorded model when there is one, otherwise a Chinese voice of the browser reads the text. */
export function playModel(text: string, audioUrl: string | null): void {
  if (audioUrl) {
    void new Audio(audioUrl).play().catch(() => speak(text))
    return
  }
  speak(text)
}

function speak(text: string): void {
  if (typeof window === 'undefined' || !('speechSynthesis' in window)) return
  const utterance = new SpeechSynthesisUtterance(text)
  utterance.lang = 'zh-CN'
  utterance.rate = 0.8
  const voice = window.speechSynthesis.getVoices().find((candidate) => candidate.lang.toLowerCase().startsWith('zh'))
  if (voice) utterance.voice = voice
  window.speechSynthesis.cancel()
  window.speechSynthesis.speak(utterance)
}
