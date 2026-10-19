import { useParams } from 'react-router-dom'

import { getPreviewStates, previewRegistry, type PreviewState } from '@/app/preview/registry'
import { StatusBadge } from '@/shared/ui'

function isPreviewState(value: string | undefined): value is PreviewState {
  return value === 'loading' || value === 'empty' || value === 'error' || value === 'ready'
}

export function PreviewFrame() {
  const { state: stateParam, viewId } = useParams()
  const entry = previewRegistry.find(({ id }) => id === viewId)

  if (!entry) {
    return (
      <main className="min-h-screen bg-canvas p-4">
        <StatusBadge tone="danger">Không tìm thấy nội dung xem trước</StatusBadge>
      </main>
    )
  }

  const states = getPreviewStates(entry)
  const state =
    isPreviewState(stateParam) && states.includes(stateParam)
      ? stateParam
      : states.includes('ready')
        ? 'ready'
        : (states[0] ?? 'ready')

  if (entry.fullFrame) return entry.render(state)

  return <main className="min-h-screen bg-canvas p-4 sm:p-6">{entry.render(state)}</main>
}
