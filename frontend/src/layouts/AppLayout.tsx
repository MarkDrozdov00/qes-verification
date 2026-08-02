import { Outlet } from 'react-router-dom'
import { AppFooter } from '../components/AppFooter'
import { AppHeader } from '../components/AppHeader'
export function AppLayout() { return <div className="app"><AppHeader /><main><Outlet /></main><AppFooter /></div> }
