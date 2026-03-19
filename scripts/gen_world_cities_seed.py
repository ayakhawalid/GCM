# -*- coding: utf-8 -*-
"""Generate SQL fragment for dummy_db.sql: 20 cities, 2 maps x 5 POIs, 1 mixed tour each."""
from __future__ import annotations

from pathlib import Path

CITIES = [
    ("New York City, USA", "Major US metropolis - finance, culture, and iconic landmarks.", 250.0),
    ("London, UK", "Historic capital - royalty, museums, and the Thames.", 220.0),
    ("Paris, France", "Art, cuisine, and world-famous monuments.", 230.0),
    ("Berlin, Germany", "History, museums, and vibrant neighborhoods.", 210.0),
    ("Tokyo, Japan", "Ultra-modern city with deep traditional roots.", 280.0),
    ("Sydney, Australia", "Harbour city - beaches, icons, and outdoor life.", 240.0),
    ("Rome, Italy", "The Eternal City - ancient ruins and Baroque splendor.", 205.0),
    ("Moscow, Russia", "Capital of Russia - Kremlin, squares, and culture.", 215.0),
    ("Cairo, Egypt", "Megacity gateway to ancient Egyptian heritage.", 195.0),
    ("Sao Paulo, Brazil", "Brazil's largest city - business, culture, and parks.", 200.0),
    ("Los Angeles, USA", "Entertainment capital - coast, hills, and studios.", 245.0),
    ("Beijing, China", "Imperial heritage and modern Olympic legacy.", 225.0),
    ("Mumbai, India", "Coastal economic hub - colonial-era icons and Bollywood.", 210.0),
    ("Istanbul, Turkiye", "Two continents - mosques, bazaars, and the Bosphorus.", 225.0),
    ("Mexico City, Mexico", "High-altitude capital - Aztec roots and vibrant plazas.", 200.0),
    ("Toronto, Canada", "Canada's largest city - Lake Ontario waterfront and diversity.", 215.0),
    ("Johannesburg, South Africa", "Economic heart of South Africa - history and urban energy.", 190.0),
    ("Bangkok, Thailand", "Temples, markets, and legendary street life.", 205.0),
    ("Singapore", "City-state - futuristic skyline and lush gardens.", 260.0),
    ("Dubai, UAE", "Desert metropolis - iconic towers and luxury coastline.", 270.0),
]

