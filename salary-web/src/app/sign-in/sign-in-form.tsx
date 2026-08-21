"use client";

import { useState, type FormEvent } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ApiError } from "@/lib/api/client";
import { useLogin } from "@/lib/auth/auth-queries";

/**
 * Wrong password, unknown email, and a locked account are all the same
 * message from the server (FR-1.3) — shown as-is, inline, never as a toast
 * (notify.ts: auth failures aren't what that host is for).
 */
export function SignInForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const login = useLogin();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    try {
      await login.mutateAsync({ email, password });
      const redirectTarget = searchParams.get("redirect") || "/";
      router.push(redirectTarget);
    }
    catch (cause) {
      setError(cause instanceof ApiError && cause.problem?.detail ? cause.problem.detail : "Sign in failed.");
    }
  }

  return (
    <Card className="w-full max-w-sm">
      <CardHeader>
        <CardTitle className="type-section">Sign in</CardTitle>
        <CardDescription>Enter your ACME email and password.</CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={handleSubmit} className="flex flex-col gap-4" noValidate>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="email">Email</Label>
            <Input
              id="email"
              name="email"
              type="email"
              autoComplete="username"
              required
              value={email}
              onChange={(event) => setEmail(event.target.value)}
            />
          </div>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="password">Password</Label>
            <Input
              id="password"
              name="password"
              type="password"
              autoComplete="current-password"
              required
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
          </div>
          {error ? (
            <p role="alert" className="type-body-sm text-critical">
              {error}
            </p>
          ) : null}
          <Button type="submit" size="sm" disabled={login.isPending} className="mt-1">
            {login.isPending ? "Signing in…" : "Sign in"}
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}
