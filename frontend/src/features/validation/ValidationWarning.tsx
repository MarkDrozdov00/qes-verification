import { ChevronDown, TriangleAlert } from 'lucide-react'
import { useId, useState } from 'react'
import type { ValidationMessage } from '../../types/validation'
import { friendlyValidationMessage } from './dssMessageMappings'

export function ValidationWarning({ warning }: { warning: ValidationMessage }) {
  const [open, setOpen] = useState(false)
  const detailId = useId()
  const friendly = friendlyValidationMessage(warning)
  return <article className={`validation-warning severity-${warning.severity.toLowerCase()}`}>
    <TriangleAlert aria-hidden="true" />
    <div>
      <strong>{friendly.title}</strong>
      {friendly.status && <span className="warning-status">{friendly.status}</span>}
      <p>{friendly.message}</p>
      {friendly.technicalCode && <div className="warning-technical">
        <button type="button" aria-expanded={open} aria-controls={detailId} onClick={() => setOpen(!open)}>
          Technical detail <ChevronDown aria-hidden="true" />
        </button>
        {open && <code id={detailId}>{friendly.technicalCode}</code>}
      </div>}
    </div>
  </article>
}
