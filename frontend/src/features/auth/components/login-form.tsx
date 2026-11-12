"use client";

import * as React from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Alert } from "@/components/ui/alert";
import { CardContent, CardFooter } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { getErrorMessage } from "@/lib/api-error";
import { cn } from "@/lib/utils";
import { useRedirectIfAuthenticated } from "../hooks/use-redirect-if-authenticated";
import { validateLogin } from "../lib/validation";
import { useAuthStore } from "../store/auth-store";
import { AuthCard } from "./auth-card";
import { FormField } from "./form-field";
import { PasswordInput } from "./password-input";
import { SubmitButton } from "./submit-button";

const INVALID_INPUT = "border-destructive focus-visible:ring-destructive";

export function LoginForm() {
  const router = useRouter();
  const login = useAuthStore((s) => s.login);
  const storeError = useAuthStore((s) => s.error);
  const clearError = useAuthStore((s) => s.clearError);
  useRedirectIfAuthenticated();

  const [email, setEmail] = React.useState("");
  const [password, setPassword] = React.useState("");
  const [isSubmitting, setIsSubmitting] = React.useState(false);
  const [touched, setTouched] = React.useState({ email: false, password: false });
  const [localError, setLocalError] = React.useState<string | null>(null);

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    clearError();
    setTouched({ email: true, password: true });

    const validationError = validateLogin({ email, password });
    setLocalError(validationError);
    if (validationError) return;

    setIsSubmitting(true);
    try {
      await login(email.trim(), password);
      router.push("/");
    } catch (error) {
      setLocalError(getErrorMessage(error, "Invalid email or password"));
    } finally {
      setIsSubmitting(false);
    }
  };

  const errorMessage = localError || storeError;

  return (
    <AuthCard
      title="Sign in to LocalMind"
      description="Enter your credentials to access your sovereign workspace"
    >
      <form onSubmit={handleSubmit} noValidate>
        <CardContent className="space-y-4">
          {errorMessage && <Alert className="animate-in fade-in-50">{errorMessage}</Alert>}

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
              onBlur={() => setTouched((prev) => ({ ...prev, email: true }))}
              className={cn(touched.email && !email.trim() && INVALID_INPUT)}
            />
          </FormField>

          <FormField id="current-password" label="Password">
            <PasswordInput
              id="current-password"
              name="password"
              autoComplete="current-password"
              required
              enterKeyHint="done"
              placeholder="Enter your password"
              value={password}
              onChange={(e) => {
                setPassword(e.target.value);
                setLocalError(null);
              }}
              onBlur={() => setTouched((prev) => ({ ...prev, password: true }))}
              invalid={touched.password && !password}
            />
          </FormField>
        </CardContent>

        <CardFooter className="flex flex-col space-y-4 pt-2">
          <SubmitButton
            isSubmitting={isSubmitting}
            label="Sign In"
            submittingLabel="Signing In..."
          />
          <p className="text-center text-xs text-muted-foreground">
            Don&apos;t have an account?{" "}
            <Link href="/register" className="text-primary hover:underline font-medium ml-1">
              Sign up
            </Link>
          </p>
        </CardFooter>
      </form>
    </AuthCard>
  );
}