# 10 (name, location, category, explanation, accessible) per city — user-supplied coordinates
POIS = [
    [
        ("Times Square", "40.7580,-73.9855", "Entertainment", "Neon heart of Midtown.", True),
        ("Central Park", "40.7829,-73.9654", "Park", "Vast urban park and recreation.", True),
        ("Statue of Liberty", "40.6892,-74.0445", "Landmark", "Icon of freedom in NY Harbor.", True),
        ("Empire State Building", "40.7484,-73.9857", "Landmark", "Art deco observation classic.", True),
        ("Brooklyn Bridge", "40.7061,-73.9969", "Landmark", "Historic suspension bridge.", True),
        ("Rockefeller Center", "40.7587,-73.9787", "Landmark", "Midtown plaza and ice rink.", True),
        ("Grand Central Terminal", "40.7527,-73.9772", "Transport", "Beaux-arts rail landmark.", True),
        ("One World Trade Center", "40.7127,-74.0134", "Landmark", "Downtown skyline anchor.", True),
        ("Times Square-42 St Subway Station", "40.7553,-73.9870", "Transport", "Major subway interchange.", True),
        ("JFK International Airport", "40.6413,-73.7781", "Transport", "Main international air gateway.", True),
    ],
    [
        ("Big Ben", "51.5007,-0.1246", "Landmark", "Clock tower at Parliament.", True),
        ("London Eye", "51.5033,-0.1195", "Entertainment", "Thames observation wheel.", True),
        ("Tower of London", "51.5081,-0.0759", "Historic", "Crown jewels fortress.", True),
        ("Tower Bridge", "51.5055,-0.0754", "Landmark", "Victorian bascule bridge.", True),
        ("Buckingham Palace", "51.5014,-0.1419", "Royal", "Monarch's London residence.", True),
        ("Trafalgar Square", "51.5080,-0.1281", "Plaza", "National Gallery steps plaza.", True),
        ("St Paul's Cathedral", "51.5138,-0.0984", "Religious", "Domed Anglican cathedral.", True),
        ("King's Cross Station", "51.5308,-0.1238", "Transport", "Major rail head.", True),
        ("Hyde Park", "51.5073,-0.1657", "Park", "Royal park central London.", True),
        ("Heathrow Airport", "51.4700,-0.4543", "Transport", "Primary London airport.", True),
    ],
    [
        ("Eiffel Tower", "48.8584,2.2945", "Landmark", "Iron lattice tower.", True),
        ("Louvre Museum", "48.8606,2.3376", "Museum", "World-class art museum.", True),
        ("Notre-Dame Cathedral", "48.8530,2.3499", "Religious", "Medieval cathedral.", True),
        ("Arc de Triomphe", "48.8738,2.2950", "Landmark", "Triumphal arch.", True),
        ("Sacré-Cœur Basilica", "48.8867,2.3431", "Religious", "Hilltop basilica Montmartre.", True),
        ("Gare du Nord", "48.8809,2.3553", "Transport", "Busy rail station.", True),
        ("Jardin du Luxembourg", "48.8462,2.3372", "Park", "Formal palace gardens.", True),
        ("Place de la Concorde", "48.8656,2.3211", "Plaza", "Major public square.", True),
        ("Champs-Elysees (central)", "48.8698,2.3073", "Street", "Grand avenue.", True),
        ("Charles de Gaulle Airport", "49.0097,2.5479", "Transport", "Main Paris airport.", True),
    ],
    [
        ("Brandenburg Gate", "52.5163,13.3777", "Landmark", "Neoclassical city gate.", True),
        ("Reichstag Building", "52.5186,13.3762", "Government", "Historic parliament dome.", True),
        ("Berlin TV Tower (Fernsehturm)", "52.5208,13.4094", "Landmark", "Alexanderplatz tower views.", True),
        ("Berlin Central Station (Hbf)", "52.5251,13.3694", "Transport", "Main railway hub.", True),
        ("Checkpoint Charlie", "52.5076,13.3904", "Historic", "Cold War crossing point.", True),
        ("East Side Gallery", "52.5050,13.4394", "Historic", "Murals on Berlin Wall remnant.", True),
        ("Alexanderplatz", "52.5219,13.4132", "Plaza", "Central square east Berlin.", True),
        ("Museum Island (center)", "52.5169,13.4010", "Museum", "UNESCO museum ensemble.", True),
        ("Tempelhofer Feld", "52.4730,13.4020", "Park", "Former airfield public park.", True),
        ("Berlin Tegel (former airport)", "52.5597,13.2877", "Transport", "Reference point former TXL.", True),
    ],
    [
        ("Tokyo Station", "35.6812,139.7671", "Transport", "Brick-front rail terminus.", True),
        ("Shibuya Crossing", "35.6595,139.7005", "Landmark", "Famous scramble crossing.", True),
        ("Tokyo Skytree", "35.7100,139.8107", "Landmark", "Tallest tower Japan.", True),
        ("Senso-ji Temple", "35.7148,139.7967", "Religious", "Asakusa Buddhist temple.", True),
        ("Tokyo Tower", "35.6586,139.7454", "Landmark", "Orange lattice tower.", True),
        ("Meiji Jingu Shrine", "35.6764,139.6993", "Religious", "Forest Shinto shrine.", True),
        ("Ueno Park (center)", "35.7156,139.7745", "Park", "Museums and cherry trees.", True),
        ("Shinjuku Gyoen", "35.6852,139.7100", "Park", "Imperial garden oasis.", True),
        ("Roppongi Hills", "35.6605,139.7293", "Entertainment", "Mori tower district.", True),
        ("Haneda Airport", "35.5494,139.7798", "Transport", "Major Tokyo airport.", True),
    ],
    [
        ("Sydney Opera House", "33.8568,151.2153", "Landmark", "Sails on the harbour.", True),
        ("Sydney Harbour Bridge", "33.8523,151.2108", "Landmark", "Coathanger steel arch.", True),
        ("Circular Quay", "33.8611,151.2109", "Transport", "Ferries and harbour front.", True),
        ("Bondi Beach", "33.8915,151.2767", "Beach", "Famous surf beach.", True),
        ("Darling Harbour", "33.8728,151.2006", "Entertainment", "Waterfront dining precinct.", True),
        ("Royal Botanic Garden (center)", "33.8642,151.2166", "Park", "Harbour-side gardens.", True),
        ("The Rocks", "33.8599,151.2070", "Historic", "Colonial laneways.", True),
        ("Central Station", "33.8830,151.2065", "Transport", "Main rail station.", True),
        ("Taronga Zoo Wharf", "33.8457,151.2413", "Transport", "Ferry to zoo.", True),
        ("Sydney Airport", "33.9399,151.1753", "Transport", "Kingsford Smith airport.", True),
    ],
    [
        ("Colosseum", "41.8902,12.4922", "Historic", "Ancient amphitheatre.", True),
        ("Roman Forum (center)", "41.8925,12.4853", "Historic", "Heart of ancient Rome.", True),
        ("Trevi Fountain", "41.9009,12.4833", "Landmark", "Baroque wishing fountain.", True),
        ("Pantheon", "41.8986,12.4769", "Historic", "Domed Roman temple.", True),
        ("St Peter's Basilica", "41.9022,12.4539", "Religious", "Vatican major basilica.", True),
        ("Piazza Navona", "41.8992,12.4731", "Plaza", "Baroque square fountains.", True),
        ("Termini Station", "41.9010,12.5018", "Transport", "Central rail hub.", True),
        ("Spanish Steps", "41.9059,12.4823", "Landmark", "Trinita dei Monti stairs.", True),
        ("Circus Maximus", "41.8861,12.4852", "Historic", "Ancient chariot circuit.", True),
        ("Ciampino Airport", "41.7999,12.5949", "Transport", "Secondary Rome airport.", True),
    ],
    [
        ("Red Square (center)", "55.7539,37.6208", "Plaza", "Historic central square.", True),
        ("Kremlin (Spasskaya Tower)", "55.7525,37.6231", "Historic", "Fortified complex.", True),
        ("St Basil's Cathedral", "55.7525,37.6230", "Religious", "Colorful onion domes.", True),
        ("Bolshoi Theatre", "55.7601,37.6186", "Cultural", "Opera and ballet house.", True),
        ("GUM Department Store", "55.7549,37.6216", "Shopping", "Arcaded retail facing square.", True),
        ("Moscow State University (main)", "55.7033,37.5301", "Education", "Stalinist skyscraper campus.", True),
        ("Gorky Park", "55.7299,37.6036", "Park", "Riverside recreation.", True),
        ("Kievsky Railway Station", "55.7431,37.5650", "Transport", "Rail terminus.", True),
        ("VDNKh (main entrance)", "55.8298,37.6339", "Entertainment", "Exhibition park gates.", True),
        ("Sheremetyevo Airport", "55.9726,37.4146", "Transport", "Major Moscow airport.", True),
    ],
    [
        ("Tahrir Square", "30.0444,31.2357", "Plaza", "Downtown focal square.", True),
        ("Egyptian Museum", "30.0460,31.2336", "Museum", "Antiquities collection.", True),
        ("Cairo Citadel", "30.0299,31.2617", "Historic", "Medieval fortifications.", True),
        ("Khan el-Khalili Bazaar", "30.0478,31.2625", "Market", "Historic souk.", True),
        ("Al-Azhar Mosque", "30.0477,31.2620", "Religious", "Fatimid mosque.", True),
        ("Giza Pyramids (Great Pyramid)", "29.9792,31.1342", "Historic", "Ancient wonder plateau.", True),
        ("Cairo Tower", "30.0459,31.2243", "Landmark", "Nile-side tower views.", True),
        ("Ramses Railway Station", "30.0635,31.2461", "Transport", "Central rail.", True),
        ("Al-Azhar Park (center)", "30.0429,31.2685", "Park", "Hilltop green space.", True),
        ("Cairo International Airport", "30.1219,31.4056", "Transport", "Main airport.", True),
    ],
    [
        ("Paulista Avenue (MASP)", "23.5614,-46.6559", "Street", "Museum and skyline.", True),
        ("Ibirapuera Park (center)", "23.5874,-46.6576", "Park", "Major city park.", True),
        ("Sao Paulo Cathedral (Se)", "23.5503,-46.6342", "Religious", "Neo-Gothic cathedral.", True),
        ("Luz Station", "23.5363,-46.6339", "Transport", "Historic rail station.", True),
        ("Municipal Market", "23.5411,-46.6296", "Market", "Gourmet produce hall.", True),
        ("Pinacoteca do Estado", "23.5342,-46.6339", "Museum", "Art museum Luz.", True),
        ("Allianz Parque Stadium", "23.5276,-46.6784", "Entertainment", "Football arena.", True),
        ("Morumbi Stadium", "23.5990,-46.7208", "Entertainment", "Large football venue.", True),
        ("Congonhas Airport", "23.6261,-46.6566", "Transport", "Domestic airport.", True),
        ("Tiete Bus Terminal", "23.5185,-46.6250", "Transport", "Major bus hub.", True),
    ],
    [
        ("Hollywood Sign", "34.1341,-118.3215", "Landmark", "Hillside cinema icon.", True),
        ("Griffith Observatory", "34.1184,-118.3004", "Museum", "Planetarium and city views.", True),
        ("Santa Monica Pier", "34.0100,-118.4963", "Entertainment", "Pacific Park pier.", True),
        ("Hollywood Walk of Fame", "34.1019,-118.3269", "Entertainment", "Terrazzo star plaques.", True),
        ("LAX Airport", "33.9416,-118.4085", "Transport", "Main LA airport.", True),
        ("Walt Disney Concert Hall", "34.0553,-118.2498", "Cultural", "Gehry concert hall.", True),
        ("The Getty Center", "34.0780,-118.4741", "Museum", "Hilltop art campus.", True),
        ("Dodger Stadium", "34.0739,-118.2400", "Entertainment", "Historic baseball ballpark.", True),
        ("Crypto.com Arena", "34.0430,-118.2673", "Entertainment", "Downtown sports venue.", True),
        ("Union Station", "34.0561,-118.2365", "Transport", "Mission revival terminal.", True),
    ],
    [
        ("Tiananmen Square (center)", "39.9055,116.3976", "Plaza", "Vast central square.", True),
        ("Forbidden City (Meridian Gate)", "39.9163,116.3972", "Historic", "Imperial palace south gate.", True),
        ("Temple of Heaven", "39.8822,116.4065", "Historic", "Ming ritual complex.", True),
        ("Summer Palace (Longevity Hill)", "39.9996,116.2755", "Historic", "Lake palace gardens.", True),
        ("Beijing National Stadium", "39.9917,116.3907", "Landmark", "Bird's Nest Olympic stadium.", True),
        ("National Aquatics Center", "39.9929,116.3960", "Landmark", "Water Cube.", True),
        ("Wangfujing Street (center)", "39.9143,116.4110", "Shopping", "Pedestrian retail strip.", True),
        ("Beijing South Railway Station", "39.8650,116.3789", "Transport", "HSR terminus.", True),
        ("Beijing Capital Airport", "40.0799,116.6031", "Transport", "PEK international.", True),
        ("798 Art District", "39.9841,116.4970", "Cultural", "Factory gallery zone.", True),
    ],
    [
        ("Chhatrapati Shivaji Terminus", "18.9402,72.8353", "Historic", "UNESCO Victoria Terminus.", True),
        ("Gateway of India", "18.9220,72.8347", "Landmark", "Harbour triumphal arch.", True),
        ("Marine Drive", "18.9430,72.8238", "Street", "Queen's Necklace promenade.", True),
        ("Siddhivinayak Temple", "19.0169,72.8305", "Religious", "Ganesha temple.", True),
        ("Haji Ali Dargah", "18.9823,72.8087", "Religious", "Coastal mosque tomb.", True),
        ("Bandra-Worli Sea Link (mid)", "19.0311,72.8074", "Landmark", "Cable-stay bridge.", True),
        ("Juhu Beach (center)", "19.1024,72.8265", "Beach", "Popular western beach.", True),
        ("CSMIA Airport (T2)", "19.0961,72.8747", "Transport", "Main Mumbai airport.", True),
        ("Film City (Goregaon)", "19.1643,72.8798", "Entertainment", "Studio complex.", True),
        ("Sanjay Gandhi National Park", "19.2169,72.9106", "Park", "Northern forest reserve.", True),
    ],
    [
        ("Hagia Sophia", "41.0086,28.9802", "Historic", "Great mosque museum.", True),
        ("Blue Mosque", "41.0054,28.9768", "Religious", "Sultan Ahmed Mosque.", True),
        ("Topkapi Palace (main gate)", "41.0115,28.9833", "Historic", "Ottoman palace.", True),
        ("Galata Tower", "41.0257,28.9744", "Landmark", "Genoese tower.", True),
        ("Taksim Square", "41.0369,28.9850", "Plaza", "Modern city hub.", True),
        ("Grand Bazaar", "41.0106,28.9680", "Market", "Historic covered market.", True),
        ("Spice Bazaar", "41.0164,28.9702", "Market", "Egyptian Bazaar.", True),
        ("Dolmabahçe Palace", "41.0390,29.0005", "Historic", "Bosphorus palace.", True),
        ("15 July Martyrs Bridge (mid)", "41.0450,29.0280", "Landmark", "Bosphorus bridge.", True),
        ("Istanbul Airport", "41.2621,28.7426", "Transport", "IST mega-hub.", True),
    ],
    [
        ("Zocalo (Plaza de la Constitucion)", "19.4326,-99.1332", "Plaza", "Main square CDMX.", True),
        ("Metropolitan Cathedral", "19.4341,-99.1339", "Religious", "Central cathedral.", True),
        ("Palacio de Bellas Artes", "19.4352,-99.1412", "Cultural", "Art nouveau theatre.", True),
        ("Ángel de la Independencia", "19.4270,-99.1677", "Landmark", "Reform column.", True),
        ("Chapultepec Castle", "19.4204,-99.1819", "Historic", "Hilltop museum.", True),
        ("Museo Nacional de Antropología", "19.4250,-99.1860", "Museum", "Anthropology flagship.", True),
        ("Basilica of Guadalupe", "19.4840,-99.1180", "Religious", "Major pilgrimage site.", True),
        ("Frida Kahlo Museum", "19.3553,-99.1628", "Museum", "Casa Azul Coyoacán.", True),
        ("Estadio Azteca", "19.3029,-99.1505", "Entertainment", "Legendary stadium.", True),
        ("Mexico City Airport", "19.4361,-99.0719", "Transport", "AICM terminal area.", True),
    ],
    [
        ("CN Tower", "43.6426,-79.3871", "Landmark", "Communications tower.", True),
        ("Rogers Centre", "43.6414,-79.3894", "Entertainment", "Retractable roof stadium.", True),
        ("Union Station", "43.6456,-79.3807", "Transport", "Historic rail hub.", True),
        ("Nathan Phillips Square", "43.6525,-79.3839", "Plaza", "City hall forecourt.", True),
        ("Royal Ontario Museum", "43.6677,-79.3948", "Museum", "ROM crystal wing.", True),
        ("Art Gallery of Ontario", "43.6536,-79.3925", "Museum", "AGO downtown.", True),
        ("Yonge-Dundas Square", "43.6561,-79.3802", "Plaza", "Neon intersection.", True),
        ("High Park (center)", "43.6465,-79.4637", "Park", "Large west-end park.", True),
        ("Billy Bishop Airport", "43.6287,-79.3962", "Transport", "Island city airport.", True),
        ("Toronto Zoo", "43.8177,-79.1859", "Entertainment", "Scarborough wildlife park.", True),
    ],
    [
        ("Nelson Mandela Square", "26.1076,28.0567", "Plaza", "Sandton public square.", True),
        ("Apartheid Museum", "26.2379,28.0100", "Museum", "Apartheid history.", True),
        ("Constitution Hill", "26.1897,28.0422", "Historic", "Old fort precinct.", True),
        ("Johannesburg Zoo", "26.1757,28.0374", "Entertainment", "Urban zoo.", True),
        ("Carlton Centre", "26.2045,28.0467", "Landmark", "Downtown skyscraper.", True),
        ("Gold Reef City", "26.2371,28.0124", "Entertainment", "Mine-themed park.", True),
        ("FNB Stadium", "26.2348,27.9826", "Entertainment", "Soccer City arena.", True),
        ("OR Tambo Airport", "26.1337,28.2420", "Transport", "Main airport.", True),
        ("Wits University (main)", "26.1880,28.0265", "Education", "Braamfontein campus.", True),
        ("Maboneng Precinct", "26.2033,28.0617", "Entertainment", "Arts district.", True),
    ],
    [
        ("Grand Palace", "13.7500,100.4913", "Historic", "Royal compound.", True),
        ("Wat Pho", "13.7466,100.4930", "Religious", "Reclining Buddha.", True),
        ("Wat Arun", "13.7437,100.4889", "Religious", "Temple of Dawn.", True),
        ("Khao San Road (center)", "13.7597,100.4977", "Entertainment", "Backpacker strip.", True),
        ("MBK Center", "13.7446,100.5296", "Shopping", "MBK mall.", True),
        ("Lumphini Park (center)", "13.7300,100.5410", "Park", "Central green lung.", True),
        ("Chatuchak Weekend Market", "13.7996,100.5530", "Market", "Vast weekend bazaar.", True),
        ("Victory Monument", "13.7629,100.5383", "Landmark", "Traffic circle obelisk.", True),
        ("Suvarnabhumi Airport", "13.6900,100.7501", "Transport", "BKK international.", True),
        ("Don Mueang Airport", "13.9126,100.6069", "Transport", "Secondary airport.", True),
    ],
    [
        ("Marina Bay Sands", "1.2834,103.8607", "Landmark", "Sky park resort.", True),
        ("Merlion Park", "1.2868,103.8545", "Landmark", "Symbol fountain.", True),
        ("Gardens by the Bay", "1.2816,103.8636", "Park", "Supertree Grove.", True),
        ("Orchard Road (central)", "1.3040,103.8318", "Shopping", "Retail boulevard.", True),
        ("Singapore Botanic Gardens", "1.3138,103.8159", "Park", "UNESCO gardens.", True),
        ("Singapore Zoo", "1.4043,103.7930", "Entertainment", "Wildlife park.", True),
        ("Sentosa (Resorts World)", "1.2565,103.8219", "Entertainment", "Island resort.", True),
        ("Clarke Quay", "1.2915,103.8463", "Entertainment", "Riverside nightlife.", True),
        ("Changi Airport (T3)", "1.3571,103.9886", "Transport", "Major hub.", True),
        ("Esplanade Theatres", "1.2894,103.8550", "Cultural", "Durian-shaped arts centre.", True),
    ],
    [
        ("Burj Khalifa", "25.1972,55.2744", "Landmark", "World's tallest tower.", True),
        ("Dubai Mall", "25.1985,55.2796", "Shopping", "Mega retail mall.", True),
        ("Dubai Fountain", "25.1965,55.2759", "Entertainment", "Choreographed water show.", True),
        ("Burj Al Arab", "25.1412,55.1853", "Landmark", "Sail-shaped hotel.", True),
        ("Palm Jumeirah (Atlantis)", "25.1311,55.1178", "Entertainment", "Palm resort tip.", True),
        ("Dubai Marina (center)", "25.0801,55.1402", "Neighborhood", "High-rise waterfront.", True),
        ("Mall of the Emirates", "25.1180,55.2007", "Shopping", "Ski Dubai mall.", True),
        ("Dubai Creek (Al Seef)", "25.2584,55.3045", "Historic", "Heritage waterfront.", True),
        ("Dubai International Airport", "25.2532,55.3657", "Transport", "DXB hubs.", True),
        ("Dubai Frame", "25.2371,55.3004", "Landmark", "Golden observation frame.", True),
    ],
]


