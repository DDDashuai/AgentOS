import { useState, useCallback, useEffect, useRef } from 'react';

export type PipelineStage =
  | 'idle'
  | 'validation'
  | 'partitioning'
  | 'tool_execution'
  | 'hitl_pending'
  | 'completed';

export interface ToolResult {
  toolName: string;
  success: boolean;
  output: string;
  durationMs: number;
  toolCallId: string | null;
}

export interface Message {
  id: string;
  role: 'user' | 'assistant';
  content: string;
}

/** An intermediate event shown as a status line in the chat (thinking, tool call). */
export interface StatusEvent {
  id: string;
  type: 'thinking' | 'tool_result';
  label: string;
  detail?: string;
  results?: ToolResult[];
}

export function useAgentConversation() {
  const [messages, setMessages] = useState<Message[]>([]);
  const [statusEvents, setStatusEvents] = useState<StatusEvent[]>([]);
  const [pipelineStage, setPipelineStage] = useState<PipelineStage>('idle');
  const [pipelineMessage, setPipelineMessage] = useState('');
  const [chartOption, setChartOption] = useState<Record<string, unknown> | null>(null);
  const [showApproval, setShowApproval] = useState(false);
  const [pendingTool, setPendingTool] = useState<string | null>(null);
  const [isProcessing, setIsProcessing] = useState(false);

  const abortRef = useRef<AbortController | null>(null);
  const sessionIdRef = useRef<string | null>(null);
  const isProcessingRef = useRef(false);
  const pendingToolRef = useRef<string | null>(null);
  const statusIdCounter = useRef(0);

  useEffect(() => { pendingToolRef.current = pendingTool; }, [pendingTool]);

  const addStatusEvent = useCallback((type: StatusEvent['type'], label: string, detail?: string, results?: ToolResult[]) => {
    const id = `status-${++statusIdCounter.current}`;
    setStatusEvents((prev) => [...prev, { id, type, label, detail, results }]);
  }, []);

  const sendMessage = useCallback(async (text: string) => {
    if (!text.trim() || isProcessingRef.current) return;
    isProcessingRef.current = true;
    setIsProcessing(true);
    setShowApproval(false);
    setChartOption(null);
    setPipelineStage('validation');
    setPipelineMessage('Sending request...');
    setStatusEvents([]);

    const userMsg: Message = {
      id: crypto.randomUUID(),
      role: 'user',
      content: text.trim(),
    };
    setMessages((prev) => [...prev, userMsg]);

    const controller = new AbortController();
    abortRef.current = controller;

    try {
      const res = await fetch('/api/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          message: text.trim(),
          sessionId: sessionIdRef.current ?? '',
        }),
        signal: controller.signal,
      });

      if (!res.ok) {
        throw new Error(`HTTP ${res.status}: ${res.statusText}`);
      }

      const reader = res.body!.getReader();
      const decoder = new TextDecoder();
      let buffer = '';

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });

        // Split on double newline (SSE event boundary)
        const parts = buffer.split('\n\n');
        buffer = parts.pop() ?? '';

        for (const part of parts) {
          // Find the line starting with "data:" (handle both "data:{...}" and "data: {...}")
          const dataLine = part
            .split('\n')
            .find((l) => l.startsWith('data:'));
          if (!dataLine) continue;

          // Strip "data:" prefix and leading whitespace
          const raw = dataLine.slice(5).trim();
          if (!raw) continue;

          const json = JSON.parse(raw);

          switch (json.type) {
            case 'thinking': {
              setPipelineStage('validation');
              setPipelineMessage(json.thought as string);
              addStatusEvent('thinking', json.thought as string);
              break;
            }
            case 'tool_execution': {
              setPipelineStage('tool_execution');
              setPipelineMessage('Executing tools...');
              const results = json.results as ToolResult[] | undefined;
              if (results) {
                for (const r of results) {
                  addStatusEvent('tool_result', r.toolName, `(success=${r.success}, ${r.durationMs}ms)`, results);
                  // Check for chart visualization output
                  if (
                    r.toolName === 'data_visualization' &&
                    r.success &&
                    r.output
                  ) {
                    try {
                      const parsed = JSON.parse(r.output);
                      if (parsed.option) {
                        setChartOption(parsed.option);
                      }
                    } catch {
                      // not a chart output, skip
                    }
                  }
                }
              }
              break;
            }
            case 'hitl_required': {
              setPipelineStage('hitl_pending');
              setPipelineMessage(json.message as string);
              setPendingTool(json.toolName as string);
              setShowApproval(true);
              break;
            }
            case 'final': {
              setPipelineStage('completed');
              setPipelineMessage('Completed');
              if (json.sessionId) {
                const sid = json.sessionId as string;
                sessionIdRef.current = sid;
              }
              setMessages((prev) => [
                ...prev,
                {
                  id: crypto.randomUUID(),
                  role: 'assistant',
                  content: json.response as string,
                },
              ]);
              break;
            }
          }
        }
      }
    } catch (err: unknown) {
      if (err instanceof DOMException && err.name === 'AbortError') return;
      console.error('Chat stream error:', err);
      setMessages((prev) => [
        ...prev,
        {
          id: crypto.randomUUID(),
          role: 'assistant',
          content: `⚠️ Connection error: ${err instanceof Error ? err.message : 'Unknown error'}`,
        },
      ]);
    } finally {
      isProcessingRef.current = false;
      setIsProcessing(false);
      abortRef.current = null;
    }
  }, [addStatusEvent]);

  const approve = useCallback(async () => {
    const tool = pendingToolRef.current;
    const sid = sessionIdRef.current;
    if (!tool || !sid) return;

    setShowApproval(false);

    try {
      await fetch('/api/chat/approve', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ sessionId: sid, toolName: tool }),
      });
    } catch (err) {
      console.error('Approval request failed:', err);
    }

    sendMessage(`Proceed with ${tool}`);
  }, [sendMessage]);

  const deny = useCallback(() => {
    abortRef.current?.abort();
    setShowApproval(false);
    setPendingTool(null);
    setPipelineStage('idle');
    setPipelineMessage('');
    setChartOption(null);
    isProcessingRef.current = false;
    setIsProcessing(false);
    setStatusEvents([]);

    setMessages((prev) => [
      ...prev,
      {
        id: crypto.randomUUID(),
        role: 'assistant',
        content: '❌ Operation denied by user.',
      },
    ]);
  }, []);

  return {
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
  } as const;
}
