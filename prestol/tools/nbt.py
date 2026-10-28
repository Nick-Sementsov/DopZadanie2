"""Minimal NBT reader/writer (big-endian, optionally gzip) for Structurize blueprints."""
import gzip, io, struct

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BARR, STR, LIST, COMP, IARR, LARR = range(13)

class Tag:
    __slots__ = ('t', 'v', 'lt')
    def __init__(self, t, v, lt=END):
        self.t, self.v, self.lt = t, v, lt
    def __repr__(self):
        return f'Tag({self.t},{self.v!r})'

def _wrap(t, p):
    return p if isinstance(p, Tag) else Tag(t, p)

def _r(f, n):
    b = f.read(n)
    if len(b) != n: raise EOFError
    return b

def _str(f):
    n = struct.unpack('>H', _r(f, 2))[0]
    return _r(f, n).decode('utf-8', 'surrogatepass')

def _payload(f, t):
    if t == BYTE: return struct.unpack('>b', _r(f, 1))[0]
    if t == SHORT: return struct.unpack('>h', _r(f, 2))[0]
    if t == INT: return struct.unpack('>i', _r(f, 4))[0]
    if t == LONG: return struct.unpack('>q', _r(f, 8))[0]
    if t == FLOAT: return struct.unpack('>f', _r(f, 4))[0]
    if t == DOUBLE: return struct.unpack('>d', _r(f, 8))[0]
    if t == BARR:
        n = struct.unpack('>i', _r(f, 4))[0]; return bytearray(_r(f, n))
    if t == STR: return _str(f)
    if t == LIST:
        lt = _r(f, 1)[0]; n = struct.unpack('>i', _r(f, 4))[0]
        return Tag(LIST, [_wrap(lt, _payload(f, lt)) for _ in range(n)], lt)
    if t == COMP:
        d = {}
        while True:
            ct = _r(f, 1)[0]
            if ct == END: return d
            name = _str(f); d[name] = _wrap(ct, _payload(f, ct))
    if t == IARR:
        n = struct.unpack('>i', _r(f, 4))[0]; return list(struct.unpack(f'>{n}i', _r(f, 4 * n)))
    if t == LARR:
        n = struct.unpack('>i', _r(f, 4))[0]; return list(struct.unpack(f'>{n}q', _r(f, 8 * n)))
    raise ValueError(t)

def read(data):
    if data[:2] == b'\x1f\x8b': data = gzip.decompress(data)
    f = io.BytesIO(data)
    t = _r(f, 1)[0]; name = _str(f)
    p = _payload(f, t)
    if isinstance(p, Tag): return name, p
    return name, Tag(t, p)

def _wstr(o, s):
    b = s.encode('utf-8', 'surrogatepass'); o.write(struct.pack('>H', len(b))); o.write(b)

def _wpayload(o, tag):
    t, v = tag.t, tag.v
    if t == BYTE: o.write(struct.pack('>b', v))
    elif t == SHORT: o.write(struct.pack('>h', v))
    elif t == INT: o.write(struct.pack('>i', v))
    elif t == LONG: o.write(struct.pack('>q', v))
    elif t == FLOAT: o.write(struct.pack('>f', v))
    elif t == DOUBLE: o.write(struct.pack('>d', v))
    elif t == BARR: o.write(struct.pack('>i', len(v))); o.write(bytes(v))
    elif t == STR: _wstr(o, v)
    elif t == LIST:
        lt = v[0].t if v else tag.lt
        o.write(bytes([lt])); o.write(struct.pack('>i', len(v)))
        for e in v: _wpayload(o, e)
    elif t == COMP:
        for k, e in v.items():
            o.write(bytes([e.t])); _wstr(o, k); _wpayload(o, e)
        o.write(b'\x00')
    elif t == IARR: o.write(struct.pack('>i', len(v))); o.write(struct.pack(f'>{len(v)}i', *v))
    elif t == LARR: o.write(struct.pack('>i', len(v))); o.write(struct.pack(f'>{len(v)}q', *v))

def write(name, tag, compress=True):
    o = io.BytesIO(); o.write(bytes([tag.t])); _wstr(o, name); _wpayload(o, tag)
    data = o.getvalue()
    return gzip.compress(data, mtime=0) if compress else data

def plain(tag):
    """Converts to plain python for printing."""
    if tag.t == COMP: return {k: plain(v) for k, v in tag.v.items()}
    if tag.t == LIST: return [plain(e) for e in tag.v]
    return tag.v
