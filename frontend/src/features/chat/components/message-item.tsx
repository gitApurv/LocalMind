import { Bot, User } from "lucide-react";
import { cn } from "@/lib/utils";
import type { Message } from "../types";
import { MarkdownContent } from "./markdown-content";
import { ReasoningAccordion } from "./reasoning-accordion";
import { ToolStatusBadge } from "./tool-status-badge";

export interface MessageItemProps {
  message: Message;
  /** True for the assistant message that is still being streamed. */
  isLastStreaming?: boolean;
  reasoningDuration?: number | null;
}

export function MessageItem({
  message,
  isLastStreaming = false,
  reasoningDuration,
}: MessageItemProps) {
  const isUser = message.role === "USER";

  return (
    <div
      className={cn(
        "group flex gap-3.5 px-4 py-4 transition-colors",
        isUser ? "bg-transparent justify-end" : "bg-card/40 border-y border-border/40 justify-start"
      )}
    >
      {!isUser && (
        <div className="w-8 h-8 rounded-lg bg-primary/10 border border-primary/20 text-primary flex items-center justify-center shrink-0 mt-0.5">
          <Bot className="w-4 h-4" />
        </div>
      )}

      <div
        className={cn(
          "flex flex-col",
          isUser ? "items-end max-w-[85%]" : "items-start w-full max-w-3xl"
        )}
      >
        <div className="flex items-center gap-2 mb-1 text-[11px] text-muted-foreground font-medium">
          <span>{isUser ? "You" : "LocalMind"}</span>
          {message.createdAt && (
            <span className="text-[10px] opacity-70">{formatTime(message.createdAt)}</span>
          )}
        </div>

        {isUser ? (
          <div className="rounded-2xl rounded-tr-sm bg-primary/15 border border-primary/25 px-4 py-2.5 text-foreground text-sm leading-relaxed whitespace-pre-wrap shadow-sm">
            {message.content}
          </div>
        ) : (
          <AssistantBody
            message={message}
            isLastStreaming={isLastStreaming}
            reasoningDuration={reasoningDuration}
          />
        )}
      </div>

      {isUser && (
        <div className="w-8 h-8 rounded-lg bg-secondary border border-border flex items-center justify-center shrink-0 text-secondary-foreground text-xs font-semibold mt-0.5">
          <User className="w-4 h-4" />
        </div>
      )}
    </div>
  );
}

function AssistantBody({ message, isLastStreaming, reasoningDuration }: MessageItemProps) {
  const tools = message.toolsUsed ?? [];

  return (
    <div className="w-full space-y-2 text-sm leading-relaxed text-foreground">
      {tools.length > 0 && (
        <div className="flex flex-wrap gap-1.5 my-1">
          {tools.map((tool, index) => (
            <ToolStatusBadge key={`${tool}-${index}`} tool={tool} status="completed" />
          ))}
        </div>
      )}

      {message.reasoningContent && (
        <ReasoningAccordion
          content={message.reasoningContent}
          durationSec={reasoningDuration}
          isStreaming={isLastStreaming && !message.content}
        />
      )}

      {message.content ? (
        <MarkdownContent content={message.content} />
      ) : (
        isLastStreaming &&
        !message.reasoningContent && (
          <div className="flex items-center gap-1.5 text-muted-foreground text-xs italic animate-pulse">
            <span>Generating response...</span>
          </div>
        )
      )}
    </div>
  );
}

function formatTime(isoDate: string): string {
  return new Date(isoDate).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
}
