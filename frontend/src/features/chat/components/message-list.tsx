"use client";

import * as React from "react";
import { useChatStore } from "../store/chat-store";
import { MessageItem } from "./message-item";
import { ToolStatusBadge } from "./tool-status-badge";

/** The thread's messages plus the live tool badge, kept scrolled to the newest token. */
export function MessageList() {
  const messages = useChatStore((s) => s.messages);
  const isStreaming = useChatStore((s) => s.isStreaming);
  const activeTool = useChatStore((s) => s.activeTool);
  const reasoningDuration = useChatStore((s) => s.reasoningDuration);
  const endRef = React.useRef<HTMLDivElement | null>(null);

  React.useEffect(() => {
    // Smooth scrolling lags behind fast token streams, so jump while streaming.
    endRef.current?.scrollIntoView?.({ behavior: isStreaming ? "auto" : "smooth" });
  }, [messages, activeTool, isStreaming]);

  const lastIndex = messages.length - 1;

  return (
    <div className="py-2 divide-y divide-border/20">
      {messages.map((message, index) => (
        <MessageItem
          key={message.id || `msg-${index}`}
          message={message}
          isLastStreaming={isStreaming && index === lastIndex}
          reasoningDuration={index === lastIndex ? reasoningDuration : undefined}
        />
      ))}

      {isStreaming && activeTool && (
        <div className="px-6 py-2.5 bg-card/20 flex items-center gap-2">
          <ToolStatusBadge tool={activeTool} status="executing" />
        </div>
      )}

      <div ref={endRef} />
    </div>
  );
}
