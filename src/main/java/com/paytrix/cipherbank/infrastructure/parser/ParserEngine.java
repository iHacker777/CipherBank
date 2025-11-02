package com.paytrix.cipherbank.infrastructure.parser;

import com.paytrix.cipherbank.infrastructure.config.parser.ParserConfig;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

public class ParserEngine {

    @Data @AllArgsConstructor
    public static class ParsedRow {
        private LocalDateTime transactionDateTime;  // CHANGED: Instant -> LocalDateTime (no UTC conversion)
        private BigDecimal amount;
        private BigDecimal balance;   // nullable
        private String reference;
        private String orderId;
        private String utr;
        private boolean payIn;
    }

    public enum FileKind { CSV, XLSX, XLS, PDF }

    // column kind guides numeric validation & field naming
    private enum ColumnKind { DATE, TIME, REFERENCE, CREDIT, DEBIT, AMOUNT, BALANCE, OTHER }

    // how far to scan left/right if direct cell is blank (only as last resort)
    private static final int EXCEL_MERGE_NEIGHBOR_SCAN = 3;

    /** Header context captured at header-detection time so we can guard neighbor probing against any other headers */
    private static final class ExcelContext {
        final List<String> headerByCol;                   // merged header text per column (post-filter to expect)
        final Map<String, List<String>> expectSynonyms;   // headers.search.expect from config (field -> synonyms)
        ExcelContext(List<String> headerByCol, Map<String, List<String>> expectSynonyms) {
            this.headerByCol = headerByCol;
            this.expectSynonyms = expectSynonyms;
        }
    }

    public static FileKind detectKind(String filename, String contentType) {
        String name = Optional.ofNullable(filename).orElse("").toLowerCase();
        if (name.endsWith(".csv")) return FileKind.CSV;
        if (name.endsWith(".xlsx")) return FileKind.XLSX;
        if (name.endsWith(".xls")) return FileKind.XLS;
        if (name.endsWith(".pdf")) return FileKind.PDF;
        if (contentType != null) {
            String ct = contentType.toLowerCase();
            if (ct.contains("csv")) return FileKind.CSV;
            if (ct.contains("excel") || ct.contains("spreadsheetml")) return FileKind.XLSX;
            if (ct.contains("pdf")) return FileKind.PDF;
        }
        throw new IllegalArgumentException("Unsupported file type for: " + filename);
    }

    // === Public parse entrypoint ===
    public static List<ParsedRow> parse(InputStream in, String filename, String contentType,
                                        ParserConfig.BankConfig bankCfg,
                                        String accountNoOverride) throws IOException {

        FileKind kind = detectKind(filename, contentType);
        return switch (kind) {
            case CSV -> parseCsv(in, bankCfg.getCsv());
            case XLSX -> parseExcel(in, bankCfg.getXlsx());
            case XLS -> parseExcel(in, bankCfg.getXls());
            case PDF -> parsePdf(in, bankCfg.getPdf());
        };
    }

