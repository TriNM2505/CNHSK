import { Suspense, lazy } from 'react'

import type { KnowledgeGraphProps } from './components/KnowledgeGraph'
import { Skeleton } from '@/shared/ui'

export type { KnowledgeGraphProps } from './components/KnowledgeGraph'

// The graph libraries are large; load them only when a screen actually shows the tree.
const KnowledgeGraph = lazy(() => import('./components/KnowledgeGraph'))

export function KnowledgeGraphLazy(props: KnowledgeGraphProps) {
  return (
    <Suspense
      fallback={<Skeleton aria-label="Đang tải sơ đồ" className="h-[560px]" variant="card" />}
    >
      <KnowledgeGraph {...props} />
    </Suspense>
  )
}
