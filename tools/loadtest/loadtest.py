"""QuickDot hostile-input + load test (spec §6, Stage 6).

Two parts:
  1. Limit checks — each spec §6 rule is attacked from a real client and the
     server's response is compared with what the spec requires.
  2. Load — N rooms, each streaming a full-size file between two
     participants with text chatter, while /healthz latency is sampled.

Usage (from the repo root, Python 3.11+ with `pip install websockets`):
    python tools/loadtest/loadtest.py                        # prod defaults
    python tools/loadtest/loadtest.py --base http://localhost:8080 \
        --origin http://localhost:4200                       # local backend
    python tools/loadtest/loadtest.py --creation-limits      # + room-creation limits

Notes:
  * All traffic comes from one IP. The per-IP room limits (≤5 concurrent,
    ≤20/hour) therefore cap how many rooms one run may use: the checks use 2
    rooms and the load part defaults to 3, i.e. 5 creations per run, so about
    4 runs per hour, ~1 min apart (left rooms are reclaimed after 30 s).
    `--creation-limits` deliberately exhausts your IP's hourly quota.
  * Payloads are random bytes, never real content. Nothing is written to disk.
"""

import argparse
import asyncio
import json
import os
import ssl
import struct
import sys
import time
import urllib.error
import urllib.request
import uuid

from websockets.asyncio.client import connect
from websockets.exceptions import ConnectionClosed, InvalidStatus

try:  # Prefer certifi's CA bundle: some Windows cert stores hold an expired
    import certifi  # cross-signed root that makes OpenSSL reject valid chains.
    SSL_CTX = ssl.create_default_context(cafile=certifi.where())
except ImportError:
    SSL_CTX = ssl.create_default_context()

CHUNK = 256 * 1024
MAX_FILE = 25 * 1024 * 1024
TEXT_LIMIT = 30  # per connection per 10 s
UPLOAD_LIMIT = 10  # per room per minute
MAX_PARTICIPANTS = 8


class Ctx:
    def __init__(self, base: str, origin: str):
        self.base = base.rstrip("/")
        self.ws_base = self.base.replace("https://", "wss://").replace("http://", "ws://")
        self.origin = origin

    def create_room(self, extra_headers: dict | None = None) -> tuple[int, str | None]:
        headers = {"Content-Type": "application/json", "Origin": self.origin, **(extra_headers or {})}
        req = urllib.request.Request(f"{self.base}/api/rooms", data=b"{}", method="POST", headers=headers)
        try:
            with urllib.request.urlopen(req, timeout=15, context=SSL_CTX) as resp:
                return resp.status, json.load(resp)["code"]
        except urllib.error.HTTPError as e:
            return e.code, None

    async def join(self, code: str, origin: str | None = None):
        tls = SSL_CTX if self.ws_base.startswith("wss://") else None
        ws = await connect(f"{self.ws_base}/ws/{code}", origin=origin or self.origin, max_size=2 * CHUNK, ssl=tls)
        first = json.loads(await asyncio.wait_for(ws.recv(), 10))
        return ws, first


def frame(transfer_id: str, seq: int, payload: bytes) -> bytes:
    tid = transfer_id.encode()
    return struct.pack(">I", len(tid)) + tid + struct.pack(">Q", seq) + payload


async def drain(ws, seconds: float) -> list:
    """Collect whatever arrives on ws for `seconds` (answers pings so we stay alive)."""
    out, deadline = [], time.monotonic() + seconds
    while (left := deadline - time.monotonic()) > 0:
        try:
            msg = await asyncio.wait_for(ws.recv(), left)
        except (asyncio.TimeoutError, ConnectionClosed):
            break
        if isinstance(msg, bytes):
            out.append(("binary", len(msg)))
            continue
        data = json.loads(msg)
        if data.get("type") == "ping":
            await ws.send(json.dumps({"type": "pong"}))
        out.append(data)
    return out


def error_codes(msgs) -> list[str]:
    return [m["code"] for m in msgs if isinstance(m, dict) and m.get("type") == "error"]


