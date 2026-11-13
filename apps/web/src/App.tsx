import { Navigate, Route, Routes, useLocation } from 'react-router-dom'
import AppShell from './app/AppShell'
import { SessionProvider, useSession } from './app/session'
import ArtifactsPage from './pages/ArtifactsPage'
import BaselinesPage from './pages/BaselinesPage'
import DeliveryPage from './pages/DeliveryPage'
import ErrorPage from './pages/ErrorPage'
import KnowledgeDetailPage from './pages/KnowledgeDetailPage'
import KnowledgePage from './pages/KnowledgePage'
import LoginPage from './pages/LoginPage'
import MembersPage from './pages/MembersPage'
import ProjectLayout from './pages/ProjectLayout'
import ProjectsPage from './pages/ProjectsPage'
import RequirementDetailPage from './pages/RequirementDetailPage'
import RequirementsPage from './pages/RequirementsPage'
import SearchPage from './pages/SearchPage'
import SettingsPage from './pages/SettingsPage'
import SetupPage from './pages/SetupPage'
import SolutionPage from './pages/SolutionPage'
import { LoadingState } from './components/ui'

function RequireAuth() {
  const { state } = useSession()
  const location = useLocation()
  if (state.status === 'loading') {
    return <LoadingState label="正在检查登录状态…" />
  }
  if (state.status === 'anonymous') {
    return <Navigate to="/login" state={{ from: location.pathname }} replace />
  }
  return <AppShell />
}

export default function App() {
  return (
    <SessionProvider>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/setup" element={<SetupPage />} />
        <Route element={<RequireAuth />}>
          <Route path="/projects" element={<ProjectsPage />} />
          <Route path="/settings" element={<SettingsPage />} />
          <Route path="/projects/:projectId" element={<ProjectLayout />}>
            <Route index element={<Navigate to="artifacts" replace />} />
            <Route path="artifacts" element={<ArtifactsPage />} />
            <Route path="knowledge" element={<KnowledgePage />} />
            <Route path="knowledge/:documentId" element={<KnowledgeDetailPage />} />
            <Route path="requirements" element={<RequirementsPage />} />
            <Route path="requirements/:workItemId" element={<RequirementDetailPage />} />
            <Route
              path="requirements/:workItemId/solutions/:versionId"
              element={<SolutionPage />}
            />
            <Route path="requirements/:workItemId/delivery" element={<DeliveryPage />} />
            <Route path="baselines" element={<BaselinesPage />} />
            <Route path="search" element={<SearchPage />} />
            <Route path="members" element={<MembersPage />} />
          </Route>
        </Route>
        <Route path="/403" element={<ErrorPage status={403} />} />
        <Route path="/404" element={<ErrorPage status={404} />} />
        <Route path="/" element={<Navigate to="/projects" replace />} />
        <Route path="*" element={<ErrorPage status={404} />} />
      </Routes>
    </SessionProvider>
  )
}
