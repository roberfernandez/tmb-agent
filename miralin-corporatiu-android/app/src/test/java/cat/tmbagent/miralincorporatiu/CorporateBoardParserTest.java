package cat.tmbagent.miralincorporatiu;

import org.junit.Test;
import static org.junit.Assert.*;

public class CorporateBoardParserTest {
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
