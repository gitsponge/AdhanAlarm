package islam.adhanalarm.handler;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LocationHandlerTest {

    private static final long MINUTE = 60 * 1000;

    @Test
    public void afterAJourneyTheAppAsksWhereItIsNowAsItOpens() {
        assertTrue(LocationHandler.needsFreshFix(false, 3 * 60 * MINUTE, true)); // Off a three-hour flight
    }

    @Test
    public void aRecentFixIsTrustedAsTheAppOpens() {
        assertFalse(LocationHandler.needsFreshFix(false, 5 * MINUTE, true));
    }

    @Test
    public void askingWhereThePhoneIsNowWantsAFixFromTheLastCoupleOfMinutes() {
        assertFalse(LocationHandler.needsFreshFix(true, MINUTE, true));
        assertTrue(LocationHandler.needsFreshFix(true, 3 * MINUTE, true));
    }

    @Test
    public void withNoFixAtAllOneIsAskedFor() {
        assertTrue(LocationHandler.needsFreshFix(false, Long.MAX_VALUE, true));
        assertTrue(LocationHandler.needsFreshFix(true, Long.MAX_VALUE, true));
    }

    @Test
    public void withNothingToAskTheLastFixIsAllThereIs() {
        assertFalse(LocationHandler.needsFreshFix(false, 3 * 60 * MINUTE, false));
        assertFalse(LocationHandler.needsFreshFix(true, 3 * 60 * MINUTE, false));
    }
}
