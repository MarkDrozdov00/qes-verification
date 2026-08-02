import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { AppFooter } from '../components/AppFooter'

test('renders the public product footer without development terminology', () => {
  render(<MemoryRouter><AppFooter /></MemoryRouter>)
  expect(screen.getByRole('contentinfo')).toBeInTheDocument()
  expect(screen.getByRole('navigation', { name: 'Footer navigation' })).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Privacy' })).toHaveAttribute('href', '/privacy')
  expect(screen.getByRole('link', { name: 'Terms of Use' })).toHaveAttribute('href', '/terms')
  expect(screen.getByRole('link', { name: 'Verification Methodology' })).toHaveAttribute('href', '/methodology')
  expect(screen.getByRole('link', { name: 'Accessibility' })).toHaveAttribute('href', '/accessibility')
  expect(screen.getByText('Documents are processed only for validation and are not permanently stored.')).toBeInTheDocument()
  expect(screen.getByText('Prototype environment')).toBeInTheDocument()
  expect(screen.queryByText(/Phase 3A|Report export coming soon|Real DSS integrity checks/i)).not.toBeInTheDocument()
})
