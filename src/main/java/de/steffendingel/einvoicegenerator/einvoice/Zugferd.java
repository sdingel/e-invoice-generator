package de.steffendingel.einvoicegenerator.einvoice;

import org.mustangproject.ZUGFeRD.Profile;
import org.mustangproject.ZUGFeRD.Profiles;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Date;

/** Settings shared by the ZUGFeRD steps ⑦ and ⑧, so that they cannot drift apart. */
final class Zugferd {

    /** ZUGFeRD profile EN 16931 ("Comfort"). */
    static final Profile PROFILE = Profiles.getByName("EN16931");

    static final String PRODUCER = "E-Invoice Generator";

    private Zugferd() {
    }

    /**
     * Mustang takes {@link Date} values and formats them in the time zone that was the JVM default when
     * its formatter was created (one per thread). Midnight in another zone would shift every date in the
     * XML by a day (found in the proof of concept). Noon in the default zone keeps the date correct even
     * if the zones differ by up to 12 hours.
     */
    static Date date(LocalDate date) {
        return Date.from(date.atTime(LocalTime.NOON).atZone(ZoneId.systemDefault()).toInstant());
    }
}
