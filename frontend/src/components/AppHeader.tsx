import { FileCheck2 } from 'lucide-react'
import { Link, NavLink } from 'react-router-dom'

export function AppHeader() {
  return <header className="app-header"><div className="shell header-inner">
    <Link className="brand" to="/" aria-label="Signature Validator home"><span className="brand-mark"><FileCheck2 size={20} /></span><span>Signature Validator</span></Link>
    <nav aria-label="Main navigation"><NavLink to="/methodology">Methodology</NavLink><Link className="button button-small" to="/">Validate document</Link></nav>
  </div></header>
}