def esc(s: str) -> str:
    return s.replace("\\", "\\\\").replace("'", "''")


def main() -> None:
    assert len(CITIES) == 20 and len(POIS) == 20
    for i, pl in enumerate(POIS):
        assert len(pl) == 10, i

    lines: list[str] = []
    lines.append("-- ============================================================")
    lines.append("-- SEED DATA: 20 world cities, 2 maps x 5 POIs each, 1 tour mixing both maps")
    lines.append("-- ============================================================")
    lines.append("")

    # Cities (explicit ids 1..20)
    lines.append("INSERT INTO cities (id, name, description, price, approved, created_by) VALUES")
    cv = []
    for i, (name, desc, price) in enumerate(CITIES, start=1):
        cv.append(f"    ({i}, '{esc(name)}', '{esc(desc)}', {price}, 1, NULL)")
    lines.append(",\n".join(cv) + ";")
    lines.append("")
    lines.append("-- Reset AUTO_INCREMENT after explicit city ids")
    lines.append("ALTER TABLE cities AUTO_INCREMENT = 21;")
    lines.append("")

    # Tours first (need tour_id for maps FK): ids 1..20, city_id matches
    lines.append("-- One tour per city (tour id = city id)")
    lines.append("INSERT INTO tours (id, city_id, name, general_description, total_distance_meters) VALUES")
    tv = []
    for cid, (cname, _, _) in enumerate(CITIES, start=1):
        tn = esc(f"{cname.split(',')[0]} Highlights Tour")
        td = esc(
            f"Mixed route across both maps - landmarks, transit, and greenspace in {cname.split(',')[0]}."
        )
        tv.append(f"    ({cid}, {cid}, '{tn}', '{td}', NULL)")
    lines.append(",\n".join(tv) + ";")
    lines.append("ALTER TABLE tours AUTO_INCREMENT = 21;")
    lines.append("")

    # Maps: ids 1..40, city c has maps 2c-1 and 2c; map 2c-1 links to tour c
    lines.append("-- Two maps per city; first map of city is dedicated tour route map (tour_id)")
    lines.append("INSERT INTO maps (id, city_id, name, short_description, approved, created_by, tour_id) VALUES")
    mv = []
    map_id = 1
    for cid, (cname, _, _) in enumerate(CITIES, start=1):
        short = cname.split(",")[0].strip()
        m1 = esc(f"{short} - Landmarks & Core (Map A)")
        m2 = esc(f"{short} - Transit, Parks & More (Map B)")
        d1 = esc(f"Five major POIs: first half of {short} curated set.")
        d2 = esc(f"Five more POIs: second half of {short} curated set.")
        mv.append(f"    ({map_id}, {cid}, '{m1}', '{d1}', 1, NULL, {cid})")
        map_id += 1
        mv.append(f"    ({map_id}, {cid}, '{m2}', '{d2}', 1, NULL, NULL)")
        map_id += 1
    lines.append(",\n".join(mv) + ";")
    lines.append("ALTER TABLE maps AUTO_INCREMENT = 41;")
    lines.append("")

    # POIs: ids 1..200
    lines.append("INSERT INTO pois (id, city_id, name, location, category, short_explanation, is_accessible) VALUES")
    pv = []
    poi_id = 1
    for cid, plist in enumerate(POIS, start=1):
        for name, loc, cat, expl, acc in plist:
            a = "TRUE" if acc else "FALSE"
            pv.append(
                f"    ({poi_id}, {cid}, '{esc(name)}', '{loc}', '{esc(cat)}', '{esc(expl)}', {a})"
            )
            poi_id += 1
    lines.append(",\n".join(pv) + ";")
    lines.append("ALTER TABLE pois AUTO_INCREMENT = 201;")
    lines.append("")

    # lat/lng backfill from location
    lines.append("SET SQL_SAFE_UPDATES = 0;")
    lines.append(
        "UPDATE pois\n"
        "SET latitude = CAST(TRIM(SUBSTRING_INDEX(location, ',', 1)) AS DECIMAL(10,6)),\n"
        "    longitude = CAST(TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(location, ',', 2), ',', -1)) AS DECIMAL(10,6))\n"
        "WHERE location REGEXP '^-?[0-9]+[.]?[0-9]*,[ ]*-?[0-9]+[.]?[0-9]*$';"
    )
    lines.append("SET SQL_SAFE_UPDATES = 1;")
    lines.append("")

    # map_pois: map (2c-1) -> pois base+0..base+4; map (2c) -> base+5..base+9
    lines.append("-- Map A: POIs 1-5 per city; Map B: POIs 6-10")
    lines.append("INSERT INTO map_pois (map_id, poi_id, display_order, approved, linked_by_user_id) VALUES")
    mpv = []
    for cid in range(1, 21):
        base = 10 * (cid - 1)
        m_a = 2 * cid - 1
        m_b = 2 * cid
        for k in range(5):
            mpv.append(f"    ({m_a}, {base + k + 1}, {k + 1}, 1, NULL)")
        for k in range(5):
            mpv.append(f"    ({m_b}, {base + k + 6}, {k + 1}, 1, NULL)")
    lines.append(",\n".join(mpv) + ";")
    lines.append("")

    # Tour stops: interleave map A and B POIs: 1,6,2,7,3,8,4,9,5,10 per city
    lines.append(
        "-- Tour order mixes both maps: POI 1,6,2,7,3,8,4,9,5,10 (randomized-style interleaving)"
    )
    lines.append("INSERT INTO tour_stops (tour_id, poi_id, stop_order, notes) VALUES")
    tsv = []
    pattern = [0, 5, 1, 6, 2, 7, 3, 8, 4, 9]
    for cid in range(1, 21):
        base = 10 * (cid - 1)
        for order, off in enumerate(pattern, start=1):
            pid = base + off + 1
            tsv.append(f"    ({cid}, {pid}, {order}, 'Stop {order} - mixed map sequence')")
    lines.append(",\n".join(tsv) + ";")
    lines.append("")

    out = Path(__file__).resolve().parents[1] / "target" / "world_seed_generated.sql"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text("\n".join(lines) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
