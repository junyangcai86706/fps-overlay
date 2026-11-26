import struct, sys

def u16(x): return struct.pack('<H', x)
def u32(x): return struct.pack('<I', x)

ATTRS = [("name", 0x01010003), ("label", 0x01010001),
         ("exported", 0x01010010), ("minSdkVersion", 0x0101020C),
         ("targetSdkVersion", 0x01010270)]

strs = [a[0] for a in ATTRS]
idx = {a[0]: i for i, a in enumerate(ATTRS)}
def sid(s):
    if s not in idx:
        idx[s] = len(strs); strs.append(s)
    return idx[s]

for s in ["android", "http://schemas.android.com/apk/res/android", "manifest", "package",
          "com.dsh.fpsmeter", "uses-sdk", "uses-permission",
          "android.permission.SYSTEM_ALERT_WINDOW",
          "android.permission.FOREGROUND_SERVICE",
          "android.permission.POST_NOTIFICATIONS",
          "android.permission.PACKAGE_USAGE_STATS",
          "application", "FPS 悬浮窗", "activity", "intent-filter",
          "android.intent.action.MAIN", "android.intent.category.LAUNCHER",
          "category", "action", "service", ".MainActivity", ".FpsService", ".CurveActivity"]:
    sid(s)

def pool(data_strs):
    body = b''
    offs = []
    for s in data_strs:
        b = s.encode('utf-8')
        offs.append(len(body))
        if len(s) < 128 and len(b) < 128:
            body += bytes([len(s), len(b)]) + b + b'\x00'
        else:
            raise SystemExit("string too long")
    pad = (-len(body)) % 4
    body += b'\x00' * pad
    hs = 28
    size = hs + 4 * len(data_strs) + len(body)
    out = u16(1) + u16(hs) + u32(size)
    out += u32(len(data_strs)) + u32(0) + u32(0x00000100) + u32(hs + 4 * len(data_strs)) + u32(0)
    for o in offs: out += u32(o)
    return out + body

def resmap():
    out = u16(0x0180) + u16(8) + u32(8 + 4 * len(ATTRS))
    for _, rid in ATTRS: out += u32(rid)
    return out

def ns_start(prefix, uri):
    return u16(0x0100) + u16(16) + u32(24) + u32(0) + u32(0) + u32(sid(prefix)) + u32(sid(uri))

def ns_end(prefix, uri):
    return u16(0x0101) + u16(16) + u32(24) + u32(0) + u32(0) + u32(sid(prefix)) + u32(sid(uri))

def elem(name, attrs, children=()):
    # attrs: list of (attr_name, kind, value) kind in {'s','i','b'}
    body = b''
    for an, kind, val in attrs:
        ai = idx[an]
        ans = 0xFFFFFFFF if an == "package" else sid("http://schemas.android.com/apk/res/android")
        if kind == 's':
            raw = sid(val); tv = u16(8) + b'\x00' + bytes([0x03]) + u32(sid(val))
        elif kind == 'i':
            raw = 0xFFFFFFFF; tv = u16(8) + b'\x00' + bytes([0x10]) + u32(val)
        else:
            raw = 0xFFFFFFFF; tv = u16(8) + b'\x00' + bytes([0x12]) + u32(1 if val else 0)
        body += u32(ans) + u32(ai) + u32(raw) + tv
    ext = u32(0xFFFFFFFF) + u32(sid(name))
    ext += u16(20) + u16(20) + u16(len(attrs)) + u16(0) + u16(0) + u16(0)
    size = 16 + len(ext) + len(body)
    out = u16(0x0102) + u16(16) + u32(size) + u32(0) + u32(0) + ext + body
    for c in children: out += c
    out += u16(0x0103) + u16(16) + u32(24) + u32(0) + u32(0) + u32(0xFFFFFFFF) + u32(sid(name))
    return out

A = "android"
main_if = elem("intent-filter", [], [
    elem("action", [("name", 's', "android.intent.action.MAIN")]),
    elem("category", [("name", 's', "android.intent.category.LAUNCHER")]),
])
application = elem("application", [("label", 's', "FPS 悬浮窗")], [
    elem("activity", [("name", 's', ".MainActivity"), ("exported", 'b', 1), ("label", 's', "FPS 悬浮窗")], [main_if]),
    elem("activity", [("name", 's', ".CurveActivity"), ("exported", 'b', 0), ("label", 's', "曲线")]),
    elem("service", [("name", 's', ".FpsService"), ("exported", 'b', 0)]),
])
root = elem("manifest", [("package", 's', "com.dsh.fpsmeter")], [
    elem("uses-sdk", [("minSdkVersion", 'i', 26), ("targetSdkVersion", 'i', 33)]),
    elem("uses-permission", [("name", 's', "android.permission.SYSTEM_ALERT_WINDOW")]),
    elem("uses-permission", [("name", 's', "android.permission.FOREGROUND_SERVICE")]),
    elem("uses-permission", [("name", 's', "android.permission.POST_NOTIFICATIONS")]),
    elem("uses-permission", [("name", 's', "android.permission.PACKAGE_USAGE_STATS")]),
    application,
])

head = u16(0x0003) + u16(8)
body = ns_start("android", "http://schemas.android.com/apk/res/android") + root + ns_end("android", "http://schemas.android.com/apk/res/android")
total = 8 + len(pool(strs)) + len(resmap()) + len(body)
out = head + u32(total) + pool(strs) + resmap() + body
open("AndroidManifest.bin", "wb").write(out)
print("manifest bytes", len(out), "strings", len(strs))
