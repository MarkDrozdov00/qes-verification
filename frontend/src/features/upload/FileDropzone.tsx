import { FileText, Replace, Trash2, UploadCloud } from 'lucide-react'
import { useRef, useState } from 'react'
import { formatBytes, validatePdfFile } from '../../utils/files'

export function FileDropzone({ file, onFile, error }: { file: File | null; onFile: (file: File | null, error?: string) => void; error?: string }) {
  const input = useRef<HTMLInputElement>(null)
  const [dragging, setDragging] = useState(false)
  const accept = (next?: File) => {
    if (!next) return
    const issue = validatePdfFile(next)
    onFile(issue ? null : next, issue ?? undefined)
  }
  if (file) return <div className="selected-file">
    <div className="file-icon"><FileText aria-hidden="true" /></div><div><strong>{file.name}</strong><span>{formatBytes(file.size)} · PDF</span></div>
    <div className="file-actions"><button type="button" className="icon-button" onClick={() => input.current?.click()} aria-label="Replace document"><Replace /></button><button type="button" className="icon-button" onClick={() => onFile(null)} aria-label="Remove document"><Trash2 /></button></div>
    <input ref={input} hidden type="file" accept=".pdf,application/pdf" onChange={(e) => accept(e.target.files?.[0])} />
  </div>
  return <div>
    <button type="button" className={`dropzone ${dragging ? 'is-dragging' : ''} ${error ? 'has-error' : ''}`} aria-describedby={error ? 'file-error' : 'file-help'}
      onClick={() => input.current?.click()} onDragOver={(e) => { e.preventDefault(); setDragging(true) }} onDragLeave={() => setDragging(false)}
      onDrop={(e) => { e.preventDefault(); setDragging(false); accept(e.dataTransfer.files[0]) }}>
      <span className="upload-symbol"><UploadCloud aria-hidden="true" /></span><strong>Drop your PDF here</strong><span>or click to browse</span><small id="file-help">PDF only · maximum 25 MB</small>
    </button>
    <input ref={input} hidden type="file" accept=".pdf,application/pdf" onChange={(e) => accept(e.target.files?.[0])} />
    {error && <p className="field-error" id="file-error" role="alert">{error}</p>}
  </div>
}
