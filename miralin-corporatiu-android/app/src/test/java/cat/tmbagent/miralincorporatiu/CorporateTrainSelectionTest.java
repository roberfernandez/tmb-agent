package cat.tmbagent.miralincorporatiu;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public final class CorporateTrainSelectionTest {
    @Test public void readsOfficialValuesUnchanged() {
        CorporateBoardParser.Track via1 = new CorporateBoardParser.Track(1, true,
            Collections.emptyList(), Collections.emptyList());
        CorporateBoardParser.Track via2 = new CorporateBoardParser.Track(2, true,
            Collections.emptyList(), Arrays.asList(
                Arrays.asList("406", "09:32", "04:21", "La Pau", "Via 2"),
                Arrays.asList("407", "09:36", "04:04", "El Maresme | Fòrum", "Via 2"),
                Arrays.asList("416", "10:04", "03:53", "Via Júlia", "Via 1")));
        CorporateBoardParser.Board board = new CorporateBoardParser.Board(via1, via2);
        CorporateTrainSelection.Train train = CorporateTrainSelection.find(board, " 406 ");
        assertNotNull(train);
        assertEquals("La Pau", train.station);
        assertEquals("Via 2", train.track);
        assertEquals("04:21", train.previousTrainGap);
        assertEquals("09:32", train.departure);
        assertEquals("Via 1", CorporateTrainSelection.find(board, "416").track);
        assertNull(CorporateTrainSelection.find(board, "999"));
    }

    @Test public void sameTrainHasDifferentDepartureInEachViaBoard() {
        CorporateBoardParser.Track via1 = new CorporateBoardParser.Track(1, true,
            Collections.emptyList(), Arrays.asList(
                Arrays.asList("406", "10:57", "03:52", "Maragall", "Via 2")));
        CorporateBoardParser.Track via2 = new CorporateBoardParser.Track(2, true,
            Collections.emptyList(), Arrays.asList(
                Arrays.asList("406", "10:39", "03:52", "Maragall", "Via 2")));
        CorporateBoardParser.Board board = new CorporateBoardParser.Board(via1, via2);
        assertEquals("10:57", CorporateTrainSelection.find(board, "406", 1).departure);
        assertEquals("10:39", CorporateTrainSelection.find(board, "406", 2).departure);
        assertNull(CorporateTrainSelection.find(board, "999", 2));
    }
}