    // ================= CSV =================
    private static List<ParsedRow> parseCsv(InputStream in, ParserConfig.FileTypeConfig cfg) throws IOException {
        var rows = new ArrayList<ParsedRow>();
        Charset cs = Charset.forName(Optional.ofNullable(cfg.getCsv()).map(ParserConfig.Csv::getCharset).orElse("UTF-8"));
        char delim = Optional.ofNullable(cfg.getCsv()).map(c -> c.getDelimiter() != null ? c.getDelimiter().charAt(0) : ',').orElse(',');
        int skip = Optional.ofNullable(cfg.getCsv()).map(c -> c.getSkipRows() != null ? c.getSkipRows() : 0).orElse(0);

        try (Reader r = new InputStreamReader(in, cs)) {
            CSVFormat format = CSVFormat.DEFAULT.builder().setDelimiter(delim).setTrim(true).build();
            CSVParser parser = new CSVParser(r, format);
            List<CSVRecord> all = parser.getRecords();

            Map<String,Integer> idx;
            int startRow;

            if ("search".equalsIgnoreCase(cfg.getHeaders().getMode())) {
                var search = cfg.getHeaders().getSearch();
                Objects.requireNonNull(search.getExpect(), "headers.search.expect must be provided");
                int mrc = Optional.ofNullable(search.getMultiRowCount()).orElse(1);
                boolean oneBased = Optional.ofNullable(search.getUseOneBasedRowIndex()).orElse(Boolean.TRUE);
                String join = Optional.ofNullable(search.getMergeSeparator()).orElse(" ");

                if (search.getFixedHeaderRows() != null) {
                    int from = search.getFixedHeaderRows().getFrom();
                    int to   = search.getFixedHeaderRows().getTo();
                    if (oneBased) { from--; to--; }
                    var merged = mergeCsvHeader(all, from, to, join);
                    idx = mapMergedHeaderToIndex(merged, search.getExpect());
                    if (!isSufficientMapping(idx)) throw new IllegalStateException("CSV header mapping insufficient");
                    startRow = to + Optional.ofNullable(search.getRowStartOffset()).orElse(1);
                } else {
                    int from = search.getScanRange().getFrom();
                    int to   = search.getScanRange().getTo();
                    if (oneBased) { from--; to--; }
                    idx = null; startRow = -1;
                    for (int srow = from; srow <= to - (mrc - 1); srow++) {
                        var merged = mergeCsvHeader(all, srow, srow + mrc - 1, join);
                        var candidate = mapMergedHeaderToIndex(merged, search.getExpect());
                        if (isSufficientMapping(candidate)) {
                            idx = candidate;
                            startRow = (srow + mrc - 1) + Optional.ofNullable(search.getRowStartOffset()).orElse(1);
                            break;
                        }
                    }
                    if (idx == null) throw new IllegalStateException("CSV header not found by search/multi-row");
                }
            } else { // fixed
                idx = fixedIndex(cfg);
                startRow = cfg.getHeaders().getFixed().getRowStart();
            }

            for (int i = Math.max(skip, startRow); i < all.size(); i++) {
                var rec = all.get(i);
                var pr = mapCsvRecord(rec, idx, cfg);
                if (pr != null) rows.add(pr);
            }
        }
        return rows;
    }

    private static List<String> mergeCsvHeader(List<CSVRecord> all, int from, int to, String join) {
        int maxCols = 0;
        for (int r = from; r <= to && r < all.size(); r++) maxCols = Math.max(maxCols, all.get(r).size());
        List<String> merged = new ArrayList<>(Collections.nCopies(Math.max(maxCols,0), ""));
        for (int c = 0; c < maxCols; c++) {
            StringBuilder sb = new StringBuilder();
            for (int r = from; r <= to && r < all.size(); r++) {
                String v = c < all.get(r).size() ? Optional.ofNullable(all.get(r).get(c)).orElse("") : "";
                v = v.replace('\u00A0', ' ').trim();
                if (!v.isBlank()) {
                    if (sb.length() > 0) sb.append(join);
                    sb.append(v);
                }
            }
            merged.set(c, sb.toString());
        }
        return merged;
    }

    private static Map<String,Integer> mapMergedHeaderToIndex(List<String> merged, Map<String, List<String>> expect) {
        Map<String,Integer> idx = new HashMap<>();
        for (int c = 0; c < merged.size(); c++) {
            String col = norm(merged.get(c));
            for (var e : expect.entrySet()) {
                for (String syn : e.getValue()) {
                    String s = norm(syn);
                    if (headerMatch(col, s)) {
                        idx.putIfAbsent(e.getKey(), c);
                    }
                }
            }
        }
        return idx;
    }

