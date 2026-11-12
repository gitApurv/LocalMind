import { Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";

export interface SubmitButtonProps {
  isSubmitting: boolean;
  label: string;
  submittingLabel: string;
}

export function SubmitButton({ isSubmitting, label, submittingLabel }: SubmitButtonProps) {
  return (
    <Button type="submit" className="w-full font-semibold" disabled={isSubmitting}>
      {isSubmitting ? (
        <>
          <Loader2 className="w-4 h-4 mr-2 animate-spin" />
          {submittingLabel}
        </>
      ) : (
        label
      )}
    </Button>
  );
}
