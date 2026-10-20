import { useParams } from 'react-router-dom'

import { useApiQuery } from '@/shared/api'

import { getPronunciationStages } from '../api/pronunciation'
import { PronunciationPracticeView } from '../components/PronunciationPracticeView'
import { PronunciationStageListView } from '../components/PronunciationStageListView'
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

  const stage = query.data?.find((candidate) => candidate.stageIndex === stageIndex)
  if (stage) {
    return (
      <PronunciationPracticeController
        key={stage.id}
        onStagesRefresh={query.reload}
        stage={stage}
      />
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
