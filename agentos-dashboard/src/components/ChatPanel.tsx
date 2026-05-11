import { useState, useRef, useEffect } from 'react';
import type { Message, StatusEvent } from '../hooks/useAgentConversation';
import { Send, Terminal, User, Loader2, CheckCircle2, XCircle } from 'lucide-react';

interface ChatPanelProps {
  messages: Message[];
  statusEvents: StatusEvent[];
  onSend: (text: string) => void;
  isProcessing: boolean;
}

export function ChatPanel({ messages, statusEvents, onSend, isProcessing }: ChatPanelProps) {
  const [input, setInput] = useState('');
  const messagesEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, statusEvents]);

  const handleSend = () => {
    if (input.trim() && !isProcessing) {
      onSend(input.trim());
      setInput('');
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  return (
    <div className="h-full flex flex-col bg-panel border-r border-border">
      {/* Header */}
      <div className="flex items-center gap-2 px-4 py-3 border-b border-border">
        <Terminal className="w-4 h-4 text-accent" />
        <span className="text-xs font-semibold text-text uppercase tracking-wider">
          Agent Chat
        </span>
      </div>

      {/* Messages */}
      <div className="flex-1 overflow-y-auto p-3 space-y-3">
        {messages.length === 0 && (
          <div className="flex items-center justify-center h-full px-2">
            <p className="text-text-dim text-xs text-center leading-relaxed">
              Send a message to start interacting with the agent.
              <br />
              <span className="text-accent/80">
                &ldquo;Query sales data and generate a bar chart&rdquo;
              </span>
            </p>
          </div>
        )}
        {messages.map((msg) => (
          <div
            key={msg.id}
            className="animate-slide-in flex"
            style={{
              justifyContent: msg.role === 'user' ? 'flex-end' : 'flex-start',
            }}
          >
            <div
              className={`max-w-[90%] rounded-lg px-3 py-2 text-sm ${
                msg.role === 'user'
                  ? 'bg-accent/10 border border-accent/30 text-text'
                  : 'bg-panel-light border border-border text-text'
              }`}
            >
              <div className="flex items-center gap-1.5 mb-1">
                {msg.role === 'user' ? (
                  <User className="w-3 h-3 text-accent" />
                ) : (
                  <Terminal className="w-3 h-3 text-cyan" />
                )}
                <span className="text-[10px] font-medium uppercase tracking-wider text-text-dim">
                  {msg.role === 'user' ? 'You' : 'Agent'}
                </span>
              </div>
              <p className="leading-relaxed whitespace-pre-wrap break-words">
                {msg.content}
              </p>
            </div>
          </div>
        ))}

        {/* Intermediate status events (thinking, tool calls) */}
        {statusEvents.map((evt) => (
          <div key={evt.id} className="flex justify-start">
            {evt.type === 'thinking' ? (
              <div className="bg-panel-light border border-border rounded-lg px-3 py-2 text-sm max-w-[90%]">
                <div className="flex items-center gap-2">
                  <Loader2 className="w-3 h-3 text-cyan animate-spin shrink-0" />
                  <span className="text-xs text-text-dim">{evt.label}</span>
                </div>
              </div>
            ) : (
              <div className="bg-panel-light border border-border rounded-lg px-3 py-2 text-sm max-w-[90%]">
                <div className="flex items-center gap-1.5 mb-1">
                  <Terminal className="w-3 h-3 text-cyan" />
                  <span className="text-[10px] font-medium uppercase tracking-wider text-text-dim">
                    Tool: {evt.label}
                  </span>
                  {evt.results?.[0]?.success !== false && (
                    <CheckCircle2 className="w-3 h-3 text-accent" />
                  )}
                  {evt.results?.[0]?.success === false && (
                    <XCircle className="w-3 h-3 text-error" />
                  )}
                </div>
                {evt.detail && (
                  <p className="text-[10px] text-text-dim leading-relaxed">{evt.detail}</p>
                )}
                {evt.results && evt.results.length > 0 && (
                  <div className="mt-1 space-y-1">
                    {evt.results.map((r, i) => (
                      <div key={i} className="text-[11px] text-text-dim">
                        <span className={r.success ? 'text-accent' : 'text-error'}>
                          {r.success ? '✓' : '✗'}
                        </span>{' '}
                        {r.toolName} — {r.durationMs}ms
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}
          </div>
        ))}

        <div ref={messagesEndRef} />
      </div>

      {/* Input */}
      <div className="border-t border-border p-3">
        <div className="flex gap-2">
          <textarea
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="Type a message..."
            rows={2}
            className="flex-1 bg-surface border border-border rounded-lg px-3 py-2 text-sm text-text placeholder-text-muted resize-none outline-none focus:border-accent/50 transition-colors"
          />
          <button
            onClick={handleSend}
            disabled={!input.trim() || isProcessing}
            className="self-end p-2 bg-accent/10 border border-accent/30 rounded-lg text-accent hover:bg-accent/20 disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
            aria-label="Send message"
          >
            <Send className="w-4 h-4" />
          </button>
        </div>
      </div>
    </div>
  );
}
