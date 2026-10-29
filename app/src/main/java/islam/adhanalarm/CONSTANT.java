package islam.adhanalarm;

import net.sourceforge.jitl.ExtremeLatitude;
import net.sourceforge.jitl.Mathhab;
import net.sourceforge.jitl.Method;
import net.sourceforge.jitl.Rounding;

import java.util.Arrays;

public class CONSTANT {

	public static final String ACTION_NOTIFY_PRAYER_TIME = "islam.adhanalarm.action.NOTIFY_PRAYER_TIME";
	public static final String ACTION_NOTIFY_REMINDER = "islam.adhanalarm.action.NOTIFY_REMINDER";
	public static final String ACTION_NEW_DAY = "islam.adhanalarm.action.NEW_DAY";
	public static final String ACTION_PLAY_BISMILLAH = "islam.adhanalarm.action.PLAY_BISMILLAH";
	public static final String ACTION_NOTIFICATION_DELETED = "islam.adhanalarm.action.NOTIFICATION_DELETED";
	public static final String ACTION_NOTIFICATION_STOPPED = "islam.adhanalarm.action.NOTIFICATION_STOPPED";

	public static final String EXTRA_TIME_INDEX = "timeIndex";
	public static final String EXTRA_ACTUAL_TIME = "actualTime";
	public static final String EXTRA_NOTIFICATION_TYPE = "notificationType"; // Overrides the user's setting, used by test alarms

	public static final short FAJR = 0, SUNRISE = 1, DHUHR = 2, ASR = 3, MAGHRIB = 4, ISHAA = 5, NEXT_FAJR = 6; // Notification Times
	public static final int[] TIME_NAMES = new int[] {R.string.fajr, R.string.sunrise, R.string.dhuhr, R.string.asr, R.string.maghrib, R.string.ishaa, R.string.fajr};
	public static final int[] ARABIC_NAMES = new int[] {R.string.arabic_fajr, R.string.arabic_sunrise, R.string.arabic_dhuhr, R.string.arabic_asr, R.string.arabic_maghrib, R.string.arabic_ishaa, R.string.arabic_fajr};

	public static final short NOTIFICATION_NONE = 0, NOTIFICATION_DEFAULT = 1, NOTIFICATION_BEEP = 2, NOTIFICATION_PLAY = 3; // Off, notification, chime, adhan

	public static final Rounding[] ROUNDING_TYPES = new Rounding[]{Rounding.NONE, Rounding.NORMAL, Rounding.SPECIAL, Rounding.AGRESSIVE};
	public static final short DEFAULT_ROUNDING_TYPE = 2; // Special

	/** Calculation methods, as stored in the "method" setting */
	public static final String METHOD_MWL = "mwl", METHOD_ISNA = "isna", METHOD_EGYPT = "egypt", METHOD_KARACHI = "karachi",
			METHOD_UMM_AL_QURA = "ummalqura", METHOD_GULF = "gulf", METHOD_KUWAIT = "kuwait", METHOD_QATAR = "qatar",
			METHOD_SINGAPORE = "singapore", METHOD_DIYANET = "diyanet", METHOD_FIXED_ISHAA = "fixedisha";
	public static final String DEFAULT_METHOD = METHOD_MWL;

	public static final String HIGH_LATITUDE_SEVENTH = "seventh", HIGH_LATITUDE_GOOD_DAY = "goodDay", HIGH_LATITUDE_LATITUDE = "latitude";

	/**
	 * The fajr angle and the isha angle, or the minutes after maghrib for isha where the angle is 0
	 */
	public static double[] getMethodParameters(String method) {
		switch (method) {
			case METHOD_ISNA: return new double[]{15, 15, 0};
			case METHOD_EGYPT: return new double[]{20, 18, 0};
			case METHOD_KARACHI: return new double[]{18, 18, 0};
			case METHOD_UMM_AL_QURA: return new double[]{19, 0, 90};
			case METHOD_GULF: return new double[]{19.5, 0, 90};
			case METHOD_KUWAIT: return new double[]{18, 17.5, 0};
			case METHOD_QATAR: return new double[]{18, 0, 90};
			case METHOD_SINGAPORE: return new double[]{20, 18, 0};
			case METHOD_DIYANET: return new double[]{18, 17, 0};
			case METHOD_FIXED_ISHAA: return new double[]{19.5, 0, 90};
			default: return new double[]{18, 17, 0}; // Muslim World League
		}
	}

