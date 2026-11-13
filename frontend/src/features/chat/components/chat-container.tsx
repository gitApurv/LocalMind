"use client";

import * as React from "react";
import { AttachmentsPanel } from "@/features/attachments/components/attachments-panel";
import { useConversationStore } from "@/features/conversations/store/conversation-store";
import { useChatStream } from "../hooks/use-chat-stream";
import { useChatStore } from "../store/chat-store";
import { ChatComposer } from "./chat-composer";
import { ChatHeader } from "./chat-header";
import { ChatWelcome } from "./chat-welcome";
import { MessageList } from "./message-list";

export interface ChatContainerProps {
  onToggleSidebar?: () => void;
}

/** Main chat area: header, optional attachments drawer, thread and composer. */
export function ChatContainer({ onToggleSidebar }: ChatContainerProps) {
  const activeConversationId = useConversationStore((s) => s.activeConversationId);
  const hasMessages = useChatStore((s) => s.messages.length > 0);
  const isLoadingMessages = useChatStore((s) => s.isLoadingMessages);
  const isStreaming = useChatStore((s) => s.isStreaming);
  const { sendMessage, stopStreaming, error } = useChatStream();

  const [draft, setDraft] = React.useState("");
  const [isAttachmentsOpen, setIsAttachmentsOpen] = React.useState(false);
  const inputRef = React.useRef<HTMLTextAreaElement | null>(null);

  const handleSend = async () => {
    if (!draft.trim() || isStreaming) return;
    const prompt = draft.trim();
    setDraft("");
    await sendMessage(prompt);
  };

  const handlePickPrompt = (prompt: string) => {
    setDraft(prompt);
    inputRef.current?.focus();
  };

  return (
    <div className="flex-1 flex flex-col h-full overflow-hidden bg-background">
      <ChatHeader
        isAttachmentsOpen={isAttachmentsOpen}
        onToggleAttachments={() => setIsAttachmentsOpen((open) => !open)}
        onToggleSidebar={onToggleSidebar}
      />

      {isAttachmentsOpen && (
        <AttachmentsPanel
          conversationId={activeConversationId}
          onClose={() => setIsAttachmentsOpen(false)}
        />
      )}

      <div className="flex-1 overflow-y-auto">
        {isLoadingMessages ? (
          <ThreadLoading />
        ) : hasMessages ? (
          <MessageList />
        ) : (
          <ChatWelcome onPickPrompt={handlePickPrompt} />
        )}
      </div>

      <ChatComposer
        value={draft}
        onChange={setDraft}
        onSend={() => void handleSend()}
        onStop={stopStreaming}
        isStreaming={isStreaming}
        error={error}
        inputRef={inputRef}
      />
    </div>
  );
}

function ThreadLoading() {
  return (
    <div className="flex h-full items-center justify-center">
      <div className="flex flex-col items-center gap-2 text-xs text-muted-foreground">
        <div className="w-5 h-5 rounded-full border-2 border-primary border-t-transparent animate-spin" />
        <span>Loading thread history...</span>
      </div>
    </div>
  );
}
