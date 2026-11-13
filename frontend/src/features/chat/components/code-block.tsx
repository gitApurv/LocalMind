"use client";

import * as React from "react";
import { Check, Copy } from "lucide-react";
import Prism from "prismjs";
import "prismjs/components/prism-javascript";
import "prismjs/components/prism-typescript";
import "prismjs/components/prism-jsx";
import "prismjs/components/prism-tsx";
import "prismjs/components/prism-python";
import "prismjs/components/prism-bash";
import "prismjs/components/prism-json";
import "prismjs/components/prism-markdown";
import "prismjs/components/prism-java";
import "prismjs/components/prism-sql";
import "prismjs/components/prism-css";
import "prismjs/components/prism-yaml";
import "prismjs/components/prism-go";
import "prismjs/components/prism-rust";
import "prismjs/components/prism-docker";
import "prismjs/themes/prism-tomorrow.css";

const COPIED_FEEDBACK_MS = 2000;

const LANGUAGE_ALIASES: Record<string, string> = {
  py: "python",
  js: "javascript",
  ts: "typescript",
  sh: "bash",
  zsh: "bash",
  yml: "yaml",
  golang: "go",
  rs: "rust",
  dockerfile: "docker",
};

export interface CodeBlockProps {
  language: string;
  code: string;
}

/** Fenced code with Prism highlighting and a copy button. */
export function CodeBlock({ language, code }: CodeBlockProps) {
  const [copied, setCopied] = React.useState(false);
  const highlighted = highlight(code, language);

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(code);
      setCopied(true);
      setTimeout(() => setCopied(false), COPIED_FEEDBACK_MS);
    } catch (error) {
      console.error("Failed to copy code to clipboard:", error);
    }
  };

  return (
    <div className="relative my-3 rounded-lg border border-border/80 bg-neutral-950 overflow-hidden font-mono text-xs">
      <div className="flex items-center justify-between px-3 py-1.5 bg-neutral-900 border-b border-border/60 text-muted-foreground text-[11px]">
        <span className="font-semibold uppercase tracking-wider text-neutral-400">
          {language || "text"}
        </span>
        <button
          type="button"
          onClick={handleCopy}
          aria-label="Copy code"
          className="flex items-center gap-1.5 px-2 py-0.5 rounded hover:bg-neutral-800 text-neutral-300 hover:text-white transition-colors"
        >
          {copied ? (
            <>
              <Check className="w-3 h-3 text-emerald-400" />
              <span className="text-[10px] text-emerald-400">Copied!</span>
            </>
          ) : (
            <>
              <Copy className="w-3 h-3" />
              <span className="text-[10px]">Copy</span>
            </>
          )}
        </button>
      </div>
      <pre className="p-3 overflow-x-auto text-neutral-100 leading-relaxed">
        {highlighted ? (
          <code dangerouslySetInnerHTML={{ __html: highlighted }} />
        ) : (
          <code>{code}</code>
        )}
      </pre>
    </div>
  );
}

/** Returns Prism HTML (escaped by Prism), or null for unknown languages. */
function highlight(code: string, language: string): string | null {
  const name = language.toLowerCase();
  const normalized = LANGUAGE_ALIASES[name] ?? name;
  const grammar = normalized ? Prism.languages[normalized] : undefined;
  return grammar ? Prism.highlight(code, grammar, normalized) : null;
}
