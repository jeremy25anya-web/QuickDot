# Ideas / deferred to v2

Seeded from SPEC.md §10. Add to this list rather than scope-creeping v1.

- Redis + horizontal scaling (multi-instance room state).
- WebRTC P2P transfer + TURN server (skip server relay for large files).
- PhotoDNA / hash-based CSAM content scanning (currently a documented gap —
  see spec §6, "CSAM — mandatory-reporting note").
- Optional room passwords.
- Larger group rooms (beyond the v1 cap of 8 participants).
- SMS / email share (send a room link without copy/paste).
- Quebec Law 25 review, if marketing expands there.
- `?demo` mode from the v2 prototype: a sample room for screenshots and
  layout checks, without a backend (ADR-0022). Use generic file names.
- Catch-up for late joiners (the v2 prototype replayed earlier items).
  Conflicts with "relay, never hold content"; only worth it as an opt-in
  peer-to-peer sync from someone already in the room.
