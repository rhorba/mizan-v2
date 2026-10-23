/** Upload payloads built in memory, so nothing binary (or malicious) is committed. */

export interface Payload {
  name: string;
  mimeType: string;
  buffer: Buffer;
}

/** A small text PDF with a valid cross-reference table. */
export function textPdf(name: string, text: string): Payload {
  const stream = `BT /F1 11 Tf 50 750 Td (${text.replace(/[()\\]/g, '\\$&')}) Tj ET`;
  const objects = [
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R' +
      ' /Resources << /Font << /F1 5 0 R >> >> >>',
    `<< /Length ${Buffer.byteLength(stream, 'latin1')} >>\nstream\n${stream}\nendstream`,
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
  ];
  let body = '%PDF-1.7\n';
  const offsets: number[] = [];
  objects.forEach((object, i) => {
    offsets.push(Buffer.byteLength(body, 'latin1'));
    body += `${i + 1} 0 obj\n${object}\nendobj\n`;
  });
  const xref = Buffer.byteLength(body, 'latin1');
  body += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`;
  body += offsets.map((o) => `${String(o).padStart(10, '0')} 00000 n \n`).join('');
  body += `trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`;
  return { name, mimeType: 'application/pdf', buffer: Buffer.from(body, 'latin1') };
}

/** Plain text that merely claims to be a PDF: passes the browser's checks, not the API's. */
export function fakePdf(name: string): Payload {
  return { name, mimeType: 'application/pdf', buffer: Buffer.from('meeting notes, not a PDF') };
}

/** The EICAR anti-virus test file, decoded only at run time. */
export function eicar(name: string): Payload {
  const encoded =
    'WDVPIVAlQEFQWzRcUFpYNTQoUF4pN0NDKTd9JEVJQ0FSLVNUQU5EQVJELUFOVElWSVJVUy1URVNULUZJTEUhJEgrSCo='; // gitleaks:allow (public EICAR test string)
  return { name, mimeType: 'application/pdf', buffer: Buffer.from(encoded, 'base64') };
}
