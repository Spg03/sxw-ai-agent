import { Routes, Route, Navigate } from 'react-router-dom'
import { lazy, Suspense } from 'react'
import { AuthProvider, useAuth } from './contexts/AuthContext'
import { ErrorBoundary } from './components/ErrorBoundary'
import Layout from './components/Layout'
import Login from './pages/Login'
const Dashboard = lazy(() => import('./pages/Dashboard'))
const Chat = lazy(() => import('./pages/Chat'))
const Treehole = lazy(() => import('./pages/Treehole'))
const Eval = lazy(() => import('./pages/Eval'))
const Skills = lazy(() => import('./pages/Skills'))
const Traces = lazy(() => import('./pages/Traces'))
const Notes = lazy(() => import('./pages/Notes'))
const Hermes = lazy(() => import('./pages/Hermes'))
const Knowledge = lazy(() => import('./pages/Knowledge'))

function AppRoutes() {
  const { user, loading } = useAuth()

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="text-slate-400">Loading...</div>
      </div>
    )
  }

  if (!user) {
    return <Login />
  }

  return (
    <Layout>
      <ErrorBoundary>
        <Suspense fallback={<div className="console-page" role="status">正在加载页面…</div>}><Routes>
          <Route path="/" element={<Dashboard />} />
          <Route path="/chat" element={<Chat />} />
          <Route path="/treehole" element={<Treehole />} />
          <Route path="/notes" element={<Notes />} />
          <Route path="/knowledge" element={<Knowledge />} />
          <Route path="/hermes" element={<Hermes />} />
          <Route path="/eval" element={<Eval />} />
          <Route path="/skills" element={<Skills />} />
          <Route path="/traces" element={<Traces />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes></Suspense>
      </ErrorBoundary>
    </Layout>
  )
}

function App() {
  return (
    <AuthProvider>
      <ErrorBoundary>
        <AppRoutes />
      </ErrorBoundary>
    </AuthProvider>
  )
}

export default App
