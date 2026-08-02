import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { ValidationProvider } from './features/validation/ValidationContext'
import { AppLayout } from './layouts/AppLayout'
import { MethodologyPage } from './pages/MethodologyPage'
import { AccessibilityPage, PrivacyPage, TermsPage } from './pages/PolicyPages'
import { ResultPage } from './pages/ResultPage'
import { UploadPage } from './pages/UploadPage'
const queryClient = new QueryClient({ defaultOptions: { mutations: { retry: false } } })
export default function App() {
  return <QueryClientProvider client={queryClient}><ValidationProvider><BrowserRouter><Routes><Route element={<AppLayout />}><Route path="/" element={<UploadPage />} /><Route path="/validation/result" element={<ResultPage />} /><Route path="/methodology" element={<MethodologyPage />} /><Route path="/privacy" element={<PrivacyPage />} /><Route path="/terms" element={<TermsPage />} /><Route path="/accessibility" element={<AccessibilityPage />} /></Route></Routes></BrowserRouter></ValidationProvider></QueryClientProvider>
}
