import * as React from "react";

export interface FormFieldProps {
  id: string;
  label: string;
  children: React.ReactNode;
}

/** Label plus control, spaced like every auth form row. */
export function FormField({ id, label, children }: FormFieldProps) {
  return (
    <div className="space-y-1.5">
      <label htmlFor={id} className="text-sm font-medium text-foreground block">
        {label}
      </label>
      {children}
    </div>
  );
}