async def file_start(ws, name="note.txt", mime="text/plain", size=1000) -> str:
    tid = str(uuid.uuid4())
    await ws.send(json.dumps({"type": "file-start", "transferId": tid, "name": name, "mime": mime, "size": size}))
    return tid


# ---------------------------------------------------------------- checks


async def check_bad_origin(ctx, room):
    try:
        ws, _ = await ctx.join(room, origin="https://evil.example")
        await ws.close()
        return False, "handshake accepted from https://evil.example"
    except InvalidStatus as e:
        return e.response.status_code == 403, f"handshake rejected with HTTP {e.response.status_code}"


async def check_room_full(ctx, room):
    conns = []
    try:
        for _ in range(MAX_PARTICIPANTS):
            ws, first = await ctx.join(room)
            conns.append(ws)
            if first.get("type") != "welcome":
                return False, f"participant {len(conns)} got {first}"
        try:
            ws, first = await ctx.join(room)
            conns.append(ws)
        except ConnectionClosed:
            return True, "9th connection closed immediately"
        code = first.get("code")
        return code == "ROOM_FULL", f"9th participant got {first.get('type')} {code or ''}".strip()
    finally:
        for ws in conns:
            await ws.close()


async def check_text_flood(ctx, room):
    a, _ = await ctx.join(room)
    b, _ = await ctx.join(room)
    try:
        await drain(a, 0.3)
        await drain(b, 0.3)
        for i in range(TEXT_LIMIT + 10):
            await a.send(json.dumps({"type": "text", "text": f"flood {i}"}))
        got_a, got_b = await asyncio.gather(drain(a, 2), drain(b, 2))
        relayed = sum(1 for m in got_b if isinstance(m, dict) and m.get("type") == "text")
        limited = "RATE_LIMITED" in error_codes(got_a)
        return limited and relayed <= TEXT_LIMIT, f"sent {TEXT_LIMIT + 10}, peer received {relayed}, RATE_LIMITED={limited}"
    finally:
        await a.close()
        await b.close()


async def expect_error(ctx, room, expected: str, action) -> tuple[bool, str]:
    ws, _ = await ctx.join(room)
    try:
        await drain(ws, 0.3)
        await action(ws)
        codes = error_codes(await drain(ws, 2))
        return expected in codes, f"server errors: {codes or 'none'}"
    finally:
        await ws.close()


async def check_declared_too_large(ctx, room):
    return await expect_error(ctx, room, "FILE_TOO_LARGE",
                              lambda ws: file_start(ws, "big.zip", "application/zip", MAX_FILE + 1))


async def check_blocked_extension(ctx, room):
    return await expect_error(ctx, room, "TYPE_BLOCKED", lambda ws: file_start(ws, "invoice.exe"))


async def check_renamed_exe(ctx, room):
    async def act(ws):
        tid = await file_start(ws, "holiday.png", "image/png", 64)
        payload = b"MZ" + bytes(62)  # Windows executable header, claimed as PNG
        await ws.send(frame(tid, 0, payload))
    return await expect_error(ctx, room, "TYPE_BLOCKED", act)


async def check_size_overflow(ctx, room):
    async def act(ws):
        tid = await file_start(ws, "small.txt", "text/plain", 1000)
        await ws.send(frame(tid, 0, b"a" * CHUNK))  # far past the declared 1000 bytes
    return await expect_error(ctx, room, "FILE_TOO_LARGE", act)


async def check_one_upload_per_connection(ctx, room):
    async def act(ws):
        await file_start(ws, "one.txt")
        await file_start(ws, "two.txt")
    return await expect_error(ctx, room, "UPLOAD_IN_PROGRESS", act)


