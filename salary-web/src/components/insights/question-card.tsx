"use client";

import { useState, type ReactNode } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { ChevronDown, ChevronUp } from "lucide-react";

/** ui doc §8.7: the saved-question library — each FR-6.x question is a card that expands into
 * its full view with filters. Collapsed, it shows the question and a headline; expanded, the
 * full chart/table/filters render beneath. */
export function QuestionCard({
  question,
  headline,
  children,
  defaultExpanded = false,
}: {
  question: string;
  headline: ReactNode;
  children: ReactNode;
  defaultExpanded?: boolean;
}) {
  const [expanded, setExpanded] = useState(defaultExpanded);

  return (
    <Card>
      <CardHeader
        className="flex cursor-pointer flex-row items-center justify-between gap-4"
        onClick={() => setExpanded((e) => !e)}
      >
        <div className="flex flex-col gap-1">
          <CardTitle className="type-section">{question}</CardTitle>
          <div className="figure-lg">{headline}</div>
        </div>
        <Button size="sm" variant="ghost" aria-label={expanded ? "Collapse" : "Expand"}>
          {expanded ? <ChevronUp className="size-4" /> : <ChevronDown className="size-4" />}
        </Button>
      </CardHeader>
      {expanded ? <CardContent className="space-y-4">{children}</CardContent> : null}
    </Card>
  );
}
