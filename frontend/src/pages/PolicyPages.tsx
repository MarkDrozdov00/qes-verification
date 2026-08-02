function PolicyPage({ title, introduction, children }: { title: string; introduction: string; children: React.ReactNode }) {
  return <div className="content-page"><div className="shell narrow">
    <h1>{title}</h1>
    <p className="lead">{introduction}</p>
    {children}
  </div></div>
}

export function PrivacyPage() {
  return <PolicyPage title="Privacy" introduction="Documents are processed only for validation and are not permanently stored.">
    <section><h2>Document processing</h2><p>Uploaded PDFs are processed in memory to inspect signatures, calculate the document fingerprint, and produce the requested validation result. The uploaded document is not added to a document archive or validation history.</p></section>
    <section><h2>Validation evidence</h2><p>The result may display certificate metadata and signer information already contained in the uploaded document. Prototype registry checks use only the document fingerprint and certificate fingerprints required to compare evidence.</p></section>
  </PolicyPage>
}

export function TermsPage() {
  return <PolicyPage title="Terms of Use" introduction="Signature Validator provides evidence about electronic signatures and related trust checks.">
    <section><h2>Use of results</h2><p>Validation results describe the evidence available at the time of validation. They support review and decision-making but do not replace the original electronically signed document or independent legal advice.</p></section>
    <section><h2>Prototype datasets</h2><p>Notary-directory and Notarity registry results are clearly identified as static or prototype evidence and must not be interpreted as live production-registry responses.</p></section>
  </PolicyPage>
}

export function AccessibilityPage() {
  return <PolicyPage title="Accessibility" introduction="Signature Validator is designed to work with keyboards, assistive technology, and a range of screen sizes.">
    <section><h2>Using the service</h2><p>Interactive controls provide visible focus states, status is communicated with text as well as color, and expandable evidence sections expose their state to assistive technology.</p></section>
    <section><h2>Accessible documents</h2><p>The source PDF may have its own accessibility characteristics that are outside this service’s control. Validation results remain available as structured web content.</p></section>
  </PolicyPage>
}
