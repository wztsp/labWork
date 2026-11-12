import { useRef, useState } from 'react';
import { Box, Typography, CircularProgress, Alert } from '@mui/material';
import UploadFileIcon from '@mui/icons-material/UploadFile';
import api from '../api/client';

interface Props {
  onUploaded: (fileId: number) => void;
}

export default function LogUploadZone({ onUploaded }: Props) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [dragging, setDragging] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const doUpload = async (file: File) => {
    setUploading(true);
    setError(null);
    try {
      const res = await api.uploadLog(file);
      onUploaded(res.file.id);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setUploading(false);
    }
  };

  return (
    <Box
      onDragOver={(e) => {
        e.preventDefault();
        setDragging(true);
      }}
      onDragLeave={() => setDragging(false)}
      onDrop={(e) => {
        e.preventDefault();
        setDragging(false);
        const f = e.dataTransfer.files?.[0];
        if (f) doUpload(f);
      }}
      sx={{
        border: '2px dashed',
        borderColor: dragging ? 'primary.light' : 'divider',
        borderRadius: 2,
        p: 3,
        textAlign: 'center',
        cursor: 'pointer',
        bgcolor: dragging ? 'action.hover' : 'background.paper',
        mb: 2,
      }}
      onClick={() => inputRef.current?.click()}
    >
      <input
        ref={inputRef}
        type="file"
        accept=".log,.txt,.out,.err"
        hidden
        onChange={(e) => {
          const f = e.target.files?.[0];
          if (f) doUpload(f);
          e.target.value = '';
        }}
      />
      {uploading ? (
        <CircularProgress size={28} />
      ) : (
        <>
          <UploadFileIcon fontSize="large" color="primary" />
          <Typography sx={{ mt: 1 }}>
            点击或拖拽日志文件到此处上传（支持 .log/.txt/.out/.err，≤50MB）
          </Typography>
        </>
      )}
      {error && (
        <Alert severity="error" sx={{ mt: 1 }}>
          {error}
        </Alert>
      )}
    </Box>
  );
}
