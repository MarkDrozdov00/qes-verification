import { fireEvent, render, screen } from '@testing-library/react'
import { FileDropzone } from '../features/upload/FileDropzone'
import { ProcessingSteps } from '../features/upload/ProcessingSteps'
import { ErrorState } from '../components/ErrorState'
import { validatePdfFile } from '../utils/files'

test('validates upload type and size', () => {
  expect(validatePdfFile(new File(['x'], 'note.txt', { type: 'text/plain' }))).toMatch(/PDF/)
  expect(validatePdfFile(new File(['x'], 'document.pdf', { type: 'application/pdf' }))).toBeNull()
})
test('shows selected file and supports remove', () => {
  const onFile = vi.fn()
  render(<FileDropzone file={new File(['pdf'], 'agreement.pdf', { type: 'application/pdf' })} onFile={onFile} />)
  expect(screen.getByText('agreement.pdf')).toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: /remove document/i }))
  expect(onFile).toHaveBeenCalledWith(null)
})
test('renders meaningful processing state', () => {
  render(<ProcessingSteps elapsed={0} />)
  expect(screen.getByRole('status')).toHaveTextContent('Reading document')
  expect(screen.getByText('Looking for a Notarity record')).toBeInTheDocument()
})
test('renders an actionable API error', () => {
  render(<ErrorState message="The PDF is invalid or could not be read." onRetry={() => undefined} />)
  expect(screen.getByRole('alert')).toHaveTextContent('The PDF is invalid')
  expect(screen.getByRole('button', { name: /try again/i })).toBeEnabled()
})
