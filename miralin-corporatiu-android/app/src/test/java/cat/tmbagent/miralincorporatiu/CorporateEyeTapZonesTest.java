package cat.tmbagent.miralincorporatiu;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class CorporateEyeTapZonesTest {
    @Test public void threeDistinctTouchTargets() {
        assertEquals(CorporateEyeTapZones.MIRALIN, CorporateEyeTapZones.actionFor(0, 176));
        assertEquals(CorporateEyeTapZones.MIRALIN, CorporateEyeTapZones.actionFor(58, 176));
        assertEquals(CorporateEyeTapZones.SERVICE, CorporateEyeTapZones.actionFor(59, 176));
        assertEquals(CorporateEyeTapZones.SERVICE, CorporateEyeTapZones.actionFor(117, 176));
        assertEquals(CorporateEyeTapZones.VIEWER, CorporateEyeTapZones.actionFor(118, 176));
        assertEquals(CorporateEyeTapZones.VIEWER, CorporateEyeTapZones.actionFor(175, 176));
    }
    @Test public void zeroSizedAccessibilityClickUsesCenter() {
        assertEquals(CorporateEyeTapZones.SERVICE, CorporateEyeTapZones.actionFor(20, 0));
    }
}