    // ================= Excel (XLS/XLSX) =================
    private static List<ParsedRow> parseExcel(InputStream in, ParserConfig.FileTypeConfig cfg) throws IOException {
        var rows = new ArrayList<ParsedRow>();
        try (Workbook wb = WorkbookFactory.create(in)) {
            int sheetIdx = Integer.parseInt(Optional.ofNullable(cfg.getSheetIndex()).orElse("0"));
            Sheet sheet = wb.getSheetAt(sheetIdx);

            Map<String,Integer> idx;
            int startRow;
            ExcelContext ctx = null;

            if ("search".equalsIgnoreCase(cfg.getHeaders().getMode())) {
                var search = cfg.getHeaders().getSearch();
                Objects.requireNonNull(search.getExpect(), "headers.search.expect must be provided");
                int mrc = Optional.ofNullable(search.getMultiRowCount()).orElse(1);
                boolean oneBased = Optional.ofNullable(search.getUseOneBasedRowIndex()).orElse(Boolean.TRUE);
                String join = Optional.ofNullable(search.getMergeSeparator()).orElse(" ");

                if (search.getFixedHeaderRows() != null) {
                    int from = search.getFixedHeaderRows().getFrom();
                    int to   = search.getFixedHeaderRows().getTo();
                    if (oneBased) { from--; to--; }
                    var merged = mergeExcelHeaderFiltered(sheet, from, to, join, search.getExpect());
                    idx = mapMergedHeaderToIndex(merged, search.getExpect());
                    if (!isSufficientMapping(idx)) throw new IllegalStateException("Excel header mapping insufficient");
                    startRow = to + Optional.ofNullable(search.getRowStartOffset()).orElse(1);
                    ctx = new ExcelContext(merged, search.getExpect());
                } else {
                    int from = search.getScanRange().getFrom();
                    int to   = search.getScanRange().getTo();
                    if (oneBased) { from--; to--; }
                    idx = null; startRow = -1;
                    ExcelContext foundCtx = null;
                    for (int srow = from; srow <= to - (mrc - 1); srow++) {
                        var merged = mergeExcelHeaderFiltered(sheet, srow, srow + mrc - 1, join, search.getExpect());
                        var candidate = mapMergedHeaderToIndex(merged, search.getExpect());
                        if (isSufficientMapping(candidate)) {
                            idx = candidate;
                            startRow = (srow + mrc - 1) + Optional.ofNullable(search.getRowStartOffset()).orElse(1);
                            foundCtx = new ExcelContext(merged, search.getExpect());
                            break;
                        }
                    }
                    if (idx == null) throw new IllegalStateException("Excel header not found by search/multi-row");
                    ctx = foundCtx;
                }
            } else { // fixed
                idx = fixedIndex(cfg);
                startRow = cfg.getHeaders().getFixed().getRowStart();
                ctx = null; // no header band available in fixed mode
            }

            for (int r = startRow; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                if (stopNow(row, cfg.getRowStop())) break;

                String ref = readExcelFlexible(sheet, row, idx, "reference", ColumnKind.REFERENCE, cfg, ctx);
                BigDecimal amount = deriveAmount(
                        readExcelFlexible(sheet, row, idx, "amount",  ColumnKind.AMOUNT,  cfg, ctx),
                        readExcelFlexible(sheet, row, idx, "credit",  ColumnKind.CREDIT,  cfg, ctx),
                        readExcelFlexible(sheet, row, idx, "debit",   ColumnKind.DEBIT,   cfg, ctx),
                        cfg
                );
                BigDecimal balance = readDecimal(readExcelFlexible(sheet, row, idx, "balance", ColumnKind.BALANCE, cfg, ctx), cfg);

                // CHANGED: keep LocalDateTime (no conversion to Instant/UTC)
                LocalDateTime dt = parseDateTime(
                        readExcelFlexible(sheet, row, idx, "date", ColumnKind.DATE, cfg, ctx),
                        readExcelFlexible(sheet, row, idx, "time", ColumnKind.TIME, cfg, ctx),
                        cfg
                );

                rows.add(new ParsedRow(dt, amount, balance, ref, parseReference(ref, cfg.getReference()).orderId,
                        parseReference(ref, cfg.getReference()).utr,
                        computePayIn(amount,
                                parseReference(ref, cfg.getReference()).orderId,
                                parseReference(ref, cfg.getReference()).utr,
                                ref, cfg.getPayInRule())));
            }
        }
        return rows;
    }

    /**
     * Expect-aware header merge:
     *  - Scans header rows [fromRow..toRow]
     *  - Keeps only substrings that match expect synonyms (field -> list of variants)
     *  - Picks the strongest (longest) synonym seen per column
     *  - Fill-forwards to the right to cover visually merged header bands
     */
    private static List<String> mergeExcelHeaderFiltered(Sheet sheet, int fromRow, int toRow, String join,
                                                         Map<String, List<String>> expect) {
        int maxCols = 0;
        for (int r = fromRow; r <= toRow; r++) {
            Row row = sheet.getRow(r);
            if (row != null) maxCols = Math.max(maxCols, row.getLastCellNum());
        }
        List<String> merged = new ArrayList<>(Collections.nCopies(Math.max(maxCols,0), ""));

        for (int c = 0; c < merged.size(); c++) {
            String bestSynonym = ""; // keep the most specific (longest) match
            for (int r = fromRow; r <= toRow; r++) {
                Row row = sheet.getRow(r);
                String raw = (row == null) ? "" : Optional.ofNullable(getCellString(row.getCell(c))).orElse("");
                String normCell = norm(raw);
                if (normCell.isBlank()) continue;

                String matched = selectBestSynonym(normCell, expect);
                if (matched != null && matched.length() > bestSynonym.length()) {
                    bestSynonym = matched;
                }
            }
            merged.set(c, bestSynonym);
        }

        propagateHeaderRight(merged);
        return merged;
    }

