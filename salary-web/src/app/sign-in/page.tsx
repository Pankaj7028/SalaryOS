import type { Metadata } from "next";
import { Brand } from "@/components/shell/brand";
import { SignInForm } from "./sign-in-form";

export const metadata: Metadata = {
  title: "Sign in — Salary OS",
};

export default function SignInPage() {
  return (
    <div className="bg-background flex min-h-full flex-1 flex-col items-center justify-center gap-8 p-6">
      <Brand />
      <SignInForm />
    </div>
  );
}
