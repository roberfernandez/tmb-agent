package cat.tmbagent.miralincorporatiu;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reader for the HTML that TMB currently renders for the Miralín station board.
 * Not a public API: keep the DOM parsing isolated and fail visibly on layout changes.
 * Times and cells are shown as-is, without interpreting "SORTIDA" as arrival.
 */
final class CorporateBoardParser {
    private CorporateBoardParser() {}

    static Board parse(String html) {
        if (html == null || html.trim().isEmpty()) throw new IllegalArgumentException("Resposta HTML buida.");
        Document doc = Jsoup.parse(html);
        Track via1 = parseTrack(doc, "indexForm:tableArrivals1", 1);
        Track via2 = parseTrack(doc, "indexForm:tableArrivals2", 2);
        if (!via1.present && !via2.present) {
            throw new IllegalArgumentException(
                "No s'han trobat les taules del Miralín corporatiu. Pot haver canviat la pàgina o la sessió.");
        }
        return new Board(via1, via2, readMdm(doc));
    }

    private static final java.util.regex.Pattern MDM = java.util.regex.Pattern.compile(
        "(?i)\\bMDM\\s*:?\\s*([0-9]{1,3}:[0-5][0-9])(?![:0-9])");

    private static String readMdm(Document doc) {
        java.util.regex.Matcher inline = MDM.matcher(doc.text());
        if (inline.find()) return inline.group(1);
        for (Element cell : doc.select("th, td")) {
            if (!cell.text().matches("(?i)MDM\\s*:?")) continue;
            Element row = cell.parent();
            Element table = cell.closest("table");
            if (table == null) continue;
            int index = cell.elementSiblingIndex();
            for (Element data : table.select("tr")) {
                if (data == row || data.childrenSize() <= index) continue;
                String value = data.child(index).text();
                if (data.closest("table") == table && value.matches("[0-9]{1,3}:[0-5][0-9]")) return value;
            }
        }
        return "";
    }

    static boolean exceedsMdm(String headway, String mdm) {
        int threshold = intervalSeconds(mdm);
        return threshold > 0 && intervalSeconds(headway) > threshold;
    }

    private static int intervalSeconds(String value) {
        if (value == null || !value.trim().matches("[0-9]{1,3}:[0-5][0-9]")) return -1;
        String[] time = value.trim().split(":");
        return Integer.parseInt(time[0]) * 60 + Integer.parseInt(time[1]);
    }

    private static Track parseTrack(Document doc, String tableId, int number) {
        Element root = doc.getElementById(tableId);
        if (root == null) root = doc.getElementById(tableId + "_data");
        if (root == null) return new Track(number, false, Collections.emptyList(), Collections.emptyList());

        Element table = root.tagName().equalsIgnoreCase("table") ? root : root.selectFirst("table");
        if (table == null) {
            // PrimeFaces sometimes provides the actual tbody with the *_data ID.
            if (!root.tagName().equalsIgnoreCase("tbody")) {
                throw new IllegalArgumentException("L'estructura de la taula de la via " + number + " ha canviat.");
            }
            table = root.parent();
        }
        if (table == null) throw new IllegalArgumentException("Taula de la via " + number + " no disponible.");

        List<String> headers = new ArrayList<>();
        Element head = table.selectFirst("thead");
        if (head != null) {
            for (Element th : head.select("th")) {
                String label = cleanText(th);
                if (!label.trim().isEmpty()) headers.add(label);
                else headers.add(" ");
            }
        }

        List<List<String>> rows = new ArrayList<>();
        Element body = table.selectFirst("tbody");
        if (body == null) {
            throw new IllegalArgumentException("La taula de la via " + number + " no conté files de dades.");
        }
        for (Element tr : body.select("tr")) {
            // Direct cells, not cells belonging to nested tables.
            List<String> cells = new ArrayList<>();
            boolean emptyMessage = tr.hasClass("ui-datatable-empty-message");
            for (Element td : tr.children()) {
                if (!"td".equalsIgnoreCase(td.tagName())) continue;
                if (td.hasClass("ui-datatable-empty-message")) emptyMessage = true;
                cells.add(cleanText(td));
            }
            if (emptyMessage) continue;
            if (!cells.isEmpty() && cells.stream().anyMatch(v -> !v.trim().isEmpty())) {
                rows.add(Collections.unmodifiableList(cells));
            }
            if (rows.size() > 150) throw new IllegalArgumentException("Taula de la via " + number + " massa gran.");
        }
        return new Track(number, true, Collections.unmodifiableList(new ArrayList<>(headers)), Collections.unmodifiableList(new ArrayList<>(rows)));
    }

    private static String cleanText(Element element) {
        Element copy = element.clone();
        copy.select("script, style, .ui-column-title, .ui-helper-hidden, [aria-hidden=true]").remove();
        String result = copy.text().replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
        return result.length() > 220 ? result.substring(0, 220) : result;
    }

    static final class Track {
        final int number;
        final boolean present;
        final List<String> headers;
        final List<List<String>> rows;

        Track(int number, boolean present, List<String> headers, List<List<String>> rows) {
            this.number = number;
            this.present = present;
            this.headers = headers;
            this.rows = rows;
        }
    }

    static final class Board {
        final Track via1;
        final Track via2;

        final String mdm;

        Board(Track via1, Track via2) { this(via1, via2, ""); }

        Board(Track via1, Track via2, String mdm) {
            this.mdm = mdm;
            this.via1 = via1;
            this.via2 = via2;
        }
    }
}