    private static String selectBestSynonym(String normCell, Map<String, List<String>> expect) {
        if (expect == null) return null;
        String best = null;
        int bestLen = -1;
        for (Map.Entry<String, List<String>> e : expect.entrySet()) {
            for (String syn : e.getValue()) {
                String ns = norm(syn);
                if (ns.isBlank()) continue;
                if (normCell.equals(ns) || normCell.contains(ns)) {
                    if (ns.length() > bestLen) {
                        best = ns;
                        bestLen = ns.length();
                    }
                }
            }
        }
        return best;
    }

    private static void propagateHeaderRight(List<String> headerByCol) {
        String last = "";
        for (int i = 0; i < headerByCol.size(); i++) {
            String h = headerByCol.get(i);
            if (h != null && !h.isBlank()) {
                last = h;
            } else if (!last.isBlank()) {
                headerByCol.set(i, last);
            }
        }
    }

    private static boolean isSufficientMapping(Map<String,Integer> idx) {
        if (idx == null) return false;
        boolean hasDate = idx.containsKey("date");
        boolean hasRef  = idx.containsKey("reference");
        boolean hasAmt  = idx.containsKey("amount") || idx.containsKey("credit") || idx.containsKey("debit");
        return hasDate && hasRef && hasAmt;
    }

    // ===== common helpers reused by CSV/Excel =====
    private static Map<String,Integer> fixedIndex(ParserConfig.FileTypeConfig cfg) {
        Map<String,Integer> idx = new HashMap<>();
        var c = cfg.getHeaders().getFixed().getColumns();
        putIfNotNull(idx, "date", c.getDate());
        putIfNotNull(idx, "time", c.getTime());
        putIfNotNull(idx, "reference", c.getReference());
        putIfNotNull(idx, "credit", c.getCredit());
        putIfNotNull(idx, "debit", c.getDebit());
        putIfNotNull(idx, "amount", c.getAmount());
        putIfNotNull(idx, "balance", c.getBalance());
        return idx;
    }
    private static void putIfNotNull(Map<String,Integer> m, String k, Integer v) { if (v != null) m.put(k, v); }

    private static ParsedRow mapCsvRecord(CSVRecord rec, Map<String,Integer> idx, ParserConfig.FileTypeConfig cfg) {
        try {
            String ref = read(rec, idx.get("reference"));
            BigDecimal amount = deriveAmount(read(rec, idx.get("amount")),
                    read(rec, idx.get("credit")), read(rec, idx.get("debit")), cfg);
            BigDecimal balance = readDecimal(read(rec, idx.get("balance")), cfg);

            LocalDateTime dt = parseDateTime(read(rec, idx.get("date")), read(rec, idx.get("time")), cfg);

            var parsedRef = parseReference(ref, cfg.getReference());
            boolean payIn = computePayIn(amount, parsedRef.orderId, parsedRef.utr, ref, cfg.getPayInRule());

            return new ParsedRow(dt, amount, balance, ref, parsedRef.orderId, parsedRef.utr, payIn);
        } catch (Exception ex) {
            return null;
        }
    }

    private static String read(CSVRecord rec, Integer idx) {
        if (idx == null || idx >= rec.size()) return null;
        return Optional.ofNullable(rec.get(idx)).orElse("").trim();
    }

    private static boolean stopNow(Row row, ParserConfig.RowStop rs) {
        if (rs == null || rs.getMode() == null) return false;
        if ("none".equalsIgnoreCase(rs.getMode())) return false;
        if ("blankRows".equalsIgnoreCase(rs.getMode())) {
            boolean allBlank = true;
            for (int c = 0; c < row.getLastCellNum(); c++) {
                String s = getCellString(row.getCell(c));
                if (s != null && !s.isBlank()) { allBlank = false; break; }
            }
            return allBlank;
        }
        if ("until".equalsIgnoreCase(rs.getMode()) && rs.getUntilRegex() != null) {
            String line = rowToLine(row);
            return Pattern.compile(rs.getUntilRegex()).matcher(line).find();
        }
        return false;
    }

    private static String rowToLine(Row row) {
        StringBuilder sb = new StringBuilder();
        for (int c = 0; c < row.getLastCellNum(); c++) {
            String s = Optional.ofNullable(getCellString(row.getCell(c))).orElse("");
            sb.append(s).append(" ");
        }
        return sb.toString().trim();
    }

