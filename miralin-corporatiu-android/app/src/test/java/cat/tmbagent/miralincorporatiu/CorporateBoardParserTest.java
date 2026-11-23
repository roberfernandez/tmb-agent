package cat.tmbagent.miralincorporatiu;

import org.junit.Test;
import static org.junit.Assert.*;

public class CorporateBoardParserTest {
    @Test public void manual407UsesItsOwnIntervalAtTheSelectedStation() {
        String html = "<table id='indexForm:tableArrivals2'><tbody>"
            + "<tr><td>405</td><td>23:10</td><td>05:00</td><td>La Pau</td></tr>"
            + "<tr><td>407</td><td>23:17</td><td>06:07</td><td>Jaume I</td></tr>"
            + "</tbody></table><div>MDM: 06:06</div>";
        CorporateBoardParser.Board board = CorporateBoardParser.parse(html);
        CorporateTrainSelection.Train train = CorporateTrainSelection.find(board, "407", 2);
        assertTrue(CorporateBoardParser.exceedsMdm(train.previousTrainGap, board.mdm));
    }

    @Test public void comparesTrainIntervalWithThePublishedMdmToTheSecond() {
        String board = "<table id='indexForm:tableArrivals2'><tbody></tbody></table>";
        String mdm = CorporateBoardParser.parse(board + "<div>MDM: <span>06:06</span></div> Ind.Reg.: 74").mdm;
        assertEquals("06:06", mdm);
        assertFalse(CorporateBoardParser.exceedsMdm("06:05", mdm));
        assertFalse(CorporateBoardParser.exceedsMdm("06:06", mdm));
        assertTrue(CorporateBoardParser.exceedsMdm("06:07", mdm));
        assertFalse(CorporateBoardParser.exceedsMdm("06:46", CorporateBoardParser.parse(board).mdm));
        assertEquals(mdm, CorporateBoardParser.parse(board
            + "<table><tr><th>MDM:</th><th>Ind.Reg.:</th></tr><tr><td>06:06</td><td>74</td></tr></table>").mdm);
    }

    @Test public void readsBothPrimefacesTablesWithoutChangingSortida() {
        String html = "<html><body>"
            + "<div id='indexForm:tableArrivals1'><table><thead><tr><th>PROP.TREN</th><th>SORTIDA</th></tr></thead>"
            + "<tbody><tr><td>413</td><td>00:18</td></tr></tbody></table></div>"
            + "<div id='indexForm:tableArrivals2'><table><thead><tr><th>Tren</th><th>Antelació</th></tr></thead>"
            + "<tbody><tr><td>409</td><td>4 min</td></tr></tbody></table></div></body></html>";
        CorporateBoardParser.Board board = CorporateBoardParser.parse(html);
        assertEquals("SORTIDA", board.via1.headers.get(1));
        assertEquals("00:18", board.via1.rows.get(0).get(1));
        assertEquals("409", board.via2.rows.get(0).get(0));
        assertEquals("4 min", board.via2.rows.get(0).get(1));
    }

    @Test public void tablePresentButEmptyIsNotMissing() {
        String html = "<table id='indexForm:tableArrivals1'><tbody>"
            + "<tr class='ui-datatable-empty-message'><td colspan='2'>No records</td></tr>"
            + "</tbody></table>"
            + "<table id='indexForm:tableArrivals2'><tbody></tbody></table>";
        CorporateBoardParser.Board board = CorporateBoardParser.parse(html);
        assertTrue(board.via1.present);
        assertTrue(board.via2.present);
        assertTrue(board.via1.rows.isEmpty());
        assertTrue(board.via2.rows.isEmpty());
    }

    @Test public void missingSecondTableNotMisreportedEmpty() {
        String html = "<div id='indexForm:tableArrivals1'><table><tbody><tr><td>408</td></tr>"
            + "</tbody></table></div>";
        CorporateBoardParser.Board board = CorporateBoardParser.parse(html);
        assertTrue(board.via1.present);
        assertFalse(board.via2.present);
    }

    @Test(expected = IllegalArgumentException.class)
    public void loginOrUnrecognizedHtmlIsErrorNotNoTrains() {
        CorporateBoardParser.parse("<html><h1>Inicia sessió</h1></html>");
    }

    @Test public void ignoresHiddenPrimeFacesTitlesAndKeepsLiteralTime() {
        String html = "<div id='indexForm:tableArrivals1'><table><tbody><tr><td>"
            + "<span class='ui-column-title'>Tren</span>413</td><td>"
            + "<span class='ui-column-title'>Sortida</span>00:18:00</td></tr>"
            + "</tbody></table></div>";
        assertEquals("00:18:00", CorporateBoardParser.parse(html).via1.rows.get(0).get(1));
    }
}
