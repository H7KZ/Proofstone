# T-M10-01 spike tool, not production code. Turns on the "Beta APIs" experiment in a BDS level.dat (little-endian NBT with an
# 8-byte header: storage version, payload length). @minecraft/server-net exists only as a beta module, and BDS has no
# server.properties key for experiments, so the world itself must carry the toggle. Sets, in the root compound:
#   experiments: { gametest: 1b, experiments_ever_used: 1b, saved_with_toggled_experiments: 1b }
# Usage: python -I leveldat.py <level.dat in> <level.dat out>    (prints the experiments compound before and after)
import struct
import sys


def rd_str(b, i):
    n = struct.unpack_from("<H", b, i)[0]
    return b[i + 2:i + 2 + n].decode("utf-8"), i + 2 + n


def rd_payload(t, b, i):
    if t == 1: return struct.unpack_from("<b", b, i)[0], i + 1
    if t == 2: return struct.unpack_from("<h", b, i)[0], i + 2
    if t == 3: return struct.unpack_from("<i", b, i)[0], i + 4
    if t == 4: return struct.unpack_from("<q", b, i)[0], i + 8
    if t == 5: return struct.unpack_from("<f", b, i)[0], i + 4
    if t == 6: return struct.unpack_from("<d", b, i)[0], i + 8
    if t == 7:
        n = struct.unpack_from("<i", b, i)[0]; return bytes(b[i + 4:i + 4 + n]), i + 4 + n
    if t == 8: return rd_str(b, i)
    if t == 9:
        et = b[i]; n = struct.unpack_from("<i", b, i + 1)[0]; i += 5; out = []
        for _ in range(n):
            v, i = rd_payload(et, b, i); out.append(v)
        return (et, out), i
    if t == 10:
        out = []
        while True:
            ct = b[i]; i += 1
            if ct == 0: return out, i
            name, i = rd_str(b, i)
            v, i = rd_payload(ct, b, i)
            out.append((ct, name, v))
    if t == 11:
        n = struct.unpack_from("<i", b, i)[0]; return list(struct.unpack_from("<%di" % n, b, i + 4)), i + 4 + 4 * n
    if t == 12:
        n = struct.unpack_from("<i", b, i)[0]; return list(struct.unpack_from("<%dq" % n, b, i + 4)), i + 4 + 8 * n
    raise ValueError("unknown tag type %d at %d" % (t, i))


def wr_str(s):
    e = s.encode("utf-8"); return struct.pack("<H", len(e)) + e


def wr_payload(t, v):
    if t == 1: return struct.pack("<b", v)
    if t == 2: return struct.pack("<h", v)
    if t == 3: return struct.pack("<i", v)
    if t == 4: return struct.pack("<q", v)
    if t == 5: return struct.pack("<f", v)
    if t == 6: return struct.pack("<d", v)
    if t == 7: return struct.pack("<i", len(v)) + v
    if t == 8: return wr_str(v)
    if t == 9:
        et, items = v
        return bytes([et]) + struct.pack("<i", len(items)) + b"".join(wr_payload(et, x) for x in items)
    if t == 10:
        return b"".join(bytes([ct]) + wr_str(n) + wr_payload(ct, x) for ct, n, x in v) + b"\x00"
    if t == 11: return struct.pack("<i", len(v)) + struct.pack("<%di" % len(v), *v)
    if t == 12: return struct.pack("<i", len(v)) + struct.pack("<%dq" % len(v), *v)
    raise ValueError(t)


def main():
    data = open(sys.argv[1], "rb").read()
    ver, length = struct.unpack_from("<ii", data, 0)
    body = data[8:8 + length]
    if body[0] != 10: raise ValueError("root is not a compound")
    rootname, i = rd_str(body, 1)
    root, end = rd_payload(10, body, i)
    if end != len(body): raise ValueError("trailing bytes: parsed %d of %d" % (end, len(body)))
    if wr_payload(10, root) != body[i:]: raise ValueError("round trip differs; refusing to write")
    exp = next((e for e in root if e[1] == "experiments"), None)
    print("before:", exp)
    want = [(1, "experiments_ever_used", 1), (1, "gametest", 1), (1, "saved_with_toggled_experiments", 1)]
    if exp is None:
        root.append((10, "experiments", want))
    else:
        keep = [x for x in exp[2] if x[1] not in {w[1] for w in want}]
        root[root.index(exp)] = (10, "experiments", keep + want)
    print("after:", next(e for e in root if e[1] == "experiments"))
    out = bytes([10]) + wr_str(rootname) + wr_payload(10, root)
    open(sys.argv[2], "wb").write(struct.pack("<ii", ver, len(out)) + out)
    print("storage version", ver, "length", length, "->", len(out))


main()
