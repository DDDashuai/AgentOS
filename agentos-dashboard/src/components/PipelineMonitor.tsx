import type { PipelineStage } from '../hooks/useAgentConversation';
import { CheckCircle2, Circle, Loader2, AlertTriangle } from 'lucide-react';

interface PipelineMonitorProps {
  currentStage: PipelineStage;
  message: string;
}

interface StageDef {
  key: PipelineStage;
  label: string;
}

const DISPLAY_STAGES: StageDef[] = [
  { key: 'validation', label: 'Validation' },
  { key: 'partitioning', label: 'Partitioning' },
  { key: 'tool_execution', label: 'Tool Execution' },
  { key: 'hitl_pending', label: 'HITL Pending' },
  { key: 'completed', label: 'Completed' },
];

const ORDER: PipelineStage[] = [
  'idle',
  'validation',
  'partitioning',
  'tool_execution',
  'hitl_pending',
  'completed',
];

function idx(stage: PipelineStage): number {
  return ORDER.indexOf(stage);
}

export function PipelineMonitor({ currentStage, message }: PipelineMonitorProps) {
  const currentIdx = idx(currentStage);

  return (
    <div className="bg-panel border border-border rounded-lg p-4">
      {/* Header */}
      <div className="flex items-center gap-2 mb-4">
        <span className="relative flex h-2 w-2">
          {currentStage !== 'idle' && currentStage !== 'completed' && (
            <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-cyan opacity-75" />
          )}
          <span
            className={`relative inline-flex rounded-full h-2 w-2 ${
              currentStage === 'idle'
                ? 'bg-text-muted'
                : currentStage === 'completed'
                ? 'bg-accent'
                : 'bg-cyan'
            }`}
          />
        </span>
        <span className="text-xs font-semibold text-text uppercase tracking-wider">
          Execution Pipeline
        </span>
      </div>

      {/* Stage nodes */}
      <div className="flex items-center">
        {DISPLAY_STAGES.map((s, i) => {
          const stageIdx = idx(s.key);
          const active = s.key === currentStage && currentStage !== 'idle';
          const done = currentIdx > stageIdx;
          const isHitlActive = s.key === 'hitl_pending' && active;

          return (
            <div key={s.key} className="flex items-center flex-1 min-w-0">
              <div
                className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md text-[11px] font-medium transition-all whitespace-nowrap ${
                  done
                    ? 'bg-accent/10 border border-accent/30 text-accent'
                    : active
                    ? isHitlActive
                      ? 'bg-warn/10 border border-warn/50 text-warn animate-pulse-border'
                      : 'bg-cyan/10 border border-cyan/50 text-cyan animate-pulse-border'
                    : 'bg-surface border border-border text-text-dim'
                }`}
              >
                {done ? (
                  <CheckCircle2 className="w-3 h-3 shrink-0" />
                ) : active ? (
                  isHitlActive ? (
                    <AlertTriangle className="w-3 h-3 shrink-0" />
                  ) : (
                    <Loader2 className="w-3 h-3 shrink-0 animate-spin" />
                  )
                ) : (
                  <Circle className="w-3 h-3 shrink-0" />
                )}
                <span className="truncate">{s.label}</span>
              </div>
              {i < DISPLAY_STAGES.length - 1 && (
                <div
                  className={`flex-1 h-[2px] mx-2 transition-colors duration-500 ${
                    currentIdx > stageIdx ? 'bg-accent' : 'bg-border'
                  }`}
                />
              )}
            </div>
          );
        })}
      </div>

      {/* Status message */}
      {message && (
        <div className="mt-4 pt-3 border-t border-border">
          <p className="text-xs text-text-dim font-mono">{message}</p>
        </div>
      )}
    </div>
  );
}
