"""Builds app/src/main/assets/cities.bin, the offline city list, from GeoNames data.

GeoNames (https://www.geonames.org) publishes the data under CC BY 4.0.
Download cities5000.zip and admin1CodesASCII.txt from
https://download.geonames.org/export/dump/, unzip the first, then run:

  python3 tools/make_cities.py cities5000.txt admin1CodesASCII.txt app/src/main/assets/cities.bin

Format (big-endian, as Java's DataInputStream reads it):
  "ADHC", version byte 2
  u16 region count, then per region: 2 byte country code, u8 length, UTF-8 name
    (an empty name stands for a country's places that have no region)
  i32 city count, then per city, most populous first:
    24-bit latitude and longitude in 1/10000 degrees (about 11 m),
    u16 region index, u8 length, UTF-8 name
  i32 other name count, then per name, in city order: 24-bit city index, u8 length, UTF-8 name
    (names in Arabic and Cyrillic script, so the city search works in the app's Arabic and Russian)
"""
import re
import struct
import sys

cities_path, regions_path, out_path = sys.argv[1:4]

# Districts of a city (PPLX) would name a London suburb instead of London; the rest no longer exist
SKIP = {"PPLX", "PPLH", "PPLQ", "PPLW", "PPLCH"}

# Other names, where people are likely to search in that script: across the Arab world and the former
# Soviet Union, and for every city of 300,000 or more. All of GeoNames' would double the file's size.
ARABIC, CYRILLIC = re.compile(r"[\u0600-\u06FF]"), re.compile(r"[\u0400-\u04FF]")
ARAB_WORLD = set("AE BH DJ DZ EG EH IQ JO KM KW LB LY MA MR OM PS QA SA SD SO SY TN YE".split())
FORMER_USSR = set("AM AZ BY GE KG KZ MD RU TJ TM UA UZ".split())
BIG = 300000

region_names = {}
with open(regions_path, encoding="utf-8") as f:
    for line in f:
        code, name = line.rstrip("\n").split("\t")[:2]
        region_names[code] = name

cities = []
with open(cities_path, encoding="utf-8") as f:
    for line in f:
        col = line.rstrip("\n").split("\t")
        if col[7] in SKIP or len(col[8]) != 2:
            continue
        cities.append((int(col[14] or 0), col[1], col[8], col[10], float(col[4]), float(col[5]), col[3]))
cities.sort(key=lambda c: (-c[0], c[1]))

# The region carries the country, so a place without a known region gets its country's blank one
regions, region_index = [], {}
for _, _, cc, admin1, _, _, _ in cities:
    key = f"{cc}.{admin1}" if f"{cc}.{admin1}" in region_names else cc
    if key not in region_index:
        region_index[key] = len(regions)
        regions.append((cc, region_names.get(key, "")))


def text(value):
    data = value.encode("utf-8")[:255]
    while True:  # never cut a character in half
        try:
            data.decode("utf-8")
            break
        except UnicodeDecodeError:
            data = data[:-1]
    return struct.pack(">B", len(data)) + data


out = bytearray(b"ADHC") + struct.pack(">B", 2)
out += struct.pack(">H", len(regions))
for cc, name in regions:
    out += cc.encode("ascii") + text(name)
out += struct.pack(">i", len(cities))
for _, name, cc, admin1, lat, lon, _ in cities:
    key = f"{cc}.{admin1}" if f"{cc}.{admin1}" in region_names else cc
    out += struct.pack(">i", round(lat * 10000))[1:] + struct.pack(">i", round(lon * 10000))[1:]
    out += struct.pack(">H", region_index[key]) + text(name)

others = []
for index, (population, name, cc, _, _, _, alternates) in enumerate(cities):
    for script, countries in ((ARABIC, ARAB_WORLD), (CYRILLIC, FORMER_USSR)):
        if cc in countries or population >= BIG:
            for other in dict.fromkeys(a.strip() for a in alternates.split(",")):
                if other and script.search(other):
                    others.append((index, other))
out += struct.pack(">i", len(others))
for index, other in others:
    out += struct.pack(">i", index)[1:] + text(other)

with open(out_path, "wb") as f:
    f.write(out)
print(f"{len(cities)} places, {len(regions)} regions, {len(others)} other names, {len(out)} bytes")
