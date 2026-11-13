import { Bot } from "lucide-react";
import { PROMPT_SUGGESTIONS } from "../constants";

export interface ChatWelcomeProps {
  onPickPrompt: (prompt: string) => void;
}

/** Empty-thread state with starter prompts. */
export function ChatWelcome({ onPickPrompt }: ChatWelcomeProps) {
  return (
    <div className="flex flex-col items-center justify-center min-h-full p-6 text-center max-w-2xl mx-auto space-y-6">
      <div className="flex flex-col items-center space-y-2">
        <div className="w-12 h-12 rounded-2xl bg-primary/10 border border-primary/20 flex items-center justify-center text-primary shadow-inner">
          <Bot className="w-7 h-7" />
        </div>
        <h3 className="text-lg font-semibold text-foreground tracking-tight">
          How can I help you today?
        </h3>
        <p className="text-xs text-muted-foreground max-w-md">
          LocalMind runs completely sovereign on your hardware with Ollama inference, Qdrant vector
          embeddings, and privacy-first tool execution.
        </p>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 w-full text-left">
        {PROMPT_SUGGESTIONS.map(({ title, description, prompt, icon: Icon }) => (
          <button
            key={title}
            type="button"
            onClick={() => onPickPrompt(prompt)}
            className="group p-3 rounded-xl border border-border/70 bg-card/40 hover:bg-card/90 hover:border-primary/40 transition-all text-left space-y-1 shadow-sm"
          >
            <div className="flex items-center gap-2 text-xs font-semibold text-foreground group-hover:text-primary transition-colors">
              <Icon className="w-3.5 h-3.5 text-primary" />
              <span>{title}</span>
            </div>
            <p className="text-[11px] text-muted-foreground leading-snug">{description}</p>
          </button>
        ))}
      </div>
    </div>
  );
}
