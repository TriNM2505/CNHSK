import { validateText } from './interaction-validation'
import type { ReportDecision, ReportTargetType } from './types'

export function makeReportDecision(
  targetKind: ReportTargetType,
  decision: string,
  note: string,
  commentAction: string,
): ReportDecision | undefined {
  if (validateText(note, 1000) || (decision !== 'ACTION' && decision !== 'REJECT')) return undefined
  if (targetKind === 'COMMENT' && decision === 'ACTION') {
    if (commentAction !== 'HIDE' && commentAction !== 'DELETE') return undefined
    return { decision, note: note.trim(), commentAction }
  }
  return { decision, note: note.trim() }
}
