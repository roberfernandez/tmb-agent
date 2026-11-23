package cat.tmbagent.miralincorporatiu;

import org.junit.Test;
import static org.junit.Assert.*;

public class CorporateEyeNotificationTextTest {
    private static final long NOW = 1795467600000L; // deterministic reference

    @Test public void manualContainsTrainStationAndEtaWithoutCorporateFiller() {
        CorporateEyeNotificationText n = CorporateEyeNotificationText.forState(
            true, "", "405", "Jaume I", "La Pau", "", "", 2, NOW + 16*60000L, NOW, false);
        assertEquals("405 · Jaume I · Via 2", n.title);
        assertTrue(n.body.startsWith("La Pau ≈"));
        assertTrue(n.body.contains("en 16 min"));
        assertFalse(n.body.contains("Miralín corporatiu"));
        assertFalse(n.body.contains("Seguiment en segon pla"));
    }

    @Test public void stationUsesEstAndUpcomingTrain() {
        CorporateEyeNotificationText n = CorporateEyeNotificationText.forState(
            false, "EST", "405", "", "Urquinaona", "Urquinaona", "", 2,
            NOW + 12*60000L, NOW, false);
        assertEquals("EST · Urquinaona · Via 2", n.title);
        assertTrue(n.body.startsWith("405 ≈"));
        assertTrue(n.body.contains("en 12 min"));
    }

    @Test public void unassignedTransferNeverInventsTrain() {
        CorporateEyeNotificationText n = CorporateEyeNotificationText.forState(
            false, "TRA", "", "", "Joanic", "Urquinaona", "Joanic", 1, -1, NOW, false);
        assertEquals("TRA · Urquinaona → Joanic", n.title);
        assertTrue(n.body.contains("sense tren assignat"));
        assertFalse(n.title.contains("405"));
    }

    @Test public void drivingNamesServiceDestinationAndStaleData() {
        CorporateEyeNotificationText n = CorporateEyeNotificationText.forState(
            false, "CON", "410", "Girona", "La Pau", "Girona", "La Pau", 2,
            NOW + 42*60000L, NOW, true);
        assertEquals("CON · 410 · Girona · Via 2", n.title);
        assertTrue(n.body.startsWith("Final La Pau · ≈"));
        assertTrue(n.body.contains("dades anteriors"));
    }
}
