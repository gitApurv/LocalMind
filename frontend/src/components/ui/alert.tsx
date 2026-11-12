import * as React from "react";
import { cva, type VariantProps } from "class-variance-authority";
import { AlertCircle } from "lucide-react";
import { cn } from "@/lib/utils";

const alertVariants = cva(
  "flex items-center gap-2 rounded-lg border border-destructive/20 text-destructive",
  {
    variants: {
      size: {
        default: "p-3 text-sm bg-destructive/15",
        sm: "px-3 py-1.5 text-xs bg-destructive/10",
      },
    },
    defaultVariants: {
      size: "default",
    },
  }
);

export interface AlertProps
  extends React.HTMLAttributes<HTMLDivElement>, VariantProps<typeof alertVariants> {}

/** Error banner for messages shown to the user. */
export function Alert({ className, size, children, ...props }: AlertProps) {
  return (
    <div role="alert" className={cn(alertVariants({ size }), className)} {...props}>
      <AlertCircle className={cn("shrink-0", size === "sm" ? "w-3.5 h-3.5" : "w-4 h-4")} />
      <span>{children}</span>
    </div>
  );
}
