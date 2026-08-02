import { fireEvent, render, screen } from '@testing-library/react'
import { MethodologyPage } from '../pages/MethodologyPage'

test('renders the implementation overview and current notary snapshot', () => {
  render(<MethodologyPage />)

  expect(screen.getByRole('heading', { name: 'Implementation Overview' })).toBeInTheDocument()
  expect(screen.getByText('Dr. Walter Bajlicz')).toBeInTheDocument()
  expect(screen.queryByText(/Phase 3A|Fixture mode|VALIDATION_MODE/i)).not.toBeInTheDocument()
})

test('keeps the technical stack collapsed until requested', () => {
  render(<MethodologyPage />)

  const toggle = screen.getByRole('button', { name: /Technical Stack/i })
  expect(toggle).toHaveAttribute('aria-expanded', 'false')
  expect(screen.queryByText('Java 21 · Spring Boot')).not.toBeInTheDocument()

  fireEvent.click(toggle)
  expect(toggle).toHaveAttribute('aria-expanded', 'true')
  expect(screen.getByText('Java 21 · Spring Boot')).toBeInTheDocument()
})
