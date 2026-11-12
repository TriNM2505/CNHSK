import { useEffect, useMemo, useState } from 'react'

import { useApiQuery } from '@/shared/api'
import { getKnowledgeTree } from '@/features/question-bank/api/knowledge'
import type { KnowledgeNode } from '@/features/question-bank/types'

import { getMyMastery, getMyMasteryNode } from '../api/mastery'
import { MasteryProgressView } from '../components/MasteryProgressView'
import type { MasteryNode, MasteryNodeDetail } from '../types'

function loadProgress(signal: AbortSignal) {
  return Promise.all([getKnowledgeTree(undefined, signal), getMyMastery(signal)] as const)
}

function fillMissingMastery(
  knowledgeNodes: readonly KnowledgeNode[],
  masteryNodes: readonly MasteryNode[],
) {
  const byKid = new Map(masteryNodes.map((node) => [node.kid, node]))
  return knowledgeNodes.map(
    (node): MasteryNode =>
      byKid.get(node.kid) ?? {
        kid: node.kid,
        value: null,
        confidence: 0,
        evidenceCount: 0,
        state: 'notyet',
        frontier: false,
        dueAt: null,
      },
  )
}

export default function Scr019LearningProgressPage() {
  const query = useApiQuery(loadProgress, [])
  const [selectedKid, setSelectedKid] = useState<string>()
  const [detail, setDetail] = useState<MasteryNodeDetail>()
  const [detailState, setDetailState] = useState<'idle' | 'loading' | 'error' | 'ready'>('idle')
  const [detailRetry, setDetailRetry] = useState(0)
  const [detailError, setDetailError] = useState('')

  const knowledgeNodes = useMemo(
    () =>
      query.data
        ? query.data[0].nodes.filter((node) => node.hskLevel >= 1 && node.hskLevel <= 3)
        : [],
    [query.data],
  )
  const masteryNodes = useMemo(
    () => (query.data ? fillMissingMastery(knowledgeNodes, query.data[1].nodes) : []),
    [knowledgeNodes, query.data],
  )
  const isNewLearner =
    knowledgeNodes.length > 0 && masteryNodes.every((node) => node.evidenceCount === 0)

  useEffect(() => {
    if (!selectedKid) {
      setDetail(undefined)
      setDetailState('idle')
      return
    }

    const controller = new AbortController()
    setDetail(undefined)
    setDetailState('loading')

    void getMyMasteryNode(selectedKid, controller.signal)
      .then((result) => {
        if (controller.signal.aborted) return
        setDetail(result)
        setDetailState('ready')
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) return
        setDetail(undefined)
        setDetailState('error')
        setDetailError(error instanceof Error ? error.message : 'Không tải được chi tiết nút.')
      })

    return () => controller.abort()
  }, [detailRetry, selectedKid])

  const selectKid = (kid: string) => {
    setDetailError('')
    setSelectedKid(kid)
  }

  const retryDetail = () => {
    setDetailError('')
    setDetailRetry((current) => current + 1)
  }

  return (
    <MasteryProgressView
      detail={detail}
      detailError={detailState === 'error' ? detailError : undefined}
      detailLoading={detailState === 'loading'}
      isNewLearner={isNewLearner}
      knowledgeNodes={knowledgeNodes}
      masteryNodes={masteryNodes}
      onRetry={query.reload}
      onRetryDetail={retryDetail}
      onSelectKid={selectKid}
      selectedKid={selectedKid}
      state={
        query.state === 'ready' && knowledgeNodes.length === 0 ? 'empty' : query.state
      }
    />
  )
}
