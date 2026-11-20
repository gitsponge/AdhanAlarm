package islam.adhanalarm.handler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import islam.adhanalarm.Locations;

public class CityDatabaseTest {

    private static CityDatabase sDatabase;

    @BeforeClass
    public static void load() throws Exception {
        try (InputStream in = new FileInputStream("src/main/assets/cities.bin")) {
            sDatabase = CityDatabase.read(in);
        }
    }

    @Test
    public void holdsTheWholeList() {
        assertTrue(sDatabase.size() > 60000);
    }

    @Test
    public void namesTheNearestCity() {
        CityDatabase.City city = sDatabase.nearest(51.5074, -0.1278);
        assertEquals("London", city.name);
        assertEquals("England", city.region);
        assertEquals("GB", city.country);
        assertTrue(city.distanceKm < 5);
    }

    @Test
    public void farFromAnyCityItSaysSo() {
        assertTrue(sDatabase.nearest(30, -40).distanceKm > CityDatabase.NEAR_KM); // The middle of the Atlantic
    }

    @Test
    public void searchFindsPlacesBiggestFirst() {
        List<CityDatabase.City> results = sDatabase.search("londo", 10);
        assertEquals("London", results.get(0).name);
        assertEquals("GB", results.get(0).country);
    }

    @Test
    public void searchIgnoresAccentsAndKnowsOtherSpellings() {
        assertEquals("Sant Julià de Lòria", sDatabase.search("sant julia", 5).get(0).name);
        assertEquals("Makkah", sDatabase.search("Mecca", 5).get(0).name);
        assertEquals("Makkah", sDatabase.search("makka", 5).get(0).name);
        assertEquals("Madinah", sDatabase.search("Medina", 5).get(0).name);
        assertEquals("Jerusalem", sDatabase.search("Al-Quds", 5).get(0).name);
    }

    @Test
    public void quickNormalizingMatchesTheFullOne() {
        int checked = 0;
        for (int i = 0; i < sDatabase.size(); i++) {
            String name = sDatabase.getName(i);
            if (name.chars().allMatch(c -> c < 128)) {
                assertEquals(CityDatabase.normalizeFully(name), CityDatabase.normalize(name));
                checked++;
            }
        }
        assertTrue(checked > 40000);
    }

    @Test
    public void searchWorksInArabicAndRussian() {
        assertEquals("Riyadh", sDatabase.search("الرياض", 5).get(0).name);
        assertEquals("Riyadh", sDatabase.search("رياض", 5).get(0).name); // Without al-
        assertEquals("Cairo", sDatabase.search("القاهرة", 5).get(0).name);
        assertEquals("Cairo", sDatabase.search("القاهره", 5).get(0).name); // ة typed as ه
        assertEquals("Makkah", sDatabase.search("مكة", 5).get(0).name);
        assertEquals("London", sDatabase.search("لندن", 5).get(0).name);
        assertEquals("Moscow", sDatabase.search("Москва", 5).get(0).name);
        assertEquals("Saint Petersburg", sDatabase.search("санкт", 5).get(0).name);
        assertEquals("Kazan", sDatabase.search("Казань", 5).get(0).name.replace("’", ""));
        assertTrue(sDatabase.search("ال", 5).isEmpty()); // Too short to search for yet
        assertTrue(!CityDatabase.isSearchable("ال") && !CityDatabase.isSearchable("الر") && CityDatabase.isSearchable("الري")); // Two letters after al-
    }

    @Test
    public void disputedPlacesFollowTheUnsLines() {
        // GeoNames' few exceptions, brought into line by tools/make_cities.py
        assertPlace("Ariel", "West Bank", "PS");
        assertPlace("Modiin Ilit", "West Bank", "PS");
        assertPlace("Katzrin", "Quneitra", "SY");
        assertPlace("Smara", "", "EH");
        assertPlace("Gueltat Zemmour", "", "EH");
        assertPlace("Aousserd", "", "EH");
        assertPlace("Girne (Kyrenia)", "Keryneia", "CY");
        assertPlace("Gazimağusa (Famagusta)", "Ammochostos", "CY");
        assertPlace("Skenderaj", "Mitrovica", "XK");
        assertPlace("Drenas", "Pristina", "XK");
    }

