package org.waypoints.next.deeds;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Strict, bounded JSON/CSV parsing with a narrowly allowed Sklotopolis wrapper. */
public final class DeedFeedParser {
    public static final int MAXIMUM_BYTES = 2_000_000;
    public static final int MAXIMUM_DEEDS = 10_000;
    private static final Pattern SKLOTOPOLIS_WRAPPER = Pattern.compile(
            "\\A(?:var\\s+deeds\\s*;\\s*)?deeds\\s*=\\s*(\\[.*])\\s*;?\\s*\\z",
            Pattern.DOTALL);

    private DeedFeedParser() { }

    public static List<DeedRecord> parse(byte[] bytes, DeedFeedFormat format,
                                         int mapWidth, int mapHeight) {
        if (bytes == null || bytes.length < 1 || bytes.length > MAXIMUM_BYTES) {
            throw new IllegalArgumentException("deed feed is empty or oversized");
        }
        return parse(new String(bytes, StandardCharsets.UTF_8), format,
                mapWidth, mapHeight);
    }

    public static List<DeedRecord> parse(String source, DeedFeedFormat format,
                                         int mapWidth, int mapHeight) {
        if (source == null || source.isEmpty()
                || source.getBytes(StandardCharsets.UTF_8).length > MAXIMUM_BYTES) {
            throw new IllegalArgumentException("deed feed is empty or oversized");
        }
        if (format == null) throw new IllegalArgumentException("feed format is required");
        if (mapWidth < 1 || mapHeight < 1) throw new IllegalArgumentException(
                "map bounds must be positive");
        String clean = stripBom(source).trim();
        if (format == DeedFeedFormat.SKLOTOPOLIS_JSON) {
            Matcher wrapper = SKLOTOPOLIS_WRAPPER.matcher(clean);
            if (wrapper.matches()) clean = wrapper.group(1);
            else if (!clean.startsWith("[")) throw new IllegalArgumentException(
                    "unexpected Sklotopolis deed wrapper");
        }
        List<Map<String, Object>> values = format == DeedFeedFormat.CSV
                ? csv(clean) : json(clean);
        List<DeedRecord> result = new ArrayList<DeedRecord>();
        Set<String> keys = new LinkedHashSet<String>();
        for (Map<String, Object> value : values) {
            if (result.size() >= MAXIMUM_DEEDS) throw new IllegalArgumentException(
                    "too many deeds");
            DeedRecord record = record(value, mapWidth, mapHeight);
            String folded = record.getStableKey().toLowerCase(Locale.ENGLISH);
            if (!keys.add(folded)) throw new IllegalArgumentException(
                    "duplicate deed stable key: " + record.getStableKey());
            result.add(record);
        }
        if (result.isEmpty()) throw new IllegalArgumentException(
                "deed feed contains no records");
        return Collections.unmodifiableList(result);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> json(String source) {
        Object root = new Json(source).parse();
        Object rows = root;
        if (root instanceof Map) rows = find((Map<String, Object>) root,
                "deeds", "settlements", "records");
        if (!(rows instanceof List)) throw new IllegalArgumentException(
                "JSON deed feed must be an array or contain a deeds array");
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Object value : (List<Object>) rows) {
            if (!(value instanceof Map)) throw new IllegalArgumentException(
                    "every deed must be a JSON object");
            result.add((Map<String, Object>) value);
        }
        return result;
    }

    private static DeedRecord record(Map<String, Object> fields,
                                     int mapWidth, int mapHeight) {
        String name = string(find(fields, "name", "deedName", "settlement"));
        String stable = string(find(fields, "id", "deedId", "stableKey", "key", "tag"));
        if (stable.isEmpty()) stable = DeedRecord.nameKey(name);
        int x = integer(find(fields, "x", "tileX", "centerX"), "X");
        int y = integer(find(fields, "y", "tileY", "centerY"), "Y");
        if (x < 0 || x >= mapWidth || y < 0 || y >= mapHeight) {
            throw new IllegalArgumentException("deed coordinates are outside "
                    + mapWidth + "x" + mapHeight + ": " + x + "," + y);
        }
        LinkedHashMap<String, String> metadata = new LinkedHashMap<String, String>();
        for (Map.Entry<String, Object> field : fields.entrySet()) {
            Object value = field.getValue();
            if (value == null || value instanceof String
                    || value instanceof Number || value instanceof Boolean) {
                metadata.put(field.getKey(), value == null ? "" : String.valueOf(value));
            }
        }
        return new DeedRecord(stable, name, x, y, metadata);
    }