	/**
	 * Minutes a method adds to each time, from fajr to isha, before the user's own adjustments
	 */
	public static int[] getMethodAdjustments(String method) {
		if (METHOD_DIYANET.equals(method)) {
			return new int[]{0, -7, 5, 4, 7, 0}; // Diyanet's temkin: a margin of safety, with sunrise earlier so fajr ends in time
		}
		return new int[6];
	}

	public static Method createMethod(String method, boolean hanafi, String highLatitude, Rounding rounding) {
		double[] parameters = getMethodParameters(method);
		Method result = Method.MUSLIM_LEAGUE.copy();
		result.setFajrAng(parameters[0]);
		result.setIshaaAng(parameters[1]);
		result.setIshaaInv((int) parameters[2]);
		result.setMathhab(hanafi ? Mathhab.HANAFI : Mathhab.SHAAFI);
		result.setRound(METHOD_DIYANET.equals(method) ? Rounding.NONE : rounding); // Diyanet rounds its own way, see ScheduleHandler
		switch (highLatitude) {
			case HIGH_LATITUDE_GOOD_DAY: result.setExtremeLatitude(ExtremeLatitude.GOOD_INVALID); break;
			case HIGH_LATITUDE_LATITUDE: result.setExtremeLatitude(ExtremeLatitude.LAT_INVALID); break;
			default: result.setExtremeLatitude(ExtremeLatitude.SEVEN_NIGHT_INVALID); // Only where the sun doesn't reach the angle
		}
		return result;
	}

	/** The method most mosques in a country use, by ISO 3166 two letter country code */
	public static String getDefaultMethod(String country) {
		if (country == null) return DEFAULT_METHOD;
		String code = country.toUpperCase(java.util.Locale.ROOT);
		for (Object[] entry : COUNTRY_METHODS) {
			if (Arrays.asList((String[]) entry[1]).contains(code)) return (String) entry[0];
		}
		return DEFAULT_METHOD;
	}

	private static final Object[][] COUNTRY_METHODS = new Object[][]{
			{METHOD_EGYPT, new String[]{
					// Africa
					"AO", "BI", "BJ", "BF", "BW", "CF", "CI", "CM", "CG", "KM", "CV", "DJ", "DZ", "EG", "ER", "EH", "ET", "GA", "GH", "GN", "GM", "GW", "GQ", "KE", "LR", "LY", "LS", "MA", "MG", "ML", "MZ", "MR", "MU", "MW", "YT", "NA", "NE", "NG", "RE", "RW", "SD", "SN", "SL", "SO", "ST", "SZ", "SC", "TD", "TG", "TN", "TZ", "UG", "ZA", "CD", "ZW",
					// Iraq, Lebanon, Syria
					"IQ", "LB", "SY"}},
			{METHOD_KARACHI, new String[]{"AF", "BD", "IN", "PK"}},
			{METHOD_ISNA, new String[]{"US", "CA"}},
			{METHOD_UMM_AL_QURA, new String[]{"BH", "SA", "YE"}},
			{METHOD_GULF, new String[]{"AE", "OM"}},
			{METHOD_KUWAIT, new String[]{"KW"}},
			{METHOD_QATAR, new String[]{"QA"}},
			{METHOD_SINGAPORE, new String[]{"BN", "ID", "MY", "SG"}},
			{METHOD_DIYANET, new String[]{"TR"}}
			// Everywhere else, Europe and the Far East included: the Muslim World League
	};

	/** The 3.x settings stored the method as an index into this list */
	public static final String[] OLD_METHOD_ORDER = new String[]{METHOD_EGYPT, METHOD_KARACHI, METHOD_KARACHI, METHOD_ISNA, METHOD_MWL, METHOD_UMM_AL_QURA, METHOD_FIXED_ISHAA};
	public static final int OLD_METHOD_KARACHI_HANAFI = 2;

	private CONSTANT() {
		// Private constructor to enforce un-instantiability.
	}
}
