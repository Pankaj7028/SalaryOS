import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";

/**
 * Routes reachable with no session — everything else redirects to sign-in.
 * `/dev/*` is the design-system audit surface (`app-shell.tsx`'s comment: it
 * stays outside the app shell on purpose) and is left open too.
 */
const PUBLIC_PATH_PREFIXES = ["/sign-in", "/dev"];

/**
 * A fast, cookie-presence gate — not the security boundary. It only avoids
 * shipping a shell that immediately 401s on its first fetch; the
 * `@PreAuthorize` on the controller is what actually decides access
 * (CLAUDE.md §7: hiding a nav item, or a whole route, is not access control).
 */
export function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl;

  if (PUBLIC_PATH_PREFIXES.some((prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`))) {
    return NextResponse.next();
  }

  if (request.cookies.has("sos_session")) {
    return NextResponse.next();
  }

  const signInUrl = new URL("/sign-in", request.url);
  signInUrl.searchParams.set("redirect", pathname);
  return NextResponse.redirect(signInUrl);
}

export const config = {
  matcher: ["/((?!api|_next/static|_next/image|favicon.ico).*)"],
};