    private static Object find(Map<String, Object> fields, String... names) {
        for (String name : names) {
            if (fields.containsKey(name)) return fields.get(name);
            for (Map.Entry<String, Object> field : fields.entrySet()) {
                if (normalize(field.getKey()).equals(normalize(name))) {
                    return field.getValue();
                }
            }
        }
        return null;
    }

    private static String string(Object value) {
        if (value == null) return "";
        if (!(value instanceof String) && !(value instanceof Number)) {
            throw new IllegalArgumentException("deed text field has invalid type");
        }
        return String.valueOf(value).trim();
    }

    private static int integer(Object value, String label) {
        try {
            if (value instanceof BigDecimal) return ((BigDecimal) value).intValueExact();
            if (value instanceof Number) return new BigDecimal(
                    value.toString()).intValueExact();
            if (value instanceof String) return new BigDecimal(
                    ((String) value).trim()).intValueExact();
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("invalid deed " + label + " coordinate", invalid);
        }
        throw new IllegalArgumentException("missing deed " + label + " coordinate");
    }

    private static List<Map<String, Object>> csv(String source) {
        List<List<String>> rows = csvRows(source);
        if (rows.size() < 2) throw new IllegalArgumentException(
                "CSV deed feed requires a header and at least one record");
        List<String> header = rows.get(0);
        Set<String> unique = new LinkedHashSet<String>();
        for (String value : header) {
            String key = value.trim();
            if (key.isEmpty() || !unique.add(normalize(key))) {
                throw new IllegalArgumentException("CSV headers must be unique and non-empty");
            }
        }
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (int row = 1; row < rows.size(); row++) {
            List<String> values = rows.get(row);
            if (values.size() == 1 && values.get(0).trim().isEmpty()) continue;
            if (values.size() != header.size()) throw new IllegalArgumentException(
                    "CSV row " + (row + 1) + " has " + values.size()
                            + " fields; expected " + header.size());
            LinkedHashMap<String, Object> fields = new LinkedHashMap<String, Object>();
            for (int column = 0; column < header.size(); column++) {
                fields.put(header.get(column).trim(), values.get(column).trim());
            }
            result.add(fields);
        }
        return result;
    }

