import { Navigate, useLocation } from 'react-router-dom'

import { LOOKUP_PATH } from '../dictionarySearch'

/** Dictionary lookup now lives on the merged "Dịch" screen; old links keep their search. */
export default function Scr024DictionarySearchPage() {
  const location = useLocation()
  return <Navigate replace to={`${LOOKUP_PATH}${location.search}`} />
}
