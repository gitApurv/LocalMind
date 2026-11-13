"use client";

import * as React from "react";
import { Check, MessageSquare, Pencil, Pin, PinOff, Trash2, X } from "lucide-react";
import { cn } from "@/lib/utils";
import { UNTITLED_CONVERSATION } from "../constants";
import { useConversationStore } from "../store/conversation-store";
import type { Conversation } from "../types";

export interface ConversationItemProps {
  conversation: Conversation;
}

/** One row of the thread list: select on click, rename inline, pin and delete on hover. */
export function ConversationItem({ conversation }: ConversationItemProps) {
  const isActive = useConversationStore((s) => s.activeConversationId === conversation.id);
  const selectConversation = useConversationStore((s) => s.selectConversation);
  const togglePin = useConversationStore((s) => s.togglePin);
  const deleteConversation = useConversationStore((s) => s.deleteConversation);
  const [isEditing, setIsEditing] = React.useState(false);

  const title = conversation.title || UNTITLED_CONVERSATION;
  const select = () => void selectConversation(conversation.id);
  const startRename = (event: React.MouseEvent) => {
    event.stopPropagation();
    setIsEditing(true);
  };

  if (isEditing) {
    return <TitleEditor conversation={conversation} onDone={() => setIsEditing(false)} />;
  }

  return (
    <div
      role="button"
      tabIndex={0}
      onClick={select}
      onKeyDown={(event) => {
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          select();
        }
      }}
      onDoubleClick={startRename}
      className={cn(
        "group flex items-center justify-between px-3 py-2 rounded-lg text-xs cursor-pointer transition-colors focus:outline-none focus-visible:ring-1 focus-visible:ring-primary",
        isActive
          ? "bg-primary/10 text-primary font-medium"
          : "text-muted-foreground hover:bg-muted hover:text-foreground"
      )}
    >
      <div className="flex items-center gap-2 truncate pr-1">
        {conversation.pinned ? (
          <Pin className="w-3.5 h-3.5 text-primary shrink-0 rotate-45" />
        ) : (
          <MessageSquare className="w-3.5 h-3.5 shrink-0" />
        )}
        <span className="truncate">{title}</span>
      </div>

      <div className="flex items-center gap-0.5 opacity-0 group-hover:opacity-100 focus-within:opacity-100 transition-opacity shrink-0">
        <ActionButton
          label={`${conversation.pinned ? "Unpin" : "Pin"} conversation ${conversation.title}`}
          title={conversation.pinned ? "Unpin thread" : "Pin thread to top"}
          hoverClassName="hover:text-primary"
          onClick={() => void togglePin(conversation.id)}
        >
          {conversation.pinned ? <PinOff className="w-3 h-3" /> : <Pin className="w-3 h-3" />}
        </ActionButton>
        <ActionButton
          label={`Rename conversation ${conversation.title}`}
          title="Rename title"
          hoverClassName="hover:text-foreground"
          onClick={() => setIsEditing(true)}
        >
          <Pencil className="w-3 h-3" />
        </ActionButton>
        <ActionButton
          label={`Delete conversation ${conversation.title}`}
          title="Delete thread"
          hoverClassName="hover:text-destructive"
          onClick={() => void deleteConversation(conversation.id)}
        >
          <Trash2 className="w-3 h-3" />
        </ActionButton>
      </div>
    </div>
  );
}

interface ActionButtonProps {
  label: string;
  title: string;
  hoverClassName: string;
  onClick: () => void;
  children: React.ReactNode;
}

function ActionButton({ label, title, hoverClassName, onClick, children }: ActionButtonProps) {
  return (
    <button
      type="button"
      onClick={(event) => {
        // Keep the click from also selecting the row.
        event.stopPropagation();
        onClick();
      }}
      aria-label={label}
      title={title}
      className={cn("p-1 text-muted-foreground transition-colors", hoverClassName)}
    >
      {children}
    </button>
  );
}

interface TitleEditorProps {
  conversation: Conversation;
  onDone: () => void;
}

function TitleEditor({ conversation, onDone }: TitleEditorProps) {
  const renameConversation = useConversationStore((s) => s.renameConversation);
  const [title, setTitle] = React.useState(conversation.title || UNTITLED_CONVERSATION);

  const save = async () => {
    const trimmed = title.trim();
    if (trimmed) {
      await renameConversation(conversation.id, trimmed);
    }
    onDone();
  };

  return (
    <div className="flex items-center gap-1.5 px-2 py-1.5 rounded-lg bg-card border border-primary/40 text-xs">
      <input
        type="text"
        aria-label="Edit conversation title"
        autoFocus
        value={title}
        onChange={(event) => setTitle(event.target.value)}
        onKeyDown={(event) => {
          if (event.key === "Enter") {
            event.preventDefault();
            void save();
          } else if (event.key === "Escape") {
            onDone();
          }
        }}
        onBlur={() => void save()}
        className="flex-1 bg-transparent px-1 py-0.5 text-foreground focus:outline-none text-xs"
      />
      <button
        type="button"
        onMouseDown={(event) => {
          // Save before the input's blur fires.
          event.preventDefault();
          void save();
        }}
        className="p-1 hover:text-emerald-400 text-muted-foreground transition-colors"
        aria-label="Save title"
      >
        <Check className="w-3.5 h-3.5" />
      </button>
      <button
        type="button"
        onClick={onDone}
        className="p-1 hover:text-destructive text-muted-foreground transition-colors"
        aria-label="Cancel rename"
      >
        <X className="w-3.5 h-3.5" />
      </button>
    </div>
  );
}
