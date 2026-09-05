// Binary wire framing for file-chunk, mirroring
// backend/src/main/java/ca/amartian/quickdot/relay/ChunkFrame.java:
//   [4 bytes: transferId UTF-8 byte length (uint32, big-endian)]
//   [N bytes: transferId, UTF-8]
//   [8 bytes: sequence number (uint64, big-endian)]
//   [remaining bytes: chunk payload]

export interface ChunkFrame {
  transferId: string;
  sequence: number;
  payload: Uint8Array;
}

const encoder = new TextEncoder();
const decoder = new TextDecoder();

export function encodeChunkFrame(transferId: string, sequence: number, payload: Uint8Array): ArrayBuffer {
  const idBytes = encoder.encode(transferId);
  const buffer = new ArrayBuffer(4 + idBytes.length + 8 + payload.length);
  const view = new DataView(buffer);
  let offset = 0;
  view.setUint32(offset, idBytes.length);
  offset += 4;
  new Uint8Array(buffer, offset, idBytes.length).set(idBytes);
  offset += idBytes.length;
  view.setBigUint64(offset, BigInt(sequence));
  offset += 8;
  new Uint8Array(buffer, offset).set(payload);
  return buffer;
}

export function decodeChunkFrame(buffer: ArrayBuffer): ChunkFrame {
  const view = new DataView(buffer);
  let offset = 0;
  const idLength = view.getUint32(offset);
  offset += 4;
  const transferId = decoder.decode(new Uint8Array(buffer, offset, idLength));
  offset += idLength;
  const sequence = Number(view.getBigUint64(offset));
  offset += 8;
  const payload = new Uint8Array(buffer, offset);
  return { transferId, sequence, payload };
}
