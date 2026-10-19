import type { ReactNode } from 'react'

export type PreviewState = 'loading' | 'empty' | 'error' | 'ready'

export interface PreviewEntry {
  fullFrame?: boolean
  group: 'component' | 'screen'
  id: string
  render: (state: PreviewState) => ReactNode
  scr?: string
  states?: readonly PreviewState[]
  title: string
}