    // === Flexible Excel reading: respect merged regions AND avoid neighbors owned by others (including unmapped headers)
    //     When we encounter another header's value band while offsetting, we stop scanning further in that direction. ===
    private static String readExcelFlexible(Sheet sheet, Row row, Map<String,Integer> idx, String fieldKey,
                                            ColumnKind kind, ParserConfig.FileTypeConfig cfg, ExcelContext ctx) {
        if (row == null) return null;
        Integer col = idx.get(fieldKey);
        if (col == null) return null;

        int r = row.getRowNum();

        // 1) try direct or merged top-left at (r, col)
        String val = readCellOrMergedTopLeft(sheet, r, col);
        if (isAcceptable(val, kind, cfg)) return val;

        // 2a) scan RIGHT; stop entirely if we hit another header's value region
        for (int c = col + 1; c <= col + EXCEL_MERGE_NEIGHBOR_SCAN; c++) {
            if (belongsToAnotherFieldRegion(sheet, r, idx, fieldKey, c, ctx)) {
                break; // STOP scanning right beyond another column's value band
            }
            String v = readCellOrMergedTopLeft(sheet, r, c);
            if (isAcceptable(v, kind, cfg)) return v;
        }

        // 2b) scan LEFT; stop entirely if we hit another header's value region
        for (int c = col - 1; c >= Math.max(0, col - EXCEL_MERGE_NEIGHBOR_SCAN); c--) {
            if (belongsToAnotherFieldRegion(sheet, r, idx, fieldKey, c, ctx)) {
                break; // STOP scanning left beyond another column's value band
            }
            String v = readCellOrMergedTopLeft(sheet, r, c);
            if (isAcceptable(v, kind, cfg)) return v;
        }

        return null;
    }

