import type { ApiQueryState } from '@/shared/api'
import { Card, ErrorState, Skeleton } from '@/shared/ui'

import type { AiNodeGaps } from '../types'

export interface AiNodeGapsPanelProps {
  gaps: AiNodeGaps | undefined
  state: ApiQueryState
  onRetry: () => void
  onSelectKid: (kid: string) => void
}

/** Nodes the AI question job skips because the node has no can-do statement or no linked word. */
export function AiNodeGapsPanel({ gaps, state, onRetry, onSelectKid }: AiNodeGapsPanelProps) {
  const nodes = gaps?.nodes ?? []
  return (
    <Card aria-labelledby="ai-node-gaps-title" className="space-y-3 p-5" role="region">
      <h2 className="text-h3 text-ink" id="ai-node-gaps-title">
        Nút AI chưa sinh được câu
      </h2>
      {state === 'loading' ? <Skeleton variant="text" /> : null}
      {state === 'error' ? (
        <ErrorState message="Không tải được danh sách nút thiếu dữ liệu." onRetry={onRetry} />
      ) : null}
      {state === 'ready' && gaps ? (
        <p className="text-body-sm text-ink-secondary">
          {nodes.length === 0
            ? `Cả ${gaps.totalNodes} nút đều đủ dữ liệu để AI sinh câu.`
            : `${nodes.length}/${gaps.totalNodes} nút bị bỏ qua vì thiếu mục tiêu "có thể làm" hoặc chưa gắn từ vựng. Bổ sung dữ liệu cho nút để AI sinh được câu.`}
        </p>
      ) : null}
      {state === 'ready' && nodes.length > 0 ? (
        <div className="max-h-80 overflow-auto">
          <table className="w-full text-left text-body-sm">
            <thead className="text-ink-secondary">
              <tr>
                <th className="py-1 pr-3 font-medium">Nút</th>
                <th className="py-1 pr-3 font-medium">Thiếu</th>
              </tr>
            </thead>
            <tbody>
              {nodes.map((node) => (
                <tr key={node.kid} className="border-t border-line">
                  <td className="py-1.5 pr-3">
                    <button
                      className="text-left text-brand-700 underline-offset-2 hover:underline"
                      onClick={() => onSelectKid(node.kid)}
                      type="button"
                    >
                      {node.kid} · {node.nameVi}
                    </button>
                  </td>
                  <td className="py-1.5 pr-3 text-ink-secondary">
                    {[
                      node.missingCanDo ? 'Mục tiêu "có thể làm"' : null,
                      node.lexemeCount === 0 ? 'Từ vựng gắn nút' : null,
                    ]
                      .filter(Boolean)
                      .join(', ')}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}
    </Card>
  )
}
