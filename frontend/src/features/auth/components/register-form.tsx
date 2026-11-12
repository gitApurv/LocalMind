"use client";

import * as React from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { CheckCircle2 } from "lucide-react";
import { Alert } from "@/components/ui/alert";
import { CardContent, CardFooter } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { getErrorMessage } from "@/lib/api-error";
import { cn } from "@/lib/utils";
import { useRedirectIfAuthenticated } from "../hooks/use-redirect-if-authenticated";
import { MAX_PASSWORD_LENGTH, MIN_PASSWORD_LENGTH, validateRegister } from "../lib/validation";
import { useAuthStore } from "../store/auth-store";
import { AuthCard } from "./auth-card";
import { FormField } from "./form-field";
import { PasswordInput } from "./password-input";
import { SubmitButton } from "./submit-button";

const INVALID_INPUT = "border-destructive focus-visible:ring-destructive";

export function RegisterForm() {
  const router = useRouter();
  const register = useAuthStore((s) => s.register);
  const storeError = useAuthStore((s) => s.error);
  const clearError = useAuthStore((s) => s.clearError);
  useRedirectIfAuthenticated();

  const [fullName, setFullName] = React.useState("");
  const [email, setEmail] = React.useState("");
  const [password, setPassword] = React.useState("");
  const [isSubmitting, setIsSubmitting] = React.useState(false);
  const [touched, setTouched] = React.useState({ fullName: false, email: false, password: false });
  const [localError, setLocalError] = React.useState<string | null>(null);

  const passwordLengthOk = password.length >= MIN_PASSWORD_LENGTH;
  const touch = (field: keyof typeof touched) => setTouched((prev) => ({ ...prev, [field]: true }));

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    clearError();
    setTouched({ fullName: true, email: true, password: true });

    const validationError = validateRegister({ fullName, email, password });
    setLocalError(validationError);
    if (validationError) return;

    setIsSubmitting(true);
    try {
      await register(email.trim(), password, fullName.trim());
      router.push("/");
    } catch (error) {
      setLocalError(getErrorMessage(error, "Failed to create account. Please try again."));
    } finally {
      setIsSubmitting(false);
    }
  };

  const errorMessage = localError || storeError;

  return (
    <AuthCard
      title="Create your account"
      description="Get started with completely private, locally orchestrated AI"
    >
      <form onSubmit={handleSubmit} noValidate>
        <CardContent className="space-y-4">
          {errorMessage && <Alert className="animate-in fade-in-50">{errorMessage}</Alert>}

          <FormField id="full-name" label="Full name">
            <Input
              id="full-name"
              name="fullName"
              type="text"
              autoComplete="name"
              required
              enterKeyHint="next"
              placeholder="Jane Doe"
              value={fullName}
              onChange={(e) => {
                setFullName(e.target.value);
                setLocalError(null);
              }}
              onBlur={() => touch("fullName")}
              className={cn(touched.fullName && !fullName.trim() && INVALID_INPUT)}
            />
          </FormField>

          <FormField id="email" label="Email address">
            <Input
              id="email"
              name="email"
              type="email"
              autoComplete="username"
              spellCheck={false}
              required
              enterKeyHint="next"
              placeholder="name@example.com"
              value={email}
              onChange={(e) => {
                setEmail(e.target.value);
                setLocalError(null);
              }}
              onBlur={() => touch("email")}
              className={cn(touched.email && !email.trim() && INVALID_INPUT)}
            />
          </FormField>

          <FormField id="new-password" label="Password">
            <PasswordInput
              id="new-password"
              name="password"
              autoComplete="new-password"
              required
              minLength={MIN_PASSWORD_LENGTH}
              maxLength={MAX_PASSWORD_LENGTH}
              enterKeyHint="done"
              placeholder="At least 8 characters"
              value={password}
              onChange={(e) => {
                setPassword(e.target.value);
                setLocalError(null);
              }}
              onBlur={() => touch("password")}
              invalid={touched.password && !passwordLengthOk}
            />
            {touched.password && (
              <div className="flex items-center gap-1.5 text-xs mt-1 text-muted-foreground">
                <CheckCircle2
                  className={cn(
                    "w-3.5 h-3.5",
                    passwordLengthOk ? "text-emerald-500" : "text-muted-foreground"
                  )}
                />
                <span className={cn(passwordLengthOk && "text-foreground")}>
                  Must be at least 8 characters
                </span>
              </div>
            )}
          </FormField>
        </CardContent>

        <CardFooter className="flex flex-col space-y-4 pt-2">
          <SubmitButton
            isSubmitting={isSubmitting}
            label="Create Account"
            submittingLabel="Creating Account..."
          />
          <p className="text-center text-xs text-muted-foreground">
            Already have an account?{" "}
            <Link href="/login" className="text-primary hover:underline font-medium ml-1">
              Sign in
            </Link>
          </p>
        </CardFooter>
      </form>
    </AuthCard>
  );
}
