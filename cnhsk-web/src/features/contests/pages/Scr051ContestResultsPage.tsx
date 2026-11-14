import { useParams } from 'react-router-dom'
import { useApiQuery } from '@/shared/api'
import { getContestResults } from '../api/contests'
import { ContestResultsView } from '../components/ContestResultsView'

function Results({ id }: { id: number }) {
  const query = useApiQuery((signal) => getContestResults(id, signal), [id])
  return (
    <ContestResultsView
      state={query.state}
      result={query.data}
      error={query.error?.message}
      onRetry={query.reload}
    />
  )
}

export default function Scr051ContestResultsPage() {
  const id = Number(useParams().id)
  return Number.isSafeInteger(id) && id > 0 ? (
    <Results key={id} id={id} />
  ) : (
    <p role="alert">Cuộc thi không khả dụng.</p>
  )
}
