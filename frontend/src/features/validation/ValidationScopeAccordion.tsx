import { ChevronDown, Info } from 'lucide-react'
import { useId, useState } from 'react'

export function ValidationScopeAccordion({ fixture = false }: { fixture?: boolean }) {
  const [open, setOpen] = useState(false)
  const id = useId()
  if (fixture) return null
  return <aside className="scope-panel">
    <button type="button" aria-expanded={open} aria-controls={id} onClick={() => setOpen(!open)}>
      <Info aria-hidden="true" /><span><strong>How this result was verified</strong><small>EU DSS validates signatures, certificate trust, timestamps, and eIDAS qualification. Notary and Notarity records use prototype datasets.</small></span><ChevronDown aria-hidden="true" />
    </button>
    {open && <div className="scope-panel-details" id={id}>
      <div><strong>Real validation</strong><ul><li>PDF signature integrity</li><li>Certificate chains and EU Trusted Lists</li><li>Revocation evidence and timestamps</li><li>eIDAS qualification</li></ul></div>
      <div><strong>Prototype validation</strong><ul><li>Austrian notary directory</li><li>Notarity registry</li><li>Signed registry audit trail</li></ul></div>
    </div>}
  </aside>
}
