import { useState, useCallback } from 'react';

export interface UploadedFileInfo {
  fileId: string;
  fileName: string;
  fileType: string;
  rowCount: number;
  headers: string[];
  preview: Record<string, string>[];
}

export interface UploadState {
  files: UploadedFileInfo[];
  isUploading: boolean;
  error: string | null;
}

export function useFileUpload(
  sessionIdRef: React.MutableRefObject<string | null>,
) {
  const [state, setState] = useState<UploadState>({
    files: [],
    isUploading: false,
    error: null,
  });

  const uploadFile = useCallback(async (file: File) => {
    setState(prev => ({ ...prev, isUploading: true, error: null }));

    try {
      const formData = new FormData();
      formData.append('file', file);
      if (sessionIdRef.current) {
        formData.append('sessionId', sessionIdRef.current);
      }

      const res = await fetch('/api/upload', { method: 'POST', body: formData });
      const json = await res.json();

      if (!json.success) {
        setState(prev => ({ ...prev, isUploading: false, error: json.message as string }));
        return;
      }

      if (json.sessionId) {
        sessionIdRef.current = json.sessionId as string;
      }

      const info: UploadedFileInfo = {
        fileId: json.fileId as string,
        fileName: json.fileName as string,
        fileType: json.fileType as string,
        rowCount: json.rowCount as number,
        headers: json.headers as string[],
        preview: json.preview as Record<string, string>[],
      };

      setState(prev => ({ files: [...prev.files, info], isUploading: false, error: null }));
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Upload failed';
      setState(prev => ({ ...prev, isUploading: false, error: msg }));
    }
  }, [sessionIdRef]);

  const removeFile = useCallback((fileId: string) => {
    setState(prev => ({ ...prev, files: prev.files.filter(f => f.fileId !== fileId) }));
  }, []);

  const clearError = useCallback(() => {
    setState(prev => ({ ...prev, error: null }));
  }, []);

  return { ...state, uploadFile, removeFile, clearError } as const;
}
