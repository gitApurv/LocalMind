import ReactMarkdown, { type Components } from "react-markdown";
import rehypeKatex from "rehype-katex";
import remarkGfm from "remark-gfm";
import remarkMath from "remark-math";
import { CodeBlock } from "./code-block";

const LANGUAGE_CLASS = /language-(\w+)/;

const COMPONENTS: Components = {
  code({ className, children, node, ...props }) {
    const language = LANGUAGE_CLASS.exec(className ?? "")?.[1];
    const text = String(children);
    // react-markdown has no inline flag: fenced blocks carry a language or span several lines.
    if (!language && !text.includes("\n")) {
      return (
        <code
          className="px-1.5 py-0.5 rounded bg-muted font-mono text-xs text-primary font-medium"
          {...props}
        >
          {children}
        </code>
      );
    }
    return <CodeBlock language={language ?? ""} code={text.replace(/\n$/, "")} />;
  },
  table({ children }) {
    return (
      <div className="overflow-x-auto my-3">
        <table className="w-full text-left border-collapse border border-border text-xs">
          {children}
        </table>
      </div>
    );
  },
  th({ children }) {
    return (
      <th className="border border-border bg-muted/60 p-2 font-semibold text-foreground">
        {children}
      </th>
    );
  },
  td({ children }) {
    return <td className="border border-border p-2">{children}</td>;
  },
  a({ href, children }) {
    return (
      <a
        href={href}
        target="_blank"
        rel="noopener noreferrer"
        className="text-primary underline underline-offset-2 hover:opacity-80"
      >
        {children}
      </a>
    );
  },
  blockquote({ children }) {
    return (
      <blockquote className="border-l-2 border-primary/60 pl-3 italic text-muted-foreground my-2">
        {children}
      </blockquote>
    );
  },
};

export interface MarkdownContentProps {
  content: string;
}

/** Renders assistant markdown with GFM tables, KaTeX math and highlighted code. */
export function MarkdownContent({ content }: MarkdownContentProps) {
  return (
    <div className="prose prose-invert prose-sm max-w-none break-words">
      <ReactMarkdown
        remarkPlugins={[remarkGfm, remarkMath]}
        rehypePlugins={[rehypeKatex]}
        components={COMPONENTS}
      >
        {content}
      </ReactMarkdown>
    </div>
  );
}
