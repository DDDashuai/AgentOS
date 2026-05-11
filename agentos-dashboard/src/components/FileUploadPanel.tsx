import { useRef, useState } from 'react';
import { Upload, FileText, X, Loader2, AlertCircle } from 'lucide-react';
import type { UploadedFileInfo } from '../hooks/useFileUpload';

interface FileUploadPanelProps {
  files: UploadedFileInfo[];
  isUploading: boolean;
  error: string | null;
  onUpload: (file: File) => void;
  onRemoveFile: (fileId: string) => void;
  onClearError: () => void;
}

const ALLOWED_TYPES = '.csv,.xlsx,.pdf';

export function FileUploadPanel({
  files, isUploading, error,
  onUpload, onRemoveFile, onClearError,
}: FileUploadPanelProps) {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [isDragOver, setIsDragOver] = useState(false);

  const handleFile = (file: File) => {
    const ext = '.' + file.name.split('.').pop()?.toLowerCase();
    if (!['.csv', '.xlsx', '.pdf'].includes(ext)) {
      alert('Unsupported file type. Allowed: .csv, .xlsx, .pdf');
      return;
    }
    onUpload(file);
  };

  return (
    <div className="border-t border-border p-3 space-y-2">
      <div
        onDragOver={(e) => { e.preventDefault(); setIsDragOver(true); }}
        onDragLeave={() => setIsDragOver(false)}
        onDrop={(e) => { e.preventDefault(); setIsDragOver(false); const f = e.dataTransfer.files?.[0]; if (f) handleFile(f); }}
        onClick={() => fileInputRef.current?.click()}
        className={`border-2 border-dashed rounded-lg p-3 text-center cursor-pointer transition-colors ${
          isDragOver ? 'border-accent bg-accent/5' : 'border-border hover:border-accent/50 hover:bg-surface'
        }`}
      >
        <input
          ref={fileInputRef}
          type="file"
          accept={ALLOWED_TYPES}
          className="hidden"
          onChange={(e) => { const f = e.target.files?.[0]; if (f) handleFile(f); e.target.value = ''; }}
        />
        {isUploading ? (
          <div className="flex items-center justify-center gap-2">
            <Loader2 className="w-4 h-4 animate-spin text-accent" />
            <span className="text-xs text-text-dim">Uploading...</span>
          </div>
        ) : (
          <div className="flex items-center justify-center gap-2">
            <Upload className="w-4 h-4 text-text-dim" />
            <span className="text-xs text-text-dim">Drop file or click to upload</span>
          </div>
        )}
      </div>

      {error && (
        <div className="flex items-center gap-2 px-3 py-2 bg-red-900/20 border border-red-700/30 rounded-lg">
          <AlertCircle className="w-3 h-3 text-red-400 shrink-0" />
          <span className="text-xs text-red-400 flex-1">{error}</span>
          <button onClick={onClearError} className="text-red-400/60 hover:text-red-400">
            <X className="w-3 h-3" />
          </button>
        </div>
      )}

      {files.length > 0 && (
        <div className="space-y-1 max-h-[200px] overflow-y-auto">
          {files.map((f) => (
            <div key={f.fileId}
              className="flex items-center gap-2 px-2 py-1.5 bg-surface border border-border rounded-lg text-xs"
            >
              <FileText className="w-3 h-3 text-accent shrink-0" />
              <div className="flex-1 min-w-0">
                <p className="truncate text-text font-medium">{f.fileName}</p>
                <p className="text-text-dim">{f.rowCount} rows, {f.headers.length} columns</p>
              </div>
              <button
                onClick={() => onRemoveFile(f.fileId)}
                className="text-text-dim hover:text-red-400 transition-colors shrink-0"
                aria-label={`Remove ${f.fileName}`}
              >
                <X className="w-3 h-3" />
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
