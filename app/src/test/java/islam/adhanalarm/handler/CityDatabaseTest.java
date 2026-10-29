package islam.adhanalarm.handler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.FileInputStream;
import java.io.InputStream;
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
    public void qiblaBearings() {
        assertEquals(119.0, Locations.qiblaBearing(51.5074, -0.1278), 0.5); // London: east-south-east
        assertEquals(54.6, Locations.qiblaBearing(43.67, -79.417), 1); // Toronto: north-east
        assertEquals(295.2, Locations.qiblaBearing(-6.2, 106.85), 1); // Jakarta: west-north-west
    }
}