async def check_upload_rate(ctx, room):
    ws, _ = await ctx.join(room)
    try:
        await drain(ws, 0.3)
        codes = []
        for i in range(UPLOAD_LIMIT + 1):
            tid = await file_start(ws, f"f{i}.txt", "text/plain", 10)
            await ws.send(frame(tid, 0, b"0123456789"))
            await ws.send(json.dumps({"type": "file-end", "transferId": tid}))
            codes += error_codes(await drain(ws, 0.3))
        return "RATE_LIMITED" in codes, f"{UPLOAD_LIMIT + 1} uploads, server errors: {codes or 'none'}"
    finally:
        await ws.close()


def check_creation_limits(ctx):
    """≤5 concurrent rooms per IP and ≤20 creations per IP per hour.

    Every request also carries a random, spoofed Fly-Client-IP header. If the
    server (or Fly's proxy) trusted a client-supplied value, each request
    would look like a new IP and no limit would ever trigger.
    """
    results = []
    for _ in range(21):
        spoofed = f"198.51.100.{uuid.uuid4().int % 254 + 1}"
        results.append(ctx.create_room({"Fly-Client-IP": spoofed})[0])
    first_reject = next((i + 1 for i, s in enumerate(results) if s != 200), None)
    ok = first_reject is not None and first_reject <= 6
    return ok, f"statuses: {results}; first rejection at request #{first_reject} (spoofed Fly-Client-IP ignored={ok})"


# ---------------------------------------------------------------- load


async def health_sampler(ctx, stop: asyncio.Event, samples: list):
    while not stop.is_set():
        t = time.monotonic()
        try:
            status = await asyncio.to_thread(lambda: urllib.request.urlopen(f"{ctx.base}/healthz", timeout=10, context=SSL_CTX).status)
        except Exception as e:  # noqa: BLE001 — any failure is a data point
            status = type(e).__name__
        samples.append((status, time.monotonic() - t))
        await asyncio.sleep(1)


async def load_room(ctx, code: str, file_mb: int) -> dict:
    sender, _ = await ctx.join(code)
    receiver, _ = await ctx.join(code)
    size = file_mb * 1024 * 1024
    received = {"bytes": 0, "ended": False, "errors": []}

    async def receive():
        while not received["ended"]:
            try:
                msg = await asyncio.wait_for(receiver.recv(), 60)
            except (asyncio.TimeoutError, ConnectionClosed):
                return
            if isinstance(msg, bytes):
                id_len = struct.unpack(">I", msg[:4])[0]
                received["bytes"] += len(msg) - 12 - id_len
                continue
            data = json.loads(msg)
            if data.get("type") == "ping":
                await receiver.send(json.dumps({"type": "pong"}))
            elif data.get("type") == "file-end":
                received["ended"] = True

    async def send():
        t = time.monotonic()
        tid = await file_start(sender, "load.bin", "application/octet-stream", size)
        seq, sent = 0, 0
        while sent < size:
            n = min(CHUNK, size - sent)
            await sender.send(frame(tid, seq, os.urandom(n)))
            seq, sent = seq + 1, sent + n
            if seq % 20 == 0:  # light text chatter, well under the text limit
                await sender.send(json.dumps({"type": "text", "text": f"progress {seq}"}))
        await sender.send(json.dumps({"type": "file-end", "transferId": tid}))
        return time.monotonic() - t

    try:
        recv_task = asyncio.create_task(receive())
        elapsed = await send()
        await asyncio.wait_for(recv_task, 120)
        return {"room": code, "sent_mb": file_mb, "received_mb": received["bytes"] / 2**20,
                "complete": received["ended"], "seconds": elapsed}
    except Exception as e:  # noqa: BLE001
        return {"room": code, "error": f"{type(e).__name__}: {e}"}
    finally:
        await sender.close()
        await receiver.close()