    /**
     * Guard: do not take a neighbor cell if it is:
     *  (A) exactly another field's mapped header column, OR
     *  (B) inside a merged region whose column span contains the header column of some OTHER mapped field, OR
     *  (C) has a header text (from header band) that does NOT match the current field's synonyms
     *      -> this blocks unmapped-but-real columns like "Instrument Id".
     *  NOTE: The caller stops scanning further in that direction when this returns true.
     */
    private static boolean belongsToAnotherFieldRegion(Sheet sheet, int rowIndex,
                                                       Map<String,Integer> idx,
                                                       String currentField,
                                                       int probeCol,
                                                       ExcelContext ctx) {
        if (probeCol < 0) return true; // out of bounds -> treat as forbidden

        // C) Header text ownership from header band (unmapped headers like "Instrument Id")
        if (ctx != null && ctx.headerByCol != null && probeCol < ctx.headerByCol.size()) {
            String headerText = norm(ctx.headerByCol.get(probeCol));
            if (!headerText.isBlank()) {
                if (!headerMatchesField(headerText, currentField, ctx.expectSynonyms)) {
                    return true;
                }
            }
        }

        // A) & B) legacy mapped-field guard
        for (Map.Entry<String,Integer> e : idx.entrySet()) {
            String otherField = e.getKey();
            Integer otherHeaderCol = e.getValue();
            if (otherHeaderCol == null) continue;
            if (otherField.equals(currentField)) continue;

            // exact same column as another field’s mapped header -> don't use
            if (otherHeaderCol == probeCol) {
                return true;
            }

            // If the probed cell sits in a merged region, and that region spans
            // the header column of another field, then this probed cell belongs
            // to that other field's value block -> don't use.
            CellRangeAddress region = findMergedRegion(sheet, rowIndex, probeCol);
            if (region != null) {
                if (otherHeaderCol >= region.getFirstColumn() && otherHeaderCol <= region.getLastColumn()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean headerMatchesField(String headerNorm, String fieldKey, Map<String, List<String>> expect) {
        if (expect == null) return false;
        List<String> syns = expect.get(fieldKey);
        if (syns == null || syns.isEmpty()) return false;
        for (String s : syns) {
            if (headerMatch(headerNorm, norm(s))) {
                return true;
            }
        }
        return false;
    }

    private static String readCellOrMergedTopLeft(Sheet sheet, int rowIndex, int colIndex) {
        if (colIndex < 0) return null;
        Row r = sheet.getRow(rowIndex);
        if (r != null) {
            Cell direct = r.getCell(colIndex);
            String val = getCellString(direct);
            if (val != null && !val.trim().isEmpty()) return val.trim();
        }

        CellRangeAddress region = findMergedRegion(sheet, rowIndex, colIndex);
        if (region != null) {
            Row topRow = sheet.getRow(region.getFirstRow());
            if (topRow != null) {
                Cell topLeft = topRow.getCell(region.getFirstColumn());
                String mergedVal = getCellString(topLeft);
                if (mergedVal != null && !mergedVal.trim().isEmpty()) {
                    return mergedVal.trim();
                }
            }
        }
        return null;
    }

    private static CellRangeAddress findMergedRegion(Sheet sheet, int rowIndex, int colIndex) {
        List<CellRangeAddress> regions = sheet.getMergedRegions();
        for (CellRangeAddress cra : regions) {
            if (cra.isInRange(rowIndex, colIndex)) {
                return cra;
            }
        }
        return null;
    }

    private static boolean isAcceptable(
            String val,
            ColumnKind kind,
            com.paytrix.cipherbank.infrastructure.config.parser.ParserConfig.FileTypeConfig cfg
    ) {
        if (val == null || val.isBlank()) return false;

        switch (kind) {
            case CREDIT, DEBIT, AMOUNT, BALANCE:
                return readDecimal(val, cfg) != null;
            default:
                return true; // DATE/TIME/REFERENCE/OTHER
        }
    }

    private static String getCellString(Cell cell) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(cell)) {
                // returns the formatted wall-clock LocalDateTime from Excel cell (no UTC conversion)
                return cell.getLocalDateTimeCellValue().toString();
            }
            double d = cell.getNumericCellValue();
            return BigDecimal.valueOf(d).toPlainString();
        }
        if (cell.getCellType() == CellType.FORMULA) {
            try { return getCellString(evaluateFormula(cell)); }
            catch (Exception e) { return cell.getStringCellValue(); }
        }
        return cell.getStringCellValue();
    }

    private static Cell evaluateFormula(Cell cell) {
        return cell; // simplified; a real evaluator can be wired if needed
    }

    // ================= PDF =================
    private static List<ParsedRow> parsePdf(InputStream in, ParserConfig.PdfConfig cfg) throws IOException {
        var rows = new ArrayList<ParsedRow>();
        try (PDDocument doc = PDDocument.load(in)) {
            var stripper = new PDFTextStripper();
            String text = stripper.getText(doc);

            String body = text;
            if (cfg.getPdfTable().getStartAfterRegex() != null) {
                var m = Pattern.compile(cfg.getPdfTable().getStartAfterRegex(), Pattern.MULTILINE).matcher(text);
                if (m.find()) body = text.substring(m.end());
            }
            if (cfg.getPdfTable().getStopBeforeRegex() != null) {
                var m = Pattern.compile(cfg.getPdfTable().getStopBeforeRegex(), Pattern.MULTILINE).matcher(body);
                if (m.find()) body = body.substring(0, m.start());
            }

            Pattern line = Pattern.compile(cfg.getPdfTable().getLinePattern());
            var scanner = new Scanner(new StringReader(body));
            while (scanner.hasNextLine()) {
                String ln = scanner.nextLine().trim();
                var m = line.matcher(ln);
                if (!m.matches()) continue;

                String date = group(m, "date");
                String ref  = group(m, "ref");
                String credit = group(m, "credit");
                String debit = group(m, "debit");
                String amount = group(m, "amount");
                String balance = group(m, "balance");

                LocalDateTime dt = parseDateTime(date, null, cfg.getDateParse());
                BigDecimal amt = deriveAmount(amount, credit, debit, cfg);
                BigDecimal bal = readDecimal(balance, cfg);

                var parsedRef = parseReference(ref, cfg.getReference());
                boolean payIn = computePayIn(amt, parsedRef.orderId, parsedRef.utr, ref, cfg.getPayInRule());

                rows.add(new ParsedRow(dt, amt, bal, ref, parsedRef.orderId, parsedRef.utr, payIn));
            }
        }
        return rows;
    }

    private static String group(java.util.regex.Matcher m, String name) {
        try { return m.group(name); } catch (Exception e) { return null; }
    }

    // ================= Parse & numeric helpers =================
    private record ParsedRef(String orderId, String utr) {}

    private static ParsedRef parseReference(String reference, ParserConfig.Reference cfg) {
        if (reference == null) reference = "";
        String orderId = null;
        String utr = null;

        String[] parts = (cfg.getSplitter() != null && !cfg.getSplitter().isBlank())
                ? reference.split(Pattern.quote(cfg.getSplitter()))
                : new String[]{reference};

        boolean partsOk = true;
        if (cfg.getPartsCount() != null && cfg.getPartsCount().getMode() != null) {
            List<Integer> vals = cfg.getPartsCount().getValues();
            if ("exact".equalsIgnoreCase(cfg.getPartsCount().getMode())) {
                partsOk = !vals.isEmpty() && parts.length == vals.get(0);
            } else if ("oneOf".equalsIgnoreCase(cfg.getPartsCount().getMode())) {
                partsOk = vals != null && vals.contains(parts.length);
            }
        }
        if (partsOk) {
            if (cfg.getOrderId() != null && cfg.getOrderId().getIndex() != null &&
                    cfg.getOrderId().getIndex() < parts.length) {
                orderId = clean(parts[cfg.getOrderId().getIndex()], cfg.getOrderId());
            }
            if (cfg.getUtr() != null && cfg.getUtr().getIndex() != null &&
                    cfg.getUtr().getIndex() < parts.length) {
                utr = clean(parts[cfg.getUtr().getIndex()], cfg.getUtr());
            }
        }
        if ((utr == null || utr.isBlank()) && cfg.getUtrFallback() != null) {
            var m = Pattern.compile(cfg.getUtrFallback().getRegex()).matcher(reference);
            if (m.find()) utr = m.group(0);
        }
        return new ParsedRef(orderId, utr);
    }

    private static String clean(String s, ParserConfig.IndexDef def) {
        if (s == null) return null;
        String x = s.replace('\u00A0', ' ').trim();
        if (Boolean.TRUE.equals(def.getCleanDigitsOnly())) x = x.replaceAll("\\D", "");
        return x;
    }

    private static BigDecimal deriveAmount(String amount, String credit, String debit, ParserConfig.FileTypeConfig cfg) {
        if (credit != null || debit != null) {
            BigDecimal cr = readDecimal(credit, cfg);
            BigDecimal dr = readDecimal(debit, cfg);
            return (cr != null ? cr : BigDecimal.ZERO).subtract(dr != null ? dr : BigDecimal.ZERO);
        }
        return readDecimal(amount, cfg);
    }

    private static BigDecimal deriveAmount(String amount, String credit, String debit, ParserConfig.PdfConfig cfg) {
        if (credit != null || debit != null) {
            BigDecimal cr = readDecimal(credit, cfg);
            BigDecimal dr = readDecimal(debit, cfg);
            return (cr != null ? cr : BigDecimal.ZERO).subtract(dr != null ? dr : BigDecimal.ZERO);
        }
        return readDecimal(amount, cfg);
    }

    private static BigDecimal readDecimal(String raw, Object cfg) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.replace("\u00A0", " ").trim();
        boolean negParen = s.contains("(") && s.contains(")"); // handle (1,234.56)
        String thousands;
        String decimal;
        if (cfg instanceof ParserConfig.FileTypeConfig f && f.getNumeric() != null) {
            thousands = Optional.ofNullable(f.getNumeric().getThousandsSeparator()).orElse(",");
            decimal = Optional.ofNullable(f.getNumeric().getDecimalSeparator()).orElse(".");
        } else if (cfg instanceof ParserConfig.PdfConfig p && p.getNumeric() != null) {
            thousands = Optional.ofNullable(p.getNumeric().getThousandsSeparator()).orElse(",");
            decimal = Optional.ofNullable(p.getNumeric().getDecimalSeparator()).orElse(".");
        } else {
            thousands = ",";
            decimal = ".";
        }
        if (!thousands.isEmpty()) s = s.replace(thousands, "");
        if (!".".equals(decimal)) s = s.replace(decimal, ".");
        s = s.replaceAll("[^0-9\\.-]", "");
        if (s.isBlank() || "-".equals(s)) return null;
        BigDecimal val = new BigDecimal(s);
        return negParen ? val.negate() : val;
    }

