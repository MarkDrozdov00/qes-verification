import { Check, Copy } from 'lucide-react'
import { useEffect, useState } from 'react'

export function CopyButton({ value, label }: { value?: string; label: string }) {
  const [copied, setCopied] = useState(false)
  useEffect(() => { if (!copied) return; const timer = window.setTimeout(() => setCopied(false), 1800); return () => window.clearTimeout(timer) }, [copied])
  if (!value) return null
  async function copy() {
    await navigator.clipboard.writeText(value!)
    setCopied(true)
  }
  return <button type="button" className="copy-button" onClick={copy} aria-label={`Copy ${label}`}>
    {copied ? <Check aria-hidden="true" /> : <Copy aria-hidden="true" />}<span>{copied ? 'Copied' : 'Copy'}</span>
    <span className="sr-only" aria-live="polite">{copied ? `${label} copied` : ''}</span>
  </button>
}