async def run_load(ctx, rooms: int, file_mb: int):
    codes = []
    for _ in range(rooms):
        status, code = ctx.create_room()
        if status != 200:
            print(f"  could not create load room (HTTP {status}); running with {len(codes)}")
            break
        codes.append(code)
    stop, samples = asyncio.Event(), []
    sampler = asyncio.create_task(health_sampler(ctx, stop, samples))
    t = time.monotonic()
    results = await asyncio.gather(*(load_room(ctx, c, file_mb) for c in codes))
    wall = time.monotonic() - t
    stop.set()
    await sampler

    for r in results:
        if "error" in r:
            print(f"  room {r['room']}: FAILED {r['error']}")
        else:
            state = "complete" if r["complete"] and abs(r["received_mb"] - r["sent_mb"]) < 0.01 else "INCOMPLETE"
            print(f"  room {r['room']}: {r['sent_mb']} MB sent, {r['received_mb']:.2f} MB relayed, "
                  f"{r['seconds']:.1f}s ({r['sent_mb'] / r['seconds']:.2f} MB/s) — {state}")
    ok = [s for s, _ in samples if s == 200]
    lat = sorted(d for s, d in samples if s == 200)
    print(f"  total {len(codes) * file_mb} MB in {wall:.1f}s wall")
    if lat:
        print(f"  /healthz during load: {len(ok)}/{len(samples)} OK, "
              f"median {lat[len(lat) // 2] * 1000:.0f} ms, max {lat[-1] * 1000:.0f} ms")
    failures = [s for s, _ in samples if s != 200]
    if failures:
        print(f"  /healthz failures: {failures}")
    if len(codes) < rooms:
        print(f"  only {len(codes)}/{rooms} load rooms could be created — counts as a failure")
    return (len(codes) == rooms and all("error" not in r and r["complete"] for r in results)
            and not failures)


# ---------------------------------------------------------------- main


async def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--base", default="https://api.quickdot.amartian.ca")
    p.add_argument("--origin", default="https://quickdot.amartian.ca")
    p.add_argument("--load-rooms", type=int, default=3)
    p.add_argument("--file-mb", type=int, default=25)
    p.add_argument("--skip-load", action="store_true")
    p.add_argument("--creation-limits", action="store_true",
                   help="also test per-IP room creation limits (uses up your IP's hourly quota)")
    args = p.parse_args()
    ctx = Ctx(args.base, args.origin)

    print(f"Target {ctx.base} (origin {ctx.origin})\n\nLimit checks")
    status, room = ctx.create_room()
    status_b, room_b = ctx.create_room()
    if status != 200 or status_b != 200:
        sys.exit(f"could not create test rooms: HTTP {status}/{status_b}")

    checks = [
        ("WebSocket origin check", check_bad_origin, room),
        ("max 8 participants", check_room_full, room),
        ("text flood throttled (30/10s)", check_text_flood, room),
        ("declared size > 25 MB rejected", check_declared_too_large, room),
        ("blocked extension (.exe)", check_blocked_extension, room),
        ("renamed .exe caught by MIME sniff", check_renamed_exe, room),
        ("bytes beyond declared size rejected", check_size_overflow, room),
        ("1 concurrent upload per connection", check_one_upload_per_connection, room),
        ("uploads ≤ 10 per room per minute", check_upload_rate, room_b),
    ]
    passed = True
    for name, fn, r in checks:
        try:
            ok, detail = await fn(ctx, r)
        except Exception as e:  # noqa: BLE001
            ok, detail = False, f"{type(e).__name__}: {e}"
        passed &= ok
        print(f"  [{'PASS' if ok else 'FAIL'}] {name} — {detail}")

    if not args.skip_load:
        print(f"\nLoad: {args.load_rooms} rooms × {args.file_mb} MB file, 2 participants each")
        passed &= await run_load(ctx, args.load_rooms, args.file_mb)

    # Last: this deliberately exhausts the per-IP room quota.
    if args.creation_limits:
        ok, detail = check_creation_limits(ctx)
        passed &= ok
        print(f"\n  [{'PASS' if ok else 'FAIL'}] room creation ≤5 concurrent / ≤20 per hour per IP — {detail}")

    print("\nRESULT:", "all passed" if passed else "FAILURES above")
    sys.exit(0 if passed else 1)


if __name__ == "__main__":
    asyncio.run(main())
