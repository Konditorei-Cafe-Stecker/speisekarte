package com.stecker.backend.synthetic;

import com.stecker.backend.model.MenuItem;

import java.util.Map;

/**
 * Groups menu products by how guests use them, so the simulation can say
 * "a hot drink" or "an ice cream" instead of naming single products.
 * Based on the subcategories of the imported menu.
 */
public enum ProductGroup {
    HOT_DRINK,
    COLD_DRINK,
    ALCOHOL,
    BAR_SNACK,
    BREAKFAST,
    SAVOURY,
    ICE_DESSERT,
    /** Add-ons such as "Portion Sahne"; never chosen on their own. */
    OTHER;

    private static final Map<String, ProductGroup> BY_SUBCATEGORY = Map.ofEntries(
            Map.entry("Azul Kaffee", HOT_DRINK),
            Map.entry("Schokolade", HOT_DRINK),
            Map.entry("Zum Aufwärmen", HOT_DRINK),
            Map.entry("Tee (Glas)", HOT_DRINK),
            Map.entry("Tee auf Stövchen traditionell", HOT_DRINK),
            Map.entry("Alkoholfreie Getränke", COLD_DRINK),
            Map.entry("Fruchtsäfte", COLD_DRINK),
            Map.entry("Biere", ALCOHOL),
            Map.entry("Weine", ALCOHOL),
            Map.entry("Sekt & Prosecco", ALCOHOL),
            Map.entry("Longdrinks", ALCOHOL),
            Map.entry("Unsere Empfehlungen zu Bier & Wein", BAR_SNACK),
            Map.entry("Frühstück", BREAKFAST),
            Map.entry("Belegte Brötchen", BREAKFAST),
            Map.entry("Kleine, herzhafte Leckereien", SAVOURY),
            Map.entry("Klassisches", SAVOURY));

    public static ProductGroup of(MenuItem item) {
        if ("Portion Sahne".equals(item.getName())) {
            return OTHER;
        }
        if (item.getSubcategory() != null) {
            ProductGroup group = BY_SUBCATEGORY.get(item.getSubcategory());
            if (group != null) {
                return group;
            }
        }
        return switch (item.getCategory()) {
            case "Eis & Desserts" -> ICE_DESSERT;
            case "Getränke" -> COLD_DRINK;
            case "Speisen" -> SAVOURY;
            default -> OTHER;
        };
    }
}
