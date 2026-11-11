#!/usr/bin/env python3
"""Read-only public Miralin client and socket contract inspection. No TMB login."""
import json, re, socket, time, urllib.request, urllib.parse
from html.parser import HTMLParser
from datetime import datetime, timezone

ORIGIN = "https://miralin.cat"
# Match the IPv4 transport already used by our Miralin backend.
original_getaddrinfo = socket.getaddrinfo
def ipv4_addresses(host, port, family=0, type=0, proto=0, flags=0):
    return original_getaddrinfo(host, port, socket.AF_INET, type, proto, flags)
socket.getaddrinfo = ipv4_addresses
def get(url, body=None, timeout=15):
    req = urllib.request.Request(url, data=body.encode() if body is not None else None,
        headers={"User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/130.0.0.0 Safari/537.36", "Content-Type": "text/plain;charset=UTF-8"})
    with urllib.request.urlopen(req, timeout=timeout) as response:
        return response.read(4 * 1024 * 1024).decode("utf-8", errors="replace")
class Scripts(HTMLParser):
    def __init__(self): super().__init__(); self.urls=[]
    def handle_starttag(self, tag, attrs):
        if tag == "script":
            for key, value in attrs:
                if key == "src": self.urls.append(urllib.parse.urljoin(ORIGIN + "/", value))
print("CAPTURE", datetime.now(timezone.utc).isoformat(), flush=True)
index = get(ORIGIN + "/")
parser = Scripts(); parser.feed(index)
print("SCRIPT_URLS", json.dumps(parser.urls), flush=True)
code_list=[("index-inline", index)]
for url in parser.urls:
    if urllib.parse.urlparse(url).netloc != "miralin.cat" or "socket.io" in url: continue
    try: code_list.append((url, get(url)))
    except Exception as exc: print("ASSET_ERROR", url, type(exc).__name__, flush=True)
pattern = re.compile(r"secPreviousTrain|stopDataUpdate|trenesV[12]|headway|Dist\\.|distancia|previousTrain", re.I)
for url, code in code_list:
    print("SOURCE", url, "LENGTH", len(code), flush=True)
    positions=[]
    for match in pattern.finditer(code):
        if positions and match.start() < positions[-1] + 350: continue
        positions.append(match.start())
        if len(positions) >= 45: break
    for i, p in enumerate(positions):
        print("CODE", url, i, repr(code[max(0,p-260):p+650]), flush=True)

base=ORIGIN+"/socket.io/?EIO=4&transport=polling"
hello=get(base); print("HANDSHAKE_OK", hello.startswith("0"), flush=True)
sid=json.loads(hello[1:])["sid"]
url=base+"&sid="+urllib.parse.quote(sid)
get(url, "40")
subscribed=False; seen={}; deadline=time.monotonic()+45
try:
    while time.monotonic()<deadline:
        for packet in get(url).split("\x1e"):
            if packet=="2": get(url, "3")
            elif packet.startswith("40") and not subscribed:
                get(url, '42["subscribeStop",{"line":"L4","stop":"413"}]'); subscribed=True
            elif packet.startswith("42"):
                event=json.loads(packet[2:]); name=event[0]; seen[name]=seen.get(name,0)+1
                if name=="stopDataUpdate" and seen[name]<=2: print("BOARD", json.dumps(event[1],ensure_ascii=False), flush=True)
                elif name=="mapUpdate" and seen[name]==1:
                    data=event[1].get("L4",{})
                    print("MAP_KEYS", list(data), flush=True)
                    print("MAP_L4", json.dumps(data,ensure_ascii=False)[:22000], flush=True)
        if seen.get("stopDataUpdate",0)>=2: break
finally:
    try: get(url, "1", timeout=3)
    except Exception: pass
print("EVENT_COUNTS", json.dumps(seen), flush=True)
