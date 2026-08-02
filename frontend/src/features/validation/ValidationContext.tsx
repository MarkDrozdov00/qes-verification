import { createContext, useContext, useMemo, useState, type ReactNode } from 'react'
import type { DocumentValidationResponse } from '../../types/validation'

interface ValidationState { result: DocumentValidationResponse | null; setResult: (value: DocumentValidationResponse | null) => void; sourceFile: File | null; setSourceFile: (value: File | null) => void }
const Context = createContext<ValidationState | null>(null)
export function ValidationProvider({ children }: { children: ReactNode }) {
  const [result, setResult] = useState<DocumentValidationResponse | null>(null)
  const [sourceFile, setSourceFile] = useState<File | null>(null)
  return <Context.Provider value={useMemo(() => ({ result, setResult, sourceFile, setSourceFile }), [result, sourceFile])}>{children}</Context.Provider>
}
export function useValidationResult() {
  const value = useContext(Context)
  if (!value) throw new Error('ValidationProvider is missing')
  return value
}
