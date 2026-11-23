package io.github.rkaushikdev.doublerbay.report;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.util.Decimals;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Getting a report out of this service and into the spreadsheet it will end up in.
 *
 * <p>Every report on this desk ends its life in a spreadsheet. Somebody pastes it into one, sorts it a
 * different way, adds a column of their own and emails it. Pretending otherwise means they retype
 * it, and a retyped report is a report with a transcription error in it.
 *
 * <p>So the reports come out as csv, with the columns named the way the printed version names them.
 * The point is that the exported file and the printed one carry the same numbers, which is why both
 * go through the same row builders.
 *
 * <p>Quoting follows the spreadsheet convention rather than being hand rolled at each call. A part
 * number contains no comma today and one will the moment somebody adds a variant suffix, and hand
 * rolled joining is how that becomes a corrupted file six months later. A value is quoted only when
 * it carries a comma, a double quote or a line break, a quote inside it is doubled, and a row that
 * is one empty value is written as a pair of quotes so it does not read back as a blank line.
 */
public final class Export {

    /** Written with the line ending a spreadsheet on either platform reads. */
    public static final String LINE_END = "\r\n";

    /** The columns of the due list. */
    public static final List<String> DUE_HEADER = List.of("tail", "card", "what", "due", "days", "clock", "over");

    /** The columns of a work pack. */
    public static final List<String> PACK_HEADER = List.of("block", "reference", "detail", "over");

    /** The columns of the growth report. */
    public static final List<String> GROWTH_HEADER = List.of("card", "readings", "total", "worst rate", "trend");

    private Export() {}

    /** Write a header and its rows, refusing a row that is the wrong width. */
    public static String asCsv(List<String> header, Iterable<? extends List<String>> rows) {
        if (header.isEmpty()) {
            throw new BadReading("a csv file has a header on it");
        }
        StringBuilder out = new StringBuilder();
        writeRow(out, header);
        int at = 2;
        for (List<String> row : rows) {
            if (row.size() != header.size()) {
                throw new BadReading(
                        "row " + at + " has " + row.size() + " columns and the header has " + header.size());
            }
            writeRow(out, row);
            at++;
        }
        return out.toString();
    }

    private static void writeRow(StringBuilder out, List<String> row) {
        if (row.size() == 1 && row.get(0).isEmpty()) {
            out.append("\"\"").append(LINE_END);
            return;
        }
        List<String> written = new ArrayList<>(row.size());
        for (String value : row) {
            written.add(quoted(value));
        }
        out.append(String.join(",", written)).append(LINE_END);
    }

    private static String quoted(String value) {
        boolean needs = false;
        for (int index = 0; index < value.length(); index++) {
            char at = value.charAt(index);
            if (at == ',' || at == '"' || at == '\r' || at == '\n') {
                needs = true;
                break;
            }
        }
        return needs ? "\"" + value.replace("\"", "\"\"") + "\"" : value;
    }

    /** The due list as rows, one item a row. */
    public static List<List<String>> dueRows(List<DueItem> items) {
        return items.stream()
                .map(one -> List.of(
                        one.serial().number(),
                        one.against().toString(),
                        one.what(),
                        one.due().toString(),
                        String.valueOf(one.margin().days()),
                        one.margin().clock().value(),
                        one.isOverrun() ? "yes" : "no"))
                .toList();
    }

    /** The due list, in the order it was given. */
    public static String dueAsCsv(List<DueItem> items) {
        return asCsv(DUE_HEADER, dueRows(items));
    }

    /**
     * Every line of a work pack, with the block it came from as a column.
     *
     * <p>The printed version puts the block in a heading. A spreadsheet cannot sort on a heading, so
     * here it becomes a column on every row.
     */
    public static List<List<String>> packRows(WorkPack pack) {
        List<List<String>> rows = new ArrayList<>();
        for (Map.Entry<String, List<PackLine>> block : pack.blocks()) {
            for (PackLine one : block.getValue()) {
                rows.add(List.of(block.getKey(), one.reference(), one.detail(), one.overrun() ? "yes" : "no"));
            }
        }
        return List.copyOf(rows);
    }

    /** One work pack, flattened so a planner can sort it. */
    public static String packAsCsv(WorkPack pack) {
        return asCsv(PACK_HEADER, packRows(pack));
    }

    /** Measured crack growth as rows, one card a row, the worst rate blank when none was measurable. */
    public static List<List<String>> growthRows(List<Growth> every) {
        return every.stream()
                .map(one -> List.of(
                        one.reference().toString(),
                        String.valueOf(one.readings()),
                        Decimals.text(one.total().inches()),
                        one.worstRateText().orElse(""),
                        one.isATrend() ? "yes" : "no"))
                .toList();
    }

    /** Measured crack growth, one card a row. */
    public static String growthAsCsv(List<Growth> every) {
        return asCsv(GROWTH_HEADER, growthRows(every));
    }

    /** How many rows a written file carries, header not counted. */
    public static int lineCount(String written) {
        return Math.max(written.split(LINE_END, -1).length - 2, 0);
    }
}
