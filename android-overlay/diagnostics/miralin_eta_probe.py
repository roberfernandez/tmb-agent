#!/usr/bin/env python3
"""Read-only diagnostic of Miralin's Engine.IO polling station subscription."""
import json, time, urllib.request, urllib.parse, sys
BASE = "https://miralin.cat/socket.io/?EIO=4&transport=polling"
stop = sys.argv[1] if len(sys.argv)>1 else "413"
def request(sid=None,body=None):
    url=BASE+("&sid="+urllib.parse.quote(sid) if sid else "")
    data=body.encode() if body is not None else None
    req=urllib.request.Request(url,data=data,headers={"Content-Type":"text/plain;charset=UTF-8","User-Agent":"TMB-Agent-ETA-Diagnostic/1.0"})
    with urllib.request.urlopen(req,timeout=45) as r:return r.read().decode()
hello=request()
print("handshake:",hello[:160],flush=True)
sid=json.loads(hello[1:])["sid"]
request(sid,"40")
connected=False; subscribed=False; seen={}
deadline=time.monotonic()+55
while time.monotonic()<deadline:
    payload=request(sid)
    for packet in payload.split("\x1e"):
        if packet=="2":request(sid,"3")
        elif packet.startswith("40"):
            connected=True
            print("namespace connected",flush=True)
        elif packet.startswith("42"):
            try:
                event=json.loads(packet[2:])
                name=event[0]
                seen[name]=seen.get(name,0)+1
                if name=="stopDataUpdate":
                    print("stopDataUpdate:",json.dumps(event[1],ensure_ascii=False)[:2500],flush=True)
                elif seen[name]==1:print("event:",name,"sample:",json.dumps(event[1],ensure_ascii=False)[:400],flush=True)
            except Exception as exc:print("unparsed event:",repr(exc),flush=True)
        elif packet.startswith(("41","44","1")):print("socket closed/rejected:",packet[:200],flush=True);sys.exit(2)
    if connected and not subscribed:
        message="42"+json.dumps(["subscribeStop",{"line":"L4","stop":stop}],separators=(",",":"))
        print("sending:",message,flush=True)
        request(sid,message)
        subscribed=True
print("summary:",json.dumps(seen),flush=True)
if not seen.get("stopDataUpdate"):sys.exit(3)
