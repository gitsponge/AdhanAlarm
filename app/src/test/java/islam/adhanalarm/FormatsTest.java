package islam.adhanalarm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class FormatsTest {

    @Test
    public void numbersAreReadAsPeopleTypeThem() {
        assertEquals("51.5", Formats.plainNumber("51.5"));
        assertEquals("51.5", Formats.plainNumber("51,5")); // German, French and others
        assertEquals("51.5", Formats.plainNumber("٥١٫٥")); // Arabic digits and decimal point
        assertEquals("-0.13", Formats.plainNumber(" −0,13 "));
        assertEquals("-79.4", Formats.plainNumber("‏-79.4")); // With a right-to-left mark
        assertEquals("1010", Formats.plainNumber("۱۰۱۰")); // Persian digits
    }

    @Test
    public void anythingElseIsNotANumber() {
        assertNull(Formats.plainNumber(""));
        assertNull(Formats.plainNumber("-"));
        assertNull(Formats.plainNumber("."));
        assertNull(Formats.plainNumber("1e5"));
        assertNull(Formats.plainNumber("NaN"));
        assertNull(Formats.plainNumber("51.5.2"));
        assertNull(Formats.plainNumber(null));
    }
}
