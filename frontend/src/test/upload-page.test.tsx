import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { vi } from 'vitest'
import { validateDocument } from '../api/validationApi'
import { ValidationProvider } from '../features/validation/ValidationContext'
import { UploadPage } from '../pages/UploadPage'

vi.mock('../api/validationApi', async (importOriginal) => {
  const original = await importOriginal<typeof import('../api/validationApi')>()
  return { ...original, validateDocument: vi.fn(() => new Promise(() => undefined)) }
})

test('one submit action starts exactly one validation request', async () => {
  const client = new QueryClient({ defaultOptions: { mutations: { retry: false } } })
  const view = render(
    <QueryClientProvider client={client}>
      <ValidationProvider>
        <MemoryRouter>
          <UploadPage />
        </MemoryRouter>
      </ValidationProvider>
    </QueryClientProvider>,
  )
  const fileInput = view.container.querySelector('input[type="file"]') as HTMLInputElement
  fireEvent.change(fileInput, {
    target: { files: [new File(['%PDF-1.4'], 'signed.pdf', { type: 'application/pdf' })] },
  })

  const submit = screen.getByRole('button', { name: 'Validate document' })
  fireEvent.click(submit)
  fireEvent.click(submit)

  await waitFor(() => expect(validateDocument).toHaveBeenCalledTimes(1))
  expect(screen.getByRole('status')).toHaveTextContent('Validating document')
})
