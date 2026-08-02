import { useMutation } from '@tanstack/react-query'
import { LockKeyhole, Sparkles } from 'lucide-react'
import { FormEvent, useEffect, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { validateDocument, ValidationApiError } from '../api/validationApi'
import { ErrorState } from '../components/ErrorState'
import { FileDropzone } from '../features/upload/FileDropzone'
import { ProcessingSteps } from '../features/upload/ProcessingSteps'
import { useValidationResult } from '../features/validation/ValidationContext'

export function UploadPage() {
  const [file, setFile] = useState<File | null>(null), [fileError, setFileError] = useState(''), [elapsed, setElapsed] = useState(0)
  const { setResult, setSourceFile } = useValidationResult()
  const navigate = useNavigate()
  const location = useLocation()
  const submitting = useRef(false)
  const routeMessage = (location.state as { message?: string } | null)?.message
  const mutation = useMutation({ mutationFn: ({ file }: { file: File }) => validateDocument(file),
    retry: false,
    onSuccess: (data, variables) => { setResult(data); setSourceFile(variables.file); navigate('/validation/result') },
    onSettled: () => { submitting.current = false } })
  useEffect(() => { if (!mutation.isPending) { setElapsed(0); return }; const id = window.setInterval(() => setElapsed((v) => v + 250), 250); return () => window.clearInterval(id) }, [mutation.isPending])
  const submit = (event?: FormEvent) => {
    event?.preventDefault()
    if (submitting.current || mutation.isPending) return
    if (!file) { setFileError('Select a PDF document before validating.'); return }
    submitting.current = true
    mutation.mutate({ file })
  }
  const apiMessage = mutation.error instanceof ValidationApiError ? mutation.error.apiError.message : mutation.error instanceof Error ? mutation.error.message : ''
  return <section className="upload-page"><div className="shell upload-shell">
    <div className="upload-intro"><span className="trust-line"><Sparkles size={16} aria-hidden="true" /> Clear evidence for consequential documents</span>
      <h1>Verify a signed or notarized document</h1>
      <p>Upload a PDF to inspect its embedded signatures, certificate trust, notary information, and available Notarity evidence.</p>
    </div>
    <form className="upload-panel" onSubmit={submit}>
      {routeMessage && !mutation.isPending && <div className="route-message" role="status">{routeMessage}</div>}
      {mutation.isPending ? <ProcessingSteps elapsed={elapsed} /> : <>
        <FileDropzone file={file} error={fileError} onFile={(next, issue) => { setFile(next); setFileError(issue ?? ''); mutation.reset() }} />
        {mutation.isError && <ErrorState message={apiMessage} onRetry={file ? submit : undefined} />}
        <div className="upload-actions"><button type="submit" className="button button-primary" disabled={!file || mutation.isPending}>Validate document</button><button type="button" className="button button-secondary" disabled title="Coming soon">Try sample document <span>Coming soon</span></button></div>
      </>}
      <p className="privacy"><LockKeyhole size={16} aria-hidden="true" />Your document is processed only for validation and is not permanently stored.</p>
    </form>
    <div className="assurance-row"><div><strong>Genuine PDF inspection</strong><span>File structure and pages are checked before validation.</span></div><div><strong>Document fingerprint</strong><span>A SHA-256 fingerprint is calculated for every upload.</span></div><div><strong>Transparent prototype</strong><span>Fixture-backed trust results are always clearly labeled.</span></div></div>
  </div></section>
}