    @Test
    public void palestineIsPalestineAndJerusalemAlQuds() {
        assertEquals("West Bank, Palestine", find("Hebron").getPlace("Palestine", "Al-Quds"));
        assertEquals("Gaza Strip, Palestine", find("Gaza").getPlace("Palestine", "Al-Quds"));
        for (String name : CityDatabase.JERUSALEM) {
            assertEquals("IL", find(name).country); // As City.isInJerusalem() expects
            assertEquals("Al-Quds", find(name).getPlace("Palestine", "Al-Quds")); // No country
        }
        assertEquals("Jerusalem", sDatabase.nearest(31.7761, 35.2358).name); // At al-Aqsa
        assertEquals("Jerusalem", sDatabase.nearest(31.7767, 35.2345).name); // In the Old City
    }

    @Test
    public void searchFindsArabicNamesFormerNamesAndOtherSpellings() {
        assertFinds("al-Khalil", "Hebron");
        assertFinds("Bayt Lahm", "Bethlehem");
        assertFinds("Beit Lahm", "Bethlehem"); // Beit for Bayt
        assertFinds("Jenin", "Janīn");
        assertFinds("Khan Younis", "Khān Yūnis");
        assertFinds("Deir al-Balah", "Dayr al Balaḩ"); // Deir for Dayr
        assertFinds("Beit Hanoun", "Bayt Ḩānūn");
        assertFinds("Beit Shemesh", "Bet Shemesh");
        assertFinds("Nuseirat", "An Nuşayrāt");
        assertFinds("الناصرة", "Nazareth");
        assertFinds("عكا", "Acre");
        assertFinds("حيفا", "Haifa");
        assertFinds("أم الفحم", "Umm el Faḥm");
        assertFinds("Akka", "Acre");
        assertFinds("Jaffa", "Tel Aviv");
        assertFinds("Umm al-Fahm", "Umm el Faḥm");
        assertFinds("Mekke", "Makkah");
        assertFinds("La Mecque", "Makkah");
        assertFinds("Makkah al-Mukarramah", "Makkah");
        assertFinds("Médine", "Madinah");
        assertFinds("Baitulmaqdis", "Jerusalem");
        assertFinds("Allahabad", "Prayagraj");
        assertFinds("Bombay", "Mumbai");
        assertFinds("Kiev", "Kyiv");
        assertFinds("Srbica", "Skenderaj");
        assertFinds("Kyrenia", "Girne (Kyrenia)");
        assertFinds("Lefkoşa", "Nicosia");
    }

    @Test
    public void aliasesWaitForTheWholeName() {
        assertFalse(names(sDatabase.search("Mad", 30)).contains("Chennai")); // On the way to Madrid
        assertTrue(names(sDatabase.search("Madras", 30)).contains("Chennai"));
        assertTrue(names(sDatabase.search("Lydd", 30)).contains("Lod"));
        assertFalse(names(sDatabase.search("Lydd", 30)).contains("Lodi")); // Whole names only
    }

    @Test
    public void everyAliasIsNormalizedAndNamesAPlace() {
        String[] names = new String[sDatabase.size()];
        for (int i = 0; i < names.length; i++) names[i] = CityDatabase.normalize(sDatabase.getName(i));
        for (String[] alias : CityDatabase.ALIASES) {
            assertEquals(alias[0], CityDatabase.normalize(alias[0]));
            boolean found = false;
            for (int i = 0; i < names.length && !found; i++) found = CityDatabase.hasWords(names[i], alias[1]);
            assertTrue(alias[1], found);
        }
    }

    private static CityDatabase.City find(String name) {
        for (CityDatabase.City city : sDatabase.search(name, 50)) {
            if (city.name.equals(name)) return city;
        }
        throw new AssertionError(name + " isn't in the list");
    }

    private static void assertPlace(String name, String region, String country) {
        CityDatabase.City city = find(name);
        assertEquals(name, region, city.region);
        assertEquals(name, country, city.country);
    }

    private static void assertFinds(String query, String name) {
        assertTrue(query + " should find " + name, names(sDatabase.search(query, 30)).contains(name));
    }

    private static List<String> names(List<CityDatabase.City> cities) {
        List<String> names = new ArrayList<>();
        for (CityDatabase.City city : cities) names.add(city.name);
        return names;
    }

    @Test
    public void qiblaBearings() {
        assertEquals(119.0, Locations.qiblaBearing(51.5074, -0.1278), 0.5); // London: east-south-east
        assertEquals(54.6, Locations.qiblaBearing(43.67, -79.417), 1); // Toronto: north-east
        assertEquals(295.2, Locations.qiblaBearing(-6.2, 106.85), 1); // Jakarta: west-north-west
    }
}
