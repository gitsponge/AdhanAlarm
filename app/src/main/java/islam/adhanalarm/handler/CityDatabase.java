package islam.adhanalarm.handler;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.core.util.Consumer;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.IllformedLocaleException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import islam.adhanalarm.R;

/**
 * The places of 5,000 people or more, from GeoNames, bundled so that naming and finding a place never
 * needs the internet. Built by tools/make_cities.py, which also changes a few disputed places.
 */
public final class CityDatabase {

    /** A city counts as where you are within this distance; further away only the coordinates are shown */
    public static final double NEAR_KM = 20;

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final AtomicInteger SEARCHES = new AtomicInteger(); // Counts searches, so an old one can stop early
    private static volatile CityDatabase sInstance;

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NOT_LETTERS = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern ARABIC_AL = Pattern.compile("(^|\\s)\u0627\u0644(?=\\p{L})");

    // Names people search for that GeoNames lists under another, whole, as normalize() leaves them
    static final String[][] ALIASES = {
            // The holy cities as the app's languages write them. GeoNames has Makkah and Madinah, but Jerusalem.
            {"mecca", "makkah"}, {"mekka", "makkah"}, {"mekke", "makkah"}, {"mekah", "makkah"}, {"mecque", "makkah"}, {"la mecque", "makkah"},
            {"makkah al", "makkah"}, {"medina", "madinah"}, {"medine", "madinah"}, {"al madinah", "madinah"}, {"madinah al", "madinah"},
            {"al quds", "jerusalem"}, {"quds", "jerusalem"}, {"bayt al maqdis", "jerusalem"}, {"baitul maqdis", "jerusalem"}, {"baitulmaqdis", "jerusalem"},
            // Palestine's towns in Arabic, and as English usually spells them
            {"al khalil", "hebron"}, {"el khalil", "hebron"}, {"bayt lahm", "bethlehem"}, {"ariha", "jericho"}, {"ghazza", "gaza"},
            {"jenin", "janin"}, {"tulkarem", "tulkarm"}, {"jabalia", "jabalya"}, {"jabaliya", "jabalya"}, {"khan younis", "khan yunis"},
            {"khan yunus", "khan yunis"}, {"dayr el balah", "dayr al balah"}, {"bayt hanoun", "bayt hanun"}, {"bayt lahia", "bayt lahya"},
            {"bayt lahiya", "bayt lahya"}, {"bayt sahour", "bayt sahur"}, {"nuseirat", "an nusayrat"}, {"bureij", "al burayj"},
            {"al bireh", "al birah"}, {"el bireh", "al birah"}, {"shuafat", "shu fat"}, {"shufat", "shu fat"}, {"sur baher", "sur bahir"},
            {"eizariya", "al ayzariyah"}, {"al eizariya", "al ayzariyah"}, {"azariya", "al ayzariyah"},
            // Towns in Israel by their Arabic names
            {"akka", "acre"}, {"nasira", "nazareth"}, {"an nasira", "nazareth"}, {"yafa", "tel aviv"}, {"jaffa", "tel aviv"}, {"lydd", "lod"},
            {"al lydd", "lod"}, {"tabariya", "tiberias"}, {"safad", "safed"}, {"bir al saba", "beersheba"}, {"bir as saba", "beersheba"},
            {"beer sheva", "beersheba"}, {"hayfa", "haifa"}, {"umm al fahm", "umm el fahm"}, {"shefa amr", "shefar am"}, {"shafa amr", "shefar am"},
            {"baqa al", "baqa el"}, {"tayibe", "taiyiba"}, {"taibe", "taiyiba"},
            // Former names: India's renamed cities, and others people still use
            {"allahabad", "prayagraj"}, {"osmanabad", "dharashiv"}, {"ahmednagar", "ahilyanagar"}, {"ahmadnagar", "ahilyanagar"},
            {"faizabad", "ayodhya"}, {"gurgaon", "gurugram"}, {"bombay", "mumbai"}, {"madras", "chennai"}, {"calcutta", "kolkata"},
            {"bangalore", "bengaluru"}, {"poona", "pune"}, {"baroda", "vadodara"}, {"benares", "varanasi"}, {"banaras", "varanasi"},
            {"cawnpore", "kanpur"}, {"trivandrum", "thiruvananthapuram"}, {"rangoon", "yangon"}, {"chittagong", "chattogram"},
            {"kiev", "kyiv"}, {"dnepropetrovsk", "dnipro"}, {"kirovograd", "kropyvnytskyi"}, {"saigon", "ho chi minh"}, {"peking", "beijing"},
            // Northern Cyprus and Kosovo, by the names GeoNames had for them (tools/make_cities.py)
            {"morphou", "guzelyurt"}, {"lefkosa", "nicosia"}, {"magusa", "gazimagusa"}, {"srbica", "skenderaj"}, {"glogovac", "drenas"}};

