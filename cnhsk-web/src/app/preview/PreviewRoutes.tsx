import { Route, Routes } from 'react-router-dom'

import { PreviewFrame } from '@/app/preview/PreviewFrame'
import { PreviewPage } from '@/app/preview/PreviewPage'

export function PreviewRoutes() {
  return (
    <Routes>
      <Route element={<PreviewPage />} index />
      <Route element={<PreviewFrame />} path="frame/:viewId/:state" />
      <Route element={<PreviewPage />} path="*" />
    </Routes>
  )
}
