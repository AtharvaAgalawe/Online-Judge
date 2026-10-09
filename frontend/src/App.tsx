import { Route, Routes } from 'react-router-dom'
import { Layout } from './components/Layout'
import { LoginPage } from './pages/LoginPage'
import { RegisterPage } from './pages/RegisterPage'
import { ProblemListPage } from './pages/ProblemListPage'
import { ProblemDetailPage } from './pages/ProblemDetailPage'
import { RequireAuth } from './auth/RequireAuth'
import { SubmissionResultPage } from './pages/SubmissionResultPage'
import { HistoryPage } from './pages/HistoryPage'

function NotFoundPage() {
  return (
    <section>
      <h1>Not found</h1>
    </section>
  )
}

function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<ProblemListPage />} />
        <Route path="problems/:slug" element={<ProblemDetailPage />} />
        <Route
          path="submissions/:id"
          element={
            <RequireAuth>
              <SubmissionResultPage />
            </RequireAuth>
          }
        />
        <Route
          path="submissions"
          element={
            <RequireAuth>
              <HistoryPage />
            </RequireAuth>
          }
        />
        <Route path="login" element={<LoginPage />} />
        <Route path="register" element={<RegisterPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}

export default App