    // Arabic and Hebrew place names are written Beit, Bayt or Bet, and Deir, Dayr or Dair: the same word each way
    private static final String[][] SPELLINGS = {{"beit", "bayt", "bet"}, {"deir", "dayr", "dair"}};

    // Jerusalem, and a neighbourhood of it that GeoNames lists as a town of its own
    static final String[] JERUSALEM = {"Jerusalem", "Qiryat HaYovel"};

    private final String[] mNames;
    private final int[] mLatitudes, mLongitudes; // In 1/10000 degrees
    private final char[] mRegions;
    private final String[] mRegionNames, mRegionCountries;
    private final int[] mOtherNameCities; // In city order
    private final String[] mOtherNames; // In Arabic and Cyrillic script, for searching
    private String[] mSearchNames, mSearchOtherNames;

    public static final class City {
        public final String name, region, country;
        public final double latitude, longitude;
        public final double distanceKm; // From the point it was found near, or 0

        City(String name, String region, String country, double latitude, double longitude, double distanceKm) {
            this.name = name;
            this.region = region;
            this.country = country;
            this.latitude = latitude;
            this.longitude = longitude;
            this.distanceKm = distanceKm;
        }

        /** For example "England, United Kingdom", "West Bank, Palestine", or for Jerusalem "Al-Quds" */
        public String getPlace(Context context) {
            return getPlace(context.getString(R.string.palestine), context.getString(R.string.al_quds));
        }

        /**
         * Android's own list of countries calls Palestine "Palestinian Territories". Jerusalem has no country: its
         * status is unresolved, as the UN holds. tools/make_cities.py has the rest of the disputed places.
         */
        String getPlace(String palestine, String alQuds) {
            if (isInJerusalem()) return alQuds;
            String countryName;
            if ("PS".equals(country)) {
                countryName = palestine;
            } else {
                try {
                    countryName = new Locale.Builder().setRegion(country).build().getDisplayCountry();
                } catch (IllformedLocaleException ex) {
                    countryName = country;
                }
            }
            return region.isEmpty() || region.equals(countryName) ? countryName : region + ", " + countryName;
        }

        boolean isInJerusalem() {
            return ("IL".equals(country) || "PS".equals(country)) && (name.equals(JERUSALEM[0]) || name.equals(JERUSALEM[1]));
        }
    }

    private CityDatabase(String[] names, int[] latitudes, int[] longitudes, char[] regions, String[] regionNames, String[] regionCountries, int[] otherNameCities, String[] otherNames) {
        mNames = names;
        mLatitudes = latitudes;
        mLongitudes = longitudes;
        mRegions = regions;
        mRegionNames = regionNames;
        mRegionCountries = regionCountries;
        mOtherNameCities = otherNameCities;
        mOtherNames = otherNames;
    }

