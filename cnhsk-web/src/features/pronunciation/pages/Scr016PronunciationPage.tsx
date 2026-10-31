import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'

import { useApiQuery } from '@/shared/api'
import { Button } from '@/shared/ui'

import { getPronunciationStages } from '../api/pronunciation'
import { PronunciationPracticeView } from '../components/PronunciationPracticeView'
import { PronunciationStageListView } from '../components/PronunciationStageListView'
import type { PronunciationStageSummary } from '../types'
import { PronunciationPracticeController } from './PronunciationPracticeController'

function parseStageIndex(value: string | undefined) {
  if (!value || !/^[1-8]$/.test(value)) return undefined
  return Number(value)
}

export default function Scr016PronunciationPage() {
  const params = useParams()
  const onStageRoute = params.stageIndex !== undefined
  const stageIndex = parseStageIndex(params.stageIndex)
  const query = useApiQuery(getPronunciationStages, [], {
    isEmpty: (stages) => stages.length === 0,
  })
  const [lastStages, setLastStages] = useState<PronunciationStageSummary[]>()

  useEffect(() => {
    if (query.data) setLastStages(query.data)
  }, [query.data])

  if (!onStageRoute) {
    return (
      <PronunciationStageListView
        error={query.error}
        onRetry={query.reload}
        stages={query.data ?? []}
        state={query.state}
      />
    )
  }

  if (!stageIndex) {
    return <PronunciationPracticeView onRetry={() => undefined} state="empty" />
  }

  const stage = (query.data ?? lastStages)?.find((candidate) => candidate.stageIndex === stageIndex)
  if (stage) {
    return (
      <div className="space-y-4">
        {query.state === 'error' ? (
          <div className="rounded-md bg-warning-bg p-4 text-body-sm text-warning" role="alert">
            <p>Không thể cập nhật danh sách tầng. Lượt luyện hiện tại vẫn được giữ.</p>
            <Button className="mt-3" onClick={query.reload} variant="outline">
              Thử tải lại danh sách
            </Button>
          </div>
        ) : null}
        <PronunciationPracticeController
          key={stage.id}
          onStagesRefresh={query.reload}
          stage={stage}
        />
      </div>
    )
  }

  if (query.state === 'loading') {
    return <PronunciationPracticeView onRetry={query.reload} state="loading" />
  }

  if (query.state === 'error') {
    return <PronunciationPracticeView error={query.error} onRetry={query.reload} state="error" />
  }

  return <PronunciationPracticeView onRetry={query.reload} state="empty" />
}
