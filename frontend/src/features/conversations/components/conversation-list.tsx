"use client";

import * as React from "react";
import { Pin } from "lucide-react";
import { cn } from "@/lib/utils";
import { useConversationStore } from "../store/conversation-store";
import type { Conversation } from "../types";
import { ConversationItem } from "./conversation-item";

const SECTION_HEADING =
  "px-3 py-1 text-[10px] font-semibold tracking-wider text-muted-foreground uppercase";

/** Sidebar thread list, split into pinned and recent sections. */
export function ConversationList() {
  const conversations = useConversationStore((s) => s.conversations);

  if (conversations.length === 0) {
    return (
      <div className="text-xs text-muted-foreground text-center py-8 px-4">
        No conversations yet.
        <br />
        Click &quot;New Thread&quot; to begin.
      </div>
    );
  }

  const pinned = conversations.filter((c) => c.pinned);
  const recent = conversations.filter((c) => !c.pinned);

  return (
    <div className="space-y-4">
      {pinned.length > 0 && (
        <ConversationSection
          heading={
            <div className={cn("flex items-center gap-1.5", SECTION_HEADING)}>
              <Pin className="w-3 h-3 text-primary rotate-45" />
              <span>Pinned</span>
            </div>
          }
          conversations={pinned}
        />
      )}
      <ConversationSection
        heading={
          <div className={SECTION_HEADING}>
            {pinned.length > 0 ? "Recent Threads" : "Conversations"}
          </div>
        }
        conversations={recent}
      />
    </div>
  );
}

interface ConversationSectionProps {
  heading: React.ReactNode;
  conversations: Conversation[];
}

function ConversationSection({ heading, conversations }: ConversationSectionProps) {
  return (
    <div className="space-y-1">
      {heading}
      <div className="space-y-0.5">
        {conversations.map((conversation) => (
          <ConversationItem key={conversation.id} conversation={conversation} />
        ))}
      </div>
    </div>
  );
}
