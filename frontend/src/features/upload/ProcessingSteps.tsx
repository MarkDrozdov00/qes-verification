import { Check, LoaderCircle } from 'lucide-react'
const steps = ['Reading document', 'Inspecting embedded signatures', 'Verifying document integrity', 'Checking signer certificates', 'Checking notary information', 'Looking for a Notarity record']
export function ProcessingSteps({ elapsed }: { elapsed: number }) {
  const active = Math.min(Math.floor(elapsed / 450), steps.length - 1)
  return <div className="processing" role="status" aria-live="polite"><div className="processing-head"><LoaderCircle className="spin" aria-hidden="true" /><div><strong>Validating document</strong><span>{steps[active]}</span></div></div>
    <ol>{steps.map((step, i) => <li key={step} className={i < active ? 'done' : i === active ? 'active' : ''}><span>{i < active ? <Check /> : i + 1}</span>{step}</li>)}</ol>
  </div>
}
