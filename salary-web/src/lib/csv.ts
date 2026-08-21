/**
 * Client-side CSV export for an analytics table already sitting in memory (ui doc §7.6: "the
 * table is the evidence, and the evidence is what gets exported"). No backend round-trip — the
 * figures are already the server's own computed values, this only serialises what is already on
 * screen, so there is no arithmetic here (CLAUDE.md §6.1 is about computing a NEW figure, not
 * formatting an existing one as text).
 */
function escapeCsvCell(cell: string): string {
  if (/[",\n]/.test(cell)) {
    return `"${cell.replace(/"/g, '""')}"`;
  }
  return cell;
}

export function downloadCsv(filename: string, headers: string[], rows: string[][]) {
  const lines = [headers, ...rows].map((row) => row.map(escapeCsvCell).join(","));
  const blob = new Blob([lines.join("\n")], { type: "text/csv;charset=utf-8;" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}
