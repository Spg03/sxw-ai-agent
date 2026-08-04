import { Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider, useAuth } from './contexts/AuthContext'
import { ErrorBoundary } from './components/ErrorBoundary'
import Layout from './components/Layout'
import Login from './pages/Login'
import Dashboard from './pages/Dashboard'
import Chat from './pages/Chat'
import Treehole from './pages/Treehole'
import Eval from './pages/Eval'
import Skills from './pages/Skills'
import Traces from './pages/Traces'
import Notes from './pages/Notes'
import Hermes from './pages/Hermes'
import Knowledge from './pages/Knowledge'

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
        <Routes>
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
        </Routes>
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
