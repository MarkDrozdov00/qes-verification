import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { OverallStatusHero } from '../features/validation/OverallStatusHero'
import { result } from './fixtures'

const file = new File(['%PDF-report-source'], 'contract.pdf', { type: 'application/pdf' })

beforeEach(() => {
  vi.restoreAllMocks()
  Object.defineProperty(URL, 'createObjectURL', { configurable: true, value: vi.fn(() => 'blob:report') })
  Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: vi.fn() })
  vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined)
})

test('report button prevents repeated downloads, shows loading, and downloads the response', async () => {
  let resolveResponse!: (response: Response) => void
  const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(() => new Promise((resolve) => { resolveResponse = resolve }))
  render(<MemoryRouter><OverallStatusHero result={result('VERIFIED')} sourceFile={file} /></MemoryRouter>)
  const button = screen.getByRole('button', { name: 'Download verification report' })
  fireEvent.click(button)
  expect(button).toBeDisabled()
  expect(button).toHaveTextContent('Generating verification report')
  fireEvent.click(button)
  expect(fetchMock).toHaveBeenCalledTimes(1)
  resolveResponse(new Response(new Blob(['%PDF']), { status: 200, headers: { 'Content-Type': 'application/pdf', 'Content-Disposition': 'attachment; filename="contract-verification-report.pdf"' } }))
  expect(await screen.findByRole('status')).toHaveTextContent('Verification report generated.')
  expect(HTMLAnchorElement.prototype.click).toHaveBeenCalledTimes(1)
})

test('report generation failure is announced and allows retry', async () => {
  vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({ code: 'REPORT_GENERATION_FAILED', message: 'The verification report could not be generated.' }), { status: 500, headers: { 'Content-Type': 'application/json' } }))
  render(<MemoryRouter><OverallStatusHero result={result('VERIFIED')} sourceFile={file} /></MemoryRouter>)
  fireEvent.click(screen.getByRole('button', { name: 'Download verification report' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('The verification report could not be generated.')
  await waitFor(() => expect(screen.getByRole('button', { name: 'Download verification report' })).not.toBeDisabled())
})