    /**
     * Loads the list off the main thread the first time, then hands it over on the main thread
     */
    public static void load(Context context, Consumer<CityDatabase> callback) {
        final Context appContext = context.getApplicationContext();
        final Handler main = new Handler(Looper.getMainLooper());
        EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                final CityDatabase database = get(appContext);
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.accept(database);
                    }
                });
            }
        });
    }

    /**
     * The list, loading it if needed. Not on the main thread: loading takes a moment.
     */
    public static CityDatabase get(Context context) {
        if (sInstance == null) {
            synchronized (CityDatabase.class) {
                if (sInstance == null) {
                    try (InputStream in = context.getAssets().open("cities.bin")) {
                        sInstance = read(in);
                    } catch (IOException ex) {
                        sInstance = new CityDatabase(new String[0], new int[0], new int[0], new char[0], new String[0], new String[0], new int[0], new String[0]);
                    }
                }
            }
        }
        return sInstance;
    }

    public static CityDatabase read(InputStream input) throws IOException {
        DataInputStream in = new DataInputStream(new BufferedInputStream(input, 65536));
        byte[] magic = new byte[4];
        in.readFully(magic);
        if (!"ADHC".equals(new String(magic, StandardCharsets.US_ASCII)) || in.readUnsignedByte() != 2) {
            throw new IOException("Not a city list");
        }
        int regionCount = in.readUnsignedShort();
        String[] regionNames = new String[regionCount];
        String[] regionCountries = new String[regionCount];
        for (int i = 0; i < regionCount; i++) {
            regionCountries[i] = new String(new byte[]{in.readByte(), in.readByte()}, StandardCharsets.US_ASCII);
            regionNames[i] = readText(in);
        }
        int count = in.readInt();
        String[] names = new String[count];
        int[] latitudes = new int[count];
        int[] longitudes = new int[count];
        char[] regions = new char[count];
        for (int i = 0; i < count; i++) {
            latitudes[i] = readInt24(in);
            longitudes[i] = readInt24(in);
            regions[i] = (char) in.readUnsignedShort();
            names[i] = readText(in);
        }
        int otherCount = in.readInt();
        int[] otherNameCities = new int[otherCount];
        String[] otherNames = new String[otherCount];
        for (int i = 0; i < otherCount; i++) {
            otherNameCities[i] = readInt24(in);
            otherNames[i] = readText(in);
        }
        return new CityDatabase(names, latitudes, longitudes, regions, regionNames, regionCountries, otherNameCities, otherNames);
    }

    private static int readInt24(DataInputStream in) throws IOException {
        int value = (in.readUnsignedByte() << 16) | (in.readUnsignedByte() << 8) | in.readUnsignedByte();
        return (value << 8) >> 8; // Sign extend
    }

    private static String readText(DataInputStream in) throws IOException {
        byte[] bytes = new byte[in.readUnsignedByte()];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public int size() {
        return mNames.length;
    }

    String getName(int i) {
        return mNames[i];
    }

    private City city(int i, double distanceKm) {
        return new City(mNames[i], mRegionNames[mRegions[i]], mRegionCountries[mRegions[i]], mLatitudes[i] / 10000.0, mLongitudes[i] / 10000.0, distanceKm);
    }

    /**
     * The closest place, whatever the distance, or null if the list is empty
     */
    public City nearest(double latitude, double longitude) {
        double cosLatitude = Math.cos(Math.toRadians(latitude));
        int best = -1;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < mNames.length; i++) {
            double dLatitude = mLatitudes[i] / 10000.0 - latitude;
            double dLongitude = Math.abs(mLongitudes[i] / 10000.0 - longitude);
            if (dLongitude > 180) dLongitude = 360 - dLongitude;
            dLongitude *= cosLatitude;
            double distance = dLatitude * dLatitude + dLongitude * dLongitude;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best < 0 ? null : city(best, distanceKm(latitude, longitude, mLatitudes[best] / 10000.0, mLongitudes[best] / 10000.0));
    }

    /**
     * Places whose name, or a word of it, starts with the query, biggest first
     */
    /**
     * Gets the list ready to search, off the main thread, so the first search is as quick as the rest
     */
    public static void prepare(Context context) {
        final Context appContext = context.getApplicationContext();
        EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                CityDatabase database = get(appContext);
                database.getSearchNames();
                database.getSearchOtherNames();
            }
        });
    }

    /**
     * Searches off the main thread and hands the places back on it. While someone types, each new search
     * stops the one before it, and only the latest one reports back.
     */
    public static void search(Context context, final String query, final int limit, final Consumer<List<City>> callback) {
        final Context appContext = context.getApplicationContext();
        final int search = SEARCHES.incrementAndGet();
        EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                if (search != SEARCHES.get()) return; // A newer search is already waiting
                final List<City> results = get(appContext).search(query, limit, search);
                if (results == null) return;
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        if (search == SEARCHES.get()) callback.accept(results);
                    }
                });
            }
        });
    }

    public List<City> search(String query, int limit) {
        return search(query, limit, 0);
    }

    /**
     * Places whose name, or a word of it, starts with the query, biggest first,
     * or null if a newer search took over (only searches numbered above 0 can)
     */
    private List<City> search(String query, int limit, int search) {
        List<City> results = new ArrayList<>();
        String key = normalize(query);
        if (!isSearchable(query)) return results;
        List<String> spellings = spellings(key);
        List<String> aliases = aliases(key, spellings);
        String[] searchNames = getSearchNames();
        String[] searchOtherNames = getSearchOtherNames();
        String wordKey = " " + key;
        int other = 0;
        for (int i = 0; i < searchNames.length && results.size() < limit; i++) {
            if (search != 0 && (i & 4095) == 0 && search != SEARCHES.get()) return null;
            String name = searchNames[i];
            boolean found = name.startsWith(key) || name.contains(wordKey);
            for (int s = 0; !found && s < spellings.size(); s++) {
                found = matches(name, spellings.get(s));
            }
            for (int a = 0; !found && a < aliases.size(); a++) {
                found = hasWords(name, aliases.get(a));
            }
            while (other < mOtherNameCities.length && mOtherNameCities[other] < i) other++;
            for (int o = other; !found && o < mOtherNameCities.length && mOtherNameCities[o] == i; o++) {
                found = searchOtherNames[o].startsWith(key) || searchOtherNames[o].contains(wordKey);
            }
            if (found) results.add(city(i, 0));
        }
        return results;
    }

    /**
     * Long enough to search for: two letters, not counting an Arabic name's al-
     */
    public static boolean isSearchable(String query) {
        String key = normalize(query);
        return key.length() >= 2 && !key.equals("\u0627\u0644");
    }

    private static boolean matches(String name, String key) {
        return name.startsWith(key) || name.contains(" " + key);
    }

    /** The query with Beit as Bayt and Bet, and so on (SPELLINGS) */
    static List<String> spellings(String key) {
        List<String> spellings = new ArrayList<>();
        String padded = " " + key + " ";
        for (String[] group : SPELLINGS) {
            for (String word : group) {
                if (!padded.contains(" " + word + " ")) continue;
                for (String other : group) {
                    if (!other.equals(word)) spellings.add(padded.replace(" " + word + " ", " " + other + " ").trim());
                }
            }
        }
        return spellings;
    }

    /**
     * The names GeoNames lists the query under, however it's spelled (ALIASES), once it's all but typed out: typing
     * Mad on the way to Madrid shouldn't bring up Chennai
     */
    static List<String> aliases(String key, List<String> spellings) {
        List<String> aliases = new ArrayList<>();
        List<String> typed = new ArrayList<>(spellings);
        typed.add(0, key);
        for (String spelling : typed) {
            for (String[] pair : ALIASES) {
                boolean typing = pair[0].startsWith(spelling) && spelling.length() >= Math.max(4, pair[0].length() - 1);
                boolean whole = spelling.equals(pair[0]) || spelling.startsWith(pair[0] + " "); // Makkah al-Mukarramah
                if ((typing || whole) && !aliases.contains(pair[1])) aliases.add(pair[1]);
            }
        }
        return aliases;
    }

    /** Whether the name has these whole words, so that Lydd finds Lod but not Lodi */
    static boolean hasWords(String name, String words) {
        for (int at = name.indexOf(words); at >= 0; at = name.indexOf(words, at + 1)) {
            int end = at + words.length();
            if ((at == 0 || name.charAt(at - 1) == ' ') && (end == name.length() || name.charAt(end) == ' ')) return true;
        }
        return false;
    }

    private synchronized String[] getSearchNames() {
        if (mSearchNames == null) {
            mSearchNames = normalizeAll(mNames);
        }
        return mSearchNames;
    }

    private synchronized String[] getSearchOtherNames() {
        if (mSearchOtherNames == null) {
            mSearchOtherNames = normalizeAll(mOtherNames);
        }
        return mSearchOtherNames;
    }

    private static String[] normalizeAll(String[] names) {
        String[] normalized = new String[names.length];
        for (int i = 0; i < names.length; i++) {
            normalized[i] = normalize(names[i]);
        }
        return normalized;
    }

    /** Lower case, without accents, with any punctuation as a space: "Sant Julià de Lòria" becomes "sant julia de loria" */
    static String normalize(String text) {
        String ascii = normalizeAscii(text);
        return ascii != null ? ascii : normalizeFully(text);
    }

    static String normalizeFully(String text) {
        String plain = MARKS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll(""); // Also takes the hamza off أ إ آ ؤ ئ
        plain = NOT_LETTERS.matcher(plain.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
        // Arabic spellings vary in a few letters, and in whether a name is written with al-: الرياض is also رياض
        plain = plain.replace('\u0671', '\u0627').replace('\u0629', '\u0647').replace('\u0649', '\u064A')
                .replace('\u06A9', '\u0643').replace('\u06CC', '\u064A').replace("\u0640", "");
        return ARABIC_AL.matcher(plain).replaceAll("$1");
    }

    /**
     * The same as normalize() for plain ASCII, most of the list, and much quicker; null for anything else
     */
    private static String normalizeAscii(String text) {
        StringBuilder plain = new StringBuilder(text.length());
        boolean space = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 128) return null;
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                if (space && plain.length() > 0) plain.append(' ');
                plain.append(c);
                space = false;
            } else if (c >= 'A' && c <= 'Z') {
                if (space && plain.length() > 0) plain.append(' ');
                plain.append((char) (c + ('a' - 'A')));
                space = false;
            } else {
                space = true; // Punctuation and spaces become one space between words
            }
        }
        return plain.toString();
    }

    public static double distanceKm(double latitude1, double longitude1, double latitude2, double longitude2) {
        double dLatitude = Math.toRadians(latitude2 - latitude1);
        double dLongitude = Math.toRadians(longitude2 - longitude1);
        double a = Math.sin(dLatitude / 2) * Math.sin(dLatitude / 2)
                + Math.cos(Math.toRadians(latitude1)) * Math.cos(Math.toRadians(latitude2)) * Math.sin(dLongitude / 2) * Math.sin(dLongitude / 2);
        return 6371.0088 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
