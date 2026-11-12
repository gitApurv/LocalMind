import * as React from "react";
import { Bot } from "lucide-react";
import { Card, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";

export interface AuthCardProps {
  title: string;
  description: string;
  children: React.ReactNode;
}

/** Centered branded card shared by the sign-in and sign-up pages. */
export function AuthCard({ title, description, children }: AuthCardProps) {
  return (
    <div className="flex min-h-screen items-center justify-center p-4 bg-background">
      <Card className="w-full max-w-md border-border bg-card shadow-2xl">
        <CardHeader className="space-y-2 text-center">
          <div className="flex justify-center mb-2">
            <div className="flex items-center justify-center w-12 h-12 rounded-xl bg-primary/10 border border-primary/20 text-primary">
              <Bot className="w-7 h-7" />
            </div>
          </div>
          <CardTitle className="text-2xl font-bold tracking-tight">{title}</CardTitle>
          <CardDescription className="text-muted-foreground text-sm">{description}</CardDescription>
        </CardHeader>
        {children}
      </Card>
    </div>
  );
}
