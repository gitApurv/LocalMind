import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "LocalMind - Sovereign Local AI Workspace",
  description: "Enterprise-grade local AI assistant powered by Ollama, Spring AI, and Next.js",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" className="dark">
      <body className="bg-background text-foreground min-h-screen">{children}</body>
    </html>
  );
}
