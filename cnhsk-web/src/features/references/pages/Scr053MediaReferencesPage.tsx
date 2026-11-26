import { Navigate } from 'react-router-dom'

/** Video and podcast links were retired; old bookmarks land on the HSK book catalogue. */
export default function Scr053MediaReferencesPage() {
  return <Navigate replace to="/references/books" />
}
