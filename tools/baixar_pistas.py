"""Baixa do OpenStreetMap (Overpass API) as pistas de skate do Brasil, com cidade e estado.
Dados © colaboradores do OpenStreetMap, licença ODbL (openstreetmap.org/copyright)."""
import json, time, urllib.request, urllib.parse, sys

QUERY = r"""
[out:json][timeout:600];
area["ISO3166-1"="BR"][admin_level=2]->.br;
( nwr["sport"~"skateboard"](area.br); nwr["leisure"="skatepark"](area.br); );
out center tags;
"""
ENDPOINTS = ["https://overpass-api.de/api/interpreter",
             "https://overpass.kumi.systems/api/interpreter",
             "https://overpass.private.coffee/api/interpreter"]

import os; os.makedirs("data", exist_ok=True)
LOG=open("data/pistas_log.txt","w")
def log(*a):
    print(*a); print(*a, file=LOG); LOG.flush()

def fetch():
    data = urllib.parse.urlencode({"data": QUERY}).encode()
    for tentativa in range(3):
        for url in ENDPOINTS:
            try:
                req = urllib.request.Request(url, data=data, headers={"User-Agent": "ISL-BLADE-app/1.0 (github.com/contatoamauriramirez-dotcom/ISL-Tricks)"})
                with urllib.request.urlopen(req, timeout=700) as r:
                    return json.load(r)
            except Exception as e:
                log("falhou", url, repr(e)); time.sleep(20)
    raise SystemExit("Overpass indisponível")

UF={"Acre":"AC","Alagoas":"AL","Amapa":"AP","Amazonas":"AM","Bahia":"BA","Ceara":"CE","Federal District":"DF","Espirito Santo":"ES","Goias":"GO","Maranhao":"MA","Mato Grosso":"MT","Mato Grosso do Sul":"MS","Minas Gerais":"MG","Para":"PA","Paraiba":"PB","Parana":"PR","Pernambuco":"PE","Piaui":"PI","Rio de Janeiro":"RJ","Rio Grande do Norte":"RN","Rio Grande do Sul":"RS","Rondonia":"RO","Roraima":"RR","Santa Catarina":"SC","Sao Paulo":"SP","Sergipe":"SE","Tocantins":"TO"}
def main():
    els = fetch()["elements"]
    log("elementos:", len(els))
    pistas = []
    for el in els:
        t = el.get("tags", {})
        lat = el.get("lat") or el.get("center", {}).get("lat")
        lon = el.get("lon") or el.get("center", {}).get("lon")
        if lat is None: continue
        rua = " ".join(x for x in [t.get("addr:street", ""), t.get("addr:housenumber", "")] if x).strip()
        bairro = t.get("addr:suburb") or t.get("addr:neighbourhood") or ""
        p = {"id": f'{el["type"][0]}{el["id"]}', "n": (t.get("name") or "").strip(),
             "lat": round(lat, 6), "lon": round(lon, 6),
             "a": ", ".join(x for x in [rua, bairro] if x), "c": t.get("addr:city", ""), "uf": ""}
        if t.get("surface"): p["s"] = t["surface"]
        if t.get("lit") == "yes": p["l"] = 1
        if t.get("access") in ("private", "customers"): p["p"] = 1
        # Fotos com licença aberta: Wikimedia Commons e Mapillary (CC BY-SA).
        wc = t.get("wikimedia_commons", "")
        img = t.get("image", "")
        if wc.startswith("File:"): p["wc"] = wc[5:]
        elif "commons.wikimedia.org" in img and "File:" in img: p["wc"] = img.split("File:")[-1]
        elif "upload.wikimedia.org" in img: p["wu"] = img
        if t.get("mapillary"): p["mp"] = t["mapillary"]
        pistas.append(p)
    import reverse_geocoder as rg, unicodedata
    res = rg.search([(p["lat"], p["lon"]) for p in pistas], mode=1) if pistas else []
    def sem_acento(x): return unicodedata.normalize("NFKD", x).encode("ascii","ignore").decode()
    for p, r in zip(pistas, res):
        if not p["c"]: p["c"] = r.get("name", "")
        p["uf"] = UF.get(sem_acento(r.get("admin1", "")), r.get("admin1", ""))
    vistos, unicos = set(), []
    for p in pistas:
        k = (round(p["lat"], 4), round(p["lon"], 4))
        if k in vistos: continue
        vistos.add(k); unicos.append(p)
    unicos.sort(key=lambda p: (p.get("uf", ""), p.get("c", ""), p.get("n", "")))
    out = {"fonte": "© colaboradores do OpenStreetMap (ODbL)", "gerado": time.strftime("%Y-%m-%d"), "total": len(unicos), "pistas": unicos}
    json.dump(out, open("data/pistas_brasil.json", "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
    log("pistas:", len(unicos), "sem nome:", sum(1 for p in unicos if not p["n"]), "commons:", sum(1 for p in unicos if p.get("wc") or p.get("wu")), "mapillary:", sum(1 for p in unicos if p.get("mp")))

main()
