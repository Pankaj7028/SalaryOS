import { describe, expect, it } from "vitest";
import { saveableQueryString } from "@/components/saved-views/saveable-query-string";

describe("saveableQueryString", () => {
  it("keeps every filter param untouched and in order", () => {
    const question = "departmentId=7748330c&status=ACTIVE&bandStatus=BELOW_MIN";
    expect(saveableQueryString(question)).toBe(question);
  });

  it("drops the cursor — a saved question must not carry a stale page position", () => {
    expect(saveableQueryString("status=ACTIVE&cursor=abc123&bandStatus=BELOW_MIN")).toBe(
      "status=ACTIVE&bandStatus=BELOW_MIN",
    );
  });

  it("keeps sortBy and limit — how you chose to look at the answer is part of the question", () => {
    expect(saveableQueryString("sortBy=compaRatio&limit=100&cursor=xyz")).toBe(
      "sortBy=compaRatio&limit=100",
    );
  });

  it("treats the unfiltered list as a legitimate view rather than nothing to save", () => {
    expect(saveableQueryString("")).toBe("");
    expect(saveableQueryString("cursor=abc")).toBe("");
  });

  it("round-trips a saved string unchanged, so replaying cannot drift from saving", () => {
    const saved = saveableQueryString("q=ada&departmentId=7748330c&limit=25&cursor=p2");
    expect(saveableQueryString(saved)).toBe(saved);
  });
});
