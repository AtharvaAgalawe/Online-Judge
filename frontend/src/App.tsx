import { Route, Routes } from 'react-router-dom'
import { Layout } from './components/Layout'
import { LoginPage } from './pages/LoginPage'
import { RegisterPage } from './pages/RegisterPage'
import { LandingPage } from './pages/LandingPage'
import { ProblemListPage } from './pages/ProblemListPage'
import { ProblemDetailPage } from './pages/ProblemDetailPage'
import { ContestsPage } from './pages/ContestsPage'
import { ContestDetailPage } from './pages/ContestDetailPage'
import { ContestProblemPage } from './pages/ContestProblemPage'
import { RequireAuth } from './auth/RequireAuth'
import { RequireAdmin } from './auth/RequireAdmin'
import { SubmissionResultPage } from './pages/SubmissionResultPage'
import { HistoryPage } from './pages/HistoryPage'
import { AdminProblemListPage } from './pages/admin/AdminProblemListPage'
import { AdminProblemCreatePage } from './pages/admin/AdminProblemCreatePage'
import { AdminProblemEditPage } from './pages/admin/AdminProblemEditPage'
import { AdminSubmissionsPage } from './pages/admin/AdminSubmissionsPage'
import { AdminQueueStatusPage } from './pages/admin/AdminQueueStatusPage'

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
        <Route index element={<LandingPage />} />
        <Route path="problems" element={<ProblemListPage />} />
        <Route path="problems/:slug" element={<ProblemDetailPage />} />
        <Route path="contests" element={<ContestsPage />} />
        <Route path="contests/:id" element={<ContestDetailPage />} />
        <Route
          path="contests/:id/problems/:slug"
          element={
            <RequireAuth>
              <ContestProblemPage />
            </RequireAuth>
          }
        />
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
        <Route
          path="admin/problems"
          element={
            <RequireAuth>
              <RequireAdmin>
                <AdminProblemListPage />
              </RequireAdmin>
            </RequireAuth>
          }
        />
        <Route
          path="admin/problems/new"
          element={
            <RequireAuth>
              <RequireAdmin>
                <AdminProblemCreatePage />
              </RequireAdmin>
            </RequireAuth>
          }
        />
        <Route
          path="admin/problems/:id"
          element={
            <RequireAuth>
              <RequireAdmin>
                <AdminProblemEditPage />
              </RequireAdmin>
            </RequireAuth>
          }
        />
        <Route
          path="admin/submissions"
          element={
            <RequireAuth>
              <RequireAdmin>
                <AdminSubmissionsPage />
              </RequireAdmin>
            </RequireAuth>
          }
        />
        <Route
          path="admin/queue"
          element={
            <RequireAuth>
              <RequireAdmin>
                <AdminQueueStatusPage />
              </RequireAdmin>
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