    // ---------- CHANGED: return LocalDateTime, never Instant ----------
    private static LocalDateTime parseDateTime(String date, String time, ParserConfig.FileTypeConfig cfg) {
        if (cfg.getDateParse() == null) return null;
        return parseDateTime(date, time, cfg.getDateParse());
    }

    private static LocalDateTime parseDateTime(String date, String time, ParserConfig.DateParse cfg) {
        if (cfg == null) return null;

        // 1) Excel serial numbers
        if ("excelSerial".equalsIgnoreCase(cfg.getInput())) {
            try {
                // If a clean double, interpret via POI then KEEP as LocalDateTime
                double serial = Double.parseDouble(date.trim());
                // Use POI utility to get java.util.Date, then convert to LocalDateTime in system default
                LocalDateTime fromSerial = LocalDateTime.ofInstant(
                        DateUtil.getJavaDate(serial).toInstant(), ZoneId.systemDefault());
                // If a time string is provided separately, prefer that; otherwise keep serial's time
                if (time != null && !time.isBlank()) {
                    LocalTime lt = parseTimeFlexible(time, cfg.getTimeFormat());
                    return LocalDateTime.of(fromSerial.toLocalDate(), lt);
                }
                return fromSerial;
            } catch (Exception ignore) {
                // fall through to other strategies
            }
        }

        // 2) ISO forms coming from getCellString() of a DATE cell (e.g., "2025-10-16T00:00")
        LocalDateTime iso = tryParseIsoLocalDateTime(date);
        if (iso != null) {
            if (time != null && !time.isBlank()) {
                LocalTime lt = parseTimeFlexible(time, cfg.getTimeFormat());
                return LocalDateTime.of(iso.toLocalDate(), lt);
            }
            return iso;
        }
        LocalDate isoD = tryParseIsoLocalDate(date);
        if (isoD != null) {
            LocalTime lt = (time != null && !time.isBlank())
                    ? parseTimeFlexible(time, cfg.getTimeFormat())
                    : LocalTime.MIDNIGHT;
            return LocalDateTime.of(isoD, lt);
        }

        // 3) Explicit formatting
        String dfmt = Optional.ofNullable(cfg.getFormat()).orElse("dd/MM/yyyy");
        LocalDate d = (date == null || date.isBlank())
                ? LocalDate.now()
                : LocalDate.parse(date.trim(), DateTimeFormatter.ofPattern(dfmt));

        LocalTime t = LocalTime.MIDNIGHT;
        if (time != null && !time.isBlank()) {
            t = parseTimeFlexible(time, cfg.getTimeFormat());
        }
        return LocalDateTime.of(d, t);
    }

