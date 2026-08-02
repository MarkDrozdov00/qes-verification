import { Link } from 'react-router-dom'

export function AppFooter() {
  return <footer className="app-footer">
    <div className="shell footer-inner">
      <div className="footer-primary">
        <div className="footer-brand">
          <Link to="/" aria-label="Signature Validator home">Signature Validator</Link>
          <p>Documents are processed only for validation and are not permanently stored.</p>
        </div>
        <nav className="footer-nav" aria-label="Footer navigation">
          <Link to="/privacy">Privacy</Link>
          <Link to="/terms">Terms of Use</Link>
          <Link to="/methodology">Verification Methodology</Link>
          <Link to="/accessibility">Accessibility</Link>
        </nav>
      </div>
      <div className="footer-meta">
        <span>© 2026 Signature Validator Prototype</span>
        <span className="environment-label">Prototype environment</span>
      </div>
    </div>
  </footer>
}
