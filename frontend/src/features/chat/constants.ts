import { Brain, Calculator, FileText, Globe, type LucideIcon } from "lucide-react";

export interface PromptSuggestion {
  title: string;
  description: string;
  prompt: string;
  icon: LucideIcon;
}

/** Starter prompts shown on an empty thread, one per backend capability. */
export const PROMPT_SUGGESTIONS: PromptSuggestion[] = [
  {
    title: "Document RAG",
    description: "Search uploaded PDFs and DOCX files with Qdrant vector retrieval.",
    prompt: "Summarize the key findings from my uploaded documents.",
    icon: FileText,
  },
  {
    title: "Web Intelligence",
    description: "Search live web results privately via self-hosted SearXNG.",
    prompt: "Search the web for the latest advancements in local AI models.",
    icon: Globe,
  },
  {
    title: "Math Calculation",
    description: "Evaluate high-precision mathematical expressions via exp4j engine.",
    prompt: "Calculate (sqrt(144) * 12) + (2^8 / 4) and explain step-by-step.",
    icon: Calculator,
  },
  {
    title: "Persistent Memory",
    description: "Store and recall personalized user facts and coding preferences.",
    prompt: "Remember that I prefer TypeScript and React 19 for full-stack engineering.",
    icon: Brain,
  },
];

/** Upper bound for the auto-growing message box, in pixels. */
export const COMPOSER_MAX_HEIGHT_PX = 180;