    private static List<List<String>> csvRows(String source) {
        List<List<String>> rows = new ArrayList<List<String>>();
        List<String> row = new ArrayList<String>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < source.length(); i++) {
            char ch = source.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < source.length() && source.charAt(i + 1) == '"') {
                        field.append('"'); i++;
                    } else quoted = false;
                } else field.append(ch);
            } else if (ch == '"') {
                if (field.length() != 0) throw new IllegalArgumentException(
                        "CSV quote must start a field");
                quoted = true;
            } else if (ch == ',') {
                row.add(field.toString()); field.setLength(0);
            } else if (ch == '\n' || ch == '\r') {
                if (ch == '\r' && i + 1 < source.length()
                        && source.charAt(i + 1) == '\n') i++;
                row.add(field.toString()); field.setLength(0);
                rows.add(row); row = new ArrayList<String>();
            } else field.append(ch);
            if (field.length() > 100_000) throw new IllegalArgumentException(
                    "CSV deed field is oversized");
        }
        if (quoted) throw new IllegalArgumentException("unterminated CSV quoted field");
        if (field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString()); rows.add(row);
        }
        return rows;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ENGLISH)
                .replace("_", "").replace("-", "").replace(" ", "");
    }

    private static String stripBom(String value) {
        return !value.isEmpty() && value.charAt(0) == '\ufeff'
                ? value.substring(1) : value;
    }

    /** Minimal strict RFC-8259 reader; downloaded content is data, never script. */
    private static final class Json {
        private final String text;
        private int at;

        Json(String text) { this.text = text; }

        Object parse() {
            Object value = value(0);
            whitespace();
            if (at != text.length()) fail("trailing JSON content");
            return value;
        }

        private Object value(int depth) {
            if (depth > 24) fail("JSON nesting is too deep");
            whitespace();
            if (at >= text.length()) fail("unexpected end of JSON");
            char ch = text.charAt(at);
            if (ch == '{') return object(depth + 1);
            if (ch == '[') return array(depth + 1);
            if (ch == '"') return string();
            if (ch == 't') { literal("true"); return Boolean.TRUE; }
            if (ch == 'f') { literal("false"); return Boolean.FALSE; }
            if (ch == 'n') { literal("null"); return null; }
            return number();
        }

        private Map<String, Object> object(int depth) {
            LinkedHashMap<String, Object> result = new LinkedHashMap<String, Object>();
            at++; whitespace();
            if (take('}')) return result;
            do {
                whitespace();
                if (at >= text.length() || text.charAt(at) != '"') fail("object key expected");
                String key = string();
                whitespace(); expect(':');
                if (result.containsKey(key)) fail("duplicate object key: " + key);
                result.put(key, value(depth));
                whitespace();
            } while (take(','));
            expect('}');
            return result;
        }

        private List<Object> array(int depth) {
            List<Object> result = new ArrayList<Object>();
            at++; whitespace();
            if (take(']')) return result;
            do {
                if (result.size() >= MAXIMUM_DEEDS + 1) fail("JSON array is too large");
                result.add(value(depth)); whitespace();
            } while (take(','));
            expect(']');
            return result;
        }

        private String string() {
            expect('"');
            StringBuilder result = new StringBuilder();
            while (at < text.length()) {
                char ch = text.charAt(at++);
                if (ch == '"') return result.toString();
                if (ch < 0x20) fail("control character in JSON string");
                if (ch != '\\') result.append(ch);
                else {
                    if (at >= text.length()) fail("unterminated JSON escape");
                    char escaped = text.charAt(at++);
                    if (escaped == '"' || escaped == '\\' || escaped == '/') result.append(escaped);
                    else if (escaped == 'b') result.append('\b');
                    else if (escaped == 'f') result.append('\f');
                    else if (escaped == 'n') result.append('\n');
                    else if (escaped == 'r') result.append('\r');
                    else if (escaped == 't') result.append('\t');
                    else if (escaped == 'u') result.append(unicode());
                    else fail("invalid JSON escape");
                }
                if (result.length() > 100_000) fail("JSON string is oversized");
            }
            fail("unterminated JSON string");
            return "";
        }

        private char unicode() {
            if (at + 4 > text.length()) fail("incomplete unicode escape");
            try {
                char result = (char) Integer.parseInt(text.substring(at, at + 4), 16);
                at += 4;
                return result;
            } catch (NumberFormatException invalid) {
                fail("invalid unicode escape"); return 0;
            }
        }

        private BigDecimal number() {
            int start = at;
            if (take('-')) { }
            if (take('0')) { }
            else digits(true);
            if (take('.')) digits(true);
            if (at < text.length() && (text.charAt(at) == 'e' || text.charAt(at) == 'E')) {
                at++; if (at < text.length() && (text.charAt(at) == '+'
                        || text.charAt(at) == '-')) at++;
                digits(true);
            }
            try { return new BigDecimal(text.substring(start, at)); }
            catch (NumberFormatException invalid) { fail("invalid JSON number"); return null; }
        }

        private void digits(boolean required) {
            int start = at;
            while (at < text.length() && Character.isDigit(text.charAt(at))) at++;
            if (required && start == at) fail("digit expected");
        }

        private void literal(String expected) {
            if (!text.regionMatches(at, expected, 0, expected.length())) fail(
                    "invalid JSON literal");
            at += expected.length();
        }

        private void whitespace() {
            while (at < text.length()) {
                char ch = text.charAt(at);
                if (ch != ' ' && ch != '\t' && ch != '\r' && ch != '\n') break;
                at++;
            }
        }

        private boolean take(char expected) {
            if (at < text.length() && text.charAt(at) == expected) { at++; return true; }
            return false;
        }

        private void expect(char expected) {
            if (!take(expected)) fail("expected '" + expected + "'");
        }

        private void fail(String message) {
            throw new IllegalArgumentException(message + " at character " + at);
        }
    }
}
