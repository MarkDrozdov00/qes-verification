export function EvidenceRow({ label, value, mono = false }: { label: string; value?: React.ReactNode; mono?: boolean }) {
  return <div className="evidence-row"><dt>{label}</dt><dd className={mono ? 'mono' : ''}>{value || 'Not available'}</dd></div>
}
