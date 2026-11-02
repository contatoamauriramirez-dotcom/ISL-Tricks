"""Baixa do OpenStreetMap (Overpass API) as pistas de skate do Brasil, com cidade e estado.
Dados © colaboradores do OpenStreetMap, licença ODbL (openstreetmap.org/copyright)."""
import json, time, urllib.request, urllib.parse, sys

QUERY = r"""
[out:json][timeout:1500];
area["ISO3166-1"="BR"][admin_level=2]->.br;
( nwr["sport"~"skateboard"](area.br); nwr["leisure"="skatepark"](area.br); )->.pistas;
foreach.pistas->.e(
  .e out center tags;
  .e is_in->.a;
  area.a[admin_level~"^(4|8)$"][boundary=administrative];
  out tags;
);
"""
ENDPOINTS = ["https://overpass-api.de/api/interpreter",
             "https://overpass.kumi.systems/api/interpreter",
             "https://overpass.private.coffee/api/interpreter"]

def fetch():
    data = urllib.parse.urlencode({"data": QUERY}).encode()
    for tentativa in range(3):
        for url in ENDPOINTS:
            try:
                req = urllib.request.Request(url, data=data, headers={"User-Agent": "ISL-BLADE-app/1.0 (github.com/contatoamauriramirez-dotcom/ISL-Tricks)"})
                with urllib.request.urlopen(req, timeout=1800) as r:
                    return json.load(r)
            except Exception as e:
                print("falhou", url, e, file=sys.stderr); time.sleep(20)
    raise SystemExit("Overpass indisponível")

def main():
    els = fetch()["elements"]
    pistas, atual = [], None
    for el in els:
        if el["type"] == "area":
            if atual is None: continue
            t = el.get("tags", {})
            if t.get("admin_level") == "8" and not atual.get("c"): atual["c"] = t.get("name", "")
            if t.get("admin_level") == "4" and not atual.get("uf"):
                iso = t.get("ISO3166-2", "")
                atual["uf"] = iso.split("-")[-1] if iso.startswith("BR-") else t.get("name", "")
            continue
        t = el.get("tags", {})
        lat = el.get("lat") or el.get("center", {}).get("lat")
        lon = el.get("lon") or el.get("center", {}).get("lon")
        if lat is None: atual = None; continue
        rua = " ".join(x for x in [t.get("addr:street", ""), t.get("addr:housenumber", "")] if x).strip()
        bairro = t.get("addr:suburb") or t.get("addr:neighbourhood") or ""
        atual = {"id": f'{el["type"][0]}{el["id"]}', "n": (t.get("name") or "").strip(),
                 "lat": round(lat, 6), "lon": round(lon, 6),
                 "a": ", ".join(x for x in [rua, bairro] if x),
                 "c": t.get("addr:city", ""), "uf": t.get("addr:state", "")}
        if t.get("surface"): atual["s"] = t["surface"]
        if t.get("lit") == "yes": atual["l"] = 1
        if t.get("access") in ("private", "customers"): atual["p"] = 1
        pistas.append(atual)
    vistos, unicos = set(), []
    for p in pistas:
        k = (round(p["lat"], 4), round(p["lon"], 4))
        if k in vistos: continue
        vistos.add(k); unicos.append(p)
    unicos.sort(key=lambda p: (p.get("uf", ""), p.get("c", ""), p.get("n", "")))
    out = {"fonte": "© colaboradores do OpenStreetMap (ODbL)", "gerado": time.strftime("%Y-%m-%d"), "total": len(unicos), "pistas": unicos}
    json.dump(out, open("data/pistas_brasil.json", "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
    print("pistas:", len(unicos), "sem cidade:", sum(1 for p in unicos if not p["c"]))

main()