    private static LocalDateTime tryParseIsoLocalDateTime(String s) {
        try {
            if (s != null && s.contains("T")) {
                return LocalDateTime.parse(s.trim(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            }
        } catch (Exception ignored) { }
        return null;
    }

    private static LocalDate tryParseIsoLocalDate(String s) {
        try {
            if (s != null && !s.isBlank() && !s.contains("T")) {
                return LocalDate.parse(s.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
            }
        } catch (Exception ignored) { }
        return null;
    }

    private static LocalTime parseTimeFlexible(String time, String configuredFmt) {
        String tfmt = Optional.ofNullable(configuredFmt).orElse("HH:mm:ss");
        try {
            return LocalTime.parse(time.trim(), DateTimeFormatter.ofPattern(tfmt));
        } catch (Exception e) {
            // fallback: try common shorter formats
            for (String alt : new String[]{"HH:mm", "H:mm", "HHmm", "h:mm a"}) {
                try {
                    return LocalTime.parse(time.trim(), DateTimeFormatter.ofPattern(alt));
                } catch (Exception ignored) {}
            }
            return LocalTime.MIDNIGHT;
        }
    }
    // -------------------------------------------------------------

    private static String norm(String s) {
        if (s == null) return "";
        return s.replace("\u00A0", " ").trim().toLowerCase().replaceAll("\\s+", " ");
    }

    private static boolean headerMatch(String col, String syn) {
        return col.equalsIgnoreCase(syn);
    }

    private static boolean computePayIn(
            java.math.BigDecimal amount,
            String orderId,
            String utr,
            String reference,
            com.paytrix.cipherbank.infrastructure.config.parser.ParserConfig.PayInRule rule
    ) {
        if (amount == null) return false;
        if (rule == null || rule.getType() == null) {
            return amount.compareTo(java.math.BigDecimal.ZERO) > 0;
        }

        String type = rule.getType();
        switch (type) {
            case "amountPositive":
            case "creditColumn":
                return amount.compareTo(java.math.BigDecimal.ZERO) > 0;
            case "orderIdNoSpace":
                return amount.compareTo(java.math.BigDecimal.ZERO) > 0
                        && (orderId == null || !orderId.contains(" "));
            case "utrNoSpace":
                return amount.compareTo(java.math.BigDecimal.ZERO) > 0
                        && (utr == null || !utr.contains(" "));
            case "narrationContains":
                if (rule.getNarrationContainsAny() == null || rule.getNarrationContainsAny().isEmpty()) {
                    return false;
                }
                String ref = reference == null ? "" : reference.toLowerCase();
                for (String needle : rule.getNarrationContainsAny()) {
                    if (needle != null && !needle.isBlank() && ref.contains(needle.toLowerCase())) {
                        return true;
                    }
                }
                return false;
            default:
                return amount.compareTo(java.math.BigDecimal.ZERO) > 0;
        }
    }
}
