import { useAgentConversation } from './hooks/useAgentConversation';
import { useFileUpload } from './hooks/useFileUpload';
import { ChatPanel } from './components/ChatPanel';
import { PipelineMonitor } from './components/PipelineMonitor';
import { DataVizPanel } from './components/DataVizPanel';
import { FileUploadPanel } from './components/FileUploadPanel';
import { ThumbsUp, ThumbsDown } from 'lucide-react';

function App() {
  const {
    messages,
    statusEvents,
    pipelineStage,
    pipelineMessage,
    chartOption,
    showApproval,
    isProcessing,
    sendMessage,
    approve,
    deny,
    sessionIdRef,
  } = useAgentConversation();

  const fileUpload = useFileUpload(sessionIdRef);

  return (
    <div className="h-screen w-screen flex bg-surface overflow-hidden">
      {/* Sidebar - Chat + File Upload */}
      <div className="w-[380px] min-w-[380px] h-full flex-shrink-0 flex flex-col">
        <ChatPanel
          messages={messages}
          statusEvents={statusEvents}
          onSend={sendMessage}
          isProcessing={isProcessing}
        />
        <FileUploadPanel
          files={fileUpload.files}
          isUploading={fileUpload.isUploading}
          error={fileUpload.error}
          onUpload={fileUpload.uploadFile}
          onRemoveFile={fileUpload.removeFile}
          onClearError={fileUpload.clearError}
        />
      </div>

      {/* Main Area */}
      <div className="flex-1 flex flex-col p-4 gap-4 min-w-0">
        {/* Top: Pipeline Monitor */}
        <div className="flex-shrink-0">
          <PipelineMonitor
            currentStage={pipelineStage}
            message={pipelineMessage}
          />
        </div>

        {/* Bottom: Data Viz + Approval */}
        <div className="flex-1 flex flex-col gap-4 min-h-0">
          <div className="flex-1 min-h-0">
            <DataVizPanel chartOption={chartOption} />
          </div>

          {showApproval && (
            <div className="animate-slide-in bg-warn/5 border border-warn/30 rounded-lg p-4 flex-shrink-0">
              <div className="flex items-center justify-between gap-4">
                <div className="min-w-0">
                  <p className="text-sm font-semibold text-warn">
                    Human Approval Required
                  </p>
                  <p className="text-xs text-text-dim mt-1">
                    The agent wants to export the generated data. Review and
                    approve or deny.
                  </p>
                </div>
                <div className="flex gap-2 flex-shrink-0">
                  <button
                    onClick={deny}
                    className="flex items-center gap-1.5 px-4 py-2 bg-error/10 border border-error/30 rounded-lg text-error text-sm font-medium hover:bg-error/20 transition-colors"
                  >
                    <ThumbsDown className="w-4 h-4" />
                    Deny
                  </button>
                  <button
                    onClick={approve}
                    className="flex items-center gap-1.5 px-4 py-2 bg-accent/10 border border-accent/30 rounded-lg text-accent text-sm font-medium hover:bg-accent/20 transition-colors"
                  >
                    <ThumbsUp className="w-4 h-4" />
                    Approve
                  </button>
                </div>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

export default App;
