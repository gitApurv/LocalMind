import { Bot, Loader2 } from "lucide-react";

export function LoadingScreen() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-background">
      <div className="flex flex-col items-center space-y-4">
        <div className="flex items-center justify-center w-14 h-14 rounded-2xl bg-primary/10 border border-primary/20 text-primary animate-pulse">
          <Bot className="w-8 h-8" />
        </div>
        <p className="text-sm text-muted-foreground flex items-center gap-2">
          <Loader2 className="w-4 h-4 animate-spin text-primary" />
          Loading LocalMind sovereign environment...
        </p>
      </div>
    </div>
  );
}
