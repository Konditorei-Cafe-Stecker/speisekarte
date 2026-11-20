package com.stecker.backend.synthetic;

import com.stecker.backend.model.MenuItem;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the real 107-product menu from the committed import SQL. */
public final class TestMenu {

    private static final Pattern ROW = Pattern.compile(
            "\\((\\d+), '((?:[^']|'')*)', '((?:[^']|'')*)', '((?:[^']|'')*)',\\s*"
                    + "(NULL|'(?:[^']|'')*'), (NULL|'(?:[^']|'')*'), ([\\d.]+),");

    private TestMenu() {
    }

    /** Products without database IDs, ready to be saved. */
    public static List<MenuItem> load() {
        String sql;
        try {
            sql = Files.readString(
                    Path.of("src/main/resources/db/stecker_menu_import.sql"),
                    StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException(error);
        }

        List<MenuItem> items = new ArrayList<>();
        Matcher matcher = ROW.matcher(sql);
        while (matcher.find()) {
            MenuItem item = new MenuItem(
                    null,
                    text(matcher.group(4)),
                    nullable(matcher.group(5)),
                    new BigDecimal(matcher.group(7)),
                    text(matcher.group(2)),
                    true);
            ReflectionTestUtils.setField(item, "subcategory", text(matcher.group(3)));
            ReflectionTestUtils.setField(item, "sizeVolume", nullable(matcher.group(6)));
            items.add(item);
        }
        return items;
    }

    /** Same products with IDs 1..n, for tests without a database. */
    public static List<MenuItem> loadWithIds() {
        List<MenuItem> items = load();
        for (int i = 0; i < items.size(); i++) {
            ReflectionTestUtils.setField(items.get(i), "id", (long) i + 1);
        }
        return items;
    }

    private static String text(String value) {
        return value.replace("''", "'");
    }

    private static String nullable(String value) {
        return "NULL".equals(value) ? null : text(value.substring(1, value.length() - 1));
    }
}
