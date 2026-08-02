import { Info } from 'lucide-react'
export function PrototypeNotice({ children, title = 'Prototype trust data' }: { children?: React.ReactNode; title?: string }) {
  return <aside className="prototype-notice"><Info size={18} aria-hidden="true" /><div><strong>{title}</strong>{typeof children === 'string' || !children ? <p>{children ?? 'Signature, certificate, notary, and Notarity trust results are fixture-backed and do not query production systems.'}</p> : children}</div></aside>
}
