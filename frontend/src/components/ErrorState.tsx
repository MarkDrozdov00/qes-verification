import { AlertCircle } from 'lucide-react'
export function ErrorState({ title = 'Validation could not be completed', message, onRetry }: { title?: string; message: string; onRetry?: () => void }) {
  return <div className="error-state" role="alert"><AlertCircle aria-hidden="true" /><div><strong>{title}</strong><p>{message}</p>{onRetry && <button className="text-button" onClick={onRetry}>Try again</button>}</div></div>
}
