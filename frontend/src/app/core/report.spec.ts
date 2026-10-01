import { reportMailto } from './report';

const decodeBody = (href: string) => decodeURIComponent(new URL(href).searchParams.get('body') ?? '');

describe('reportMailto', () => {
  const at = new Date('2026-09-30T12:00:00Z');

  it('addresses the abuse inbox with the room code in the subject', () => {
    const href = reportMailto({ id: 'm1', kind: 'text', outgoing: false, senderName: 'Quiet Heron', text: 'hi' }, 'AB2CD3', at);
    expect(href.startsWith('mailto:abuse@amartian.ca?')).toBe(true);
    expect(new URL(href).searchParams.get('subject')).toBe('QuickDot report: room AB2CD3');
  });

  it('includes the metadata needed to find the room in the audit log', () => {
    const body = decodeBody(
      reportMailto(
        { id: 'f1', kind: 'file', outgoing: false, senderName: 'Quiet Heron', name: 'x.pdf', mime: 'application/pdf', size: 12345 },
        'AB2CD3',
        at
      )
    );
    expect(body).toContain('Room code: AB2CD3');
    expect(body).toContain('Reported at: 2026-09-30T12:00:00.000Z');
    expect(body).toContain('File type: application/pdf');
    expect(body).toContain('File size: 12345 bytes');
  });

  it('never includes message text or file names', () => {
    const text = decodeBody(
      reportMailto({ id: 'm1', kind: 'text', outgoing: false, senderName: 'Quiet Heron', text: 'secret message' }, 'AB2CD3', at)
    );
    const file = decodeBody(
      reportMailto({ id: 'f1', kind: 'file', outgoing: false, senderName: 'Quiet Heron', name: 'secret-name.pdf', size: 1 }, 'AB2CD3', at)
    );
    expect(text).not.toContain('secret');
    expect(file).not.toContain('secret');
  });
});
