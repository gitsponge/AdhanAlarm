"""Builds app/src/main/assets/cities.bin, the offline city list, from GeoNames data.

GeoNames (https://www.geonames.org) publishes the data under CC BY 4.0.
Download cities5000.zip and admin1CodesASCII.txt from
https://download.geonames.org/export/dump/, unzip the first, then run:

  python3 tools/make_cities.py cities5000.txt admin1CodesASCII.txt app/src/main/assets/cities.bin

The list follows GeoNames except in a few disputed places (MOVED, RENAMED and MOVED_POINTS below),
which is why the app's Credits say it has been changed.

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

# Districts of a city (PPLX) would name a London suburb instead of London; the rest no longer exist
SKIP = {"PPLX", "PPLH", "PPLQ", "PPLW", "PPLCH"}

# Disputed places. GeoNames mostly follows the UN's lines; these bring its few exceptions into line, and give
# places the names their own people use. Each rule must match exactly one place, so that a newer GeoNames can't
# drop one unnoticed. The app adds two more in CityDatabase.City: Palestine is named Palestine rather than the
# "Palestinian Territories" of Android's own list, and Jerusalem shows as Al-Quds, with no country.
MOVED = {  # (name, country): (country, region)
    ("Ariel", "IL"): ("PS", "West Bank"),  # Settlements in the West Bank, which GeoNames has under Israel
    ("Modiin Ilit", "IL"): ("PS", "West Bank"),
    ("Katzrin", "IL"): ("SY", "Quneitra"),  # In the Golan, as Majdal Shams is
    ("Smara", "MA"): ("EH", ""),  # In Western Sahara, as Laayoune and Dakhla are
    ("Gueltat Zemmour", "MA"): ("EH", ""),
    ("Aousserd", "MA"): ("EH", ""),
}
RENAMED = {  # (name, country): name
    ("Kyrenia", "CY"): "Girne (Kyrenia)",  # Northern Cyprus: the Turkish name its people use, then the Greek
    ("Famagusta", "CY"): "Gazimağusa (Famagusta)",
    ("Mórfou", "CY"): "Güzelyurt (Mórfou)",
    ("Lápithos", "CY"): "Lapta (Lápithos)",
    ("Léfka", "CY"): "Lefke (Léfka)",
    ("Rizokárpaso", "CY"): "Dipkarpaz (Rizokárpaso)",
    ("Égkomi", "CY"): "Tuzla (Égkomi)",
    ("Srbica", "XK"): "Skenderaj",  # Kosovo's names for these towns, which replaced the Serbian ones
    ("Glogovac", "XK"): "Drenas",
}
# GeoNames' point is in West Jerusalem. In the Old City, al-Aqsa is named Jerusalem rather than a neighbourhood.
MOVED_POINTS = {("Jerusalem", "IL"): (31.7767, 35.2342)}

# Other names, where people are likely to search in that script: across the Arab world and in Israel, where about a
# fifth of the people are Arab, across the former Soviet Union, and for every city of 300,000 or more. All of
# GeoNames' would double the file's size.
ARABIC, CYRILLIC = re.compile(r"[؀-ۿ]"), re.compile(r"[Ѐ-ӿ]")
ARABIC_COUNTRIES = set("AE BH DJ DZ EG EH IL IQ JO KM KW LB LY MA MR OM PS QA SA SD SO SY TN YE".split())
FORMER_USSR = set("AM AZ BY GE KG KZ MD RU TJ TM UA UZ".split())
BIG = 300000


def apply_rules(cities):
    """MOVED, RENAMED and MOVED_POINTS, on (population, name, country, region, latitude, longitude, alternates)"""
    matches = dict.fromkeys(list(MOVED) + list(RENAMED) + list(MOVED_POINTS), 0)
    changed = []
    for population, name, cc, region, lat, lon, alternates in cities:
        key = (name, cc)
        if key in matches:
            matches[key] += 1
            cc, region = MOVED.get(key, (cc, region))
            lat, lon = MOVED_POINTS.get(key, (lat, lon))
            name = RENAMED.get(key, name)
        changed.append((population, name, cc, region, lat, lon, alternates))
    wrong = [f"{name} ({cc}) matches {count}" for (name, cc), count in matches.items() if count != 1]
    if wrong:
        sys.exit("Each disputed-place rule must match one place: " + "; ".join(wrong))
    return changed


def other_names(population, cc, alternates):
    """The names in Arabic and Cyrillic script to search by, for a place of this size in this country"""
    names = []
    for script, countries in ((ARABIC, ARABIC_COUNTRIES), (CYRILLIC, FORMER_USSR)):
        if cc in countries or population >= BIG:
            for other in dict.fromkeys(a.strip() for a in alternates.split(",")):
                if other and script.search(other):
                    names.append(other)
    return names


def text(value):
    data = value.encode("utf-8")[:255]
    while True:  # never cut a character in half
        try:
            data.decode("utf-8")
            break
        except UnicodeDecodeError:
            data = data[:-1]
    return struct.pack(">B", len(data)) + data


def encode(regions, cities, others):
    """regions as (country, name); cities as (latitude, longitude, region index, name), in 1/10000 degrees;
    others as (city index, name), in city order"""
    out = bytearray(b"ADHC") + struct.pack(">B", 2)
    out += struct.pack(">H", len(regions))
    for cc, name in regions:
        out += cc.encode("ascii") + text(name)
    out += struct.pack(">i", len(cities))
    for lat, lon, region, name in cities:
        out += struct.pack(">i", lat)[1:] + struct.pack(">i", lon)[1:] + struct.pack(">H", region) + text(name)
    out += struct.pack(">i", len(others))
    for index, other in others:
        out += struct.pack(">i", index)[1:] + text(other)
    return bytes(out)


def main(cities_path, regions_path, out_path):
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
            region = region_names.get(f"{col[8]}.{col[10]}", "")  # A place without a known region gets its country's blank one
            cities.append((int(col[14] or 0), col[1], col[8], region, float(col[4]), float(col[5]), col[3]))
    cities = apply_rules(cities)
    cities.sort(key=lambda c: (-c[0], c[1]))

    # The region carries the country
    regions, region_index, rows, others = [], {}, [], []
    for index, (population, name, cc, region, lat, lon, alternates) in enumerate(cities):
        if (cc, region) not in region_index:
            region_index[(cc, region)] = len(regions)
            regions.append((cc, region))
        rows.append((round(lat * 10000), round(lon * 10000), region_index[(cc, region)], name))
        others += [(index, other) for other in other_names(population, cc, alternates)]

    out = encode(regions, rows, others)
    with open(out_path, "wb") as f:
        f.write(out)
    print(f"{len(cities)} places, {len(regions)} regions, {len(others)} other names, {len(out)} bytes")


if __name__ == "__main__":
    main(*sys.argv[1:4])
