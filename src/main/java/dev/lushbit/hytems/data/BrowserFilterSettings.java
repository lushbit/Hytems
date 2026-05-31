package dev.lushbit.hytems.data;

public class BrowserFilterSettings {
    public static final String ALL = "all";
    public static final String SORT_AZ = "a-z";

    public String category = ALL;
    public String mod = ALL;
    public String craftable = ALL;
    public String droppable = ALL;
    public String pinned = ALL;
    public String sorting = SORT_AZ;
    public boolean showSalvagerRecipes;
    public boolean showHiddenItems;

    public BrowserFilterSettings copy() {
        BrowserFilterSettings copy = new BrowserFilterSettings();
        copy.category = normalize(category, ALL);
        copy.mod = normalize(mod, ALL);
        copy.craftable = normalize(craftable, ALL);
        copy.droppable = normalize(droppable, ALL);
        copy.pinned = normalize(pinned, ALL);
        copy.sorting = normalize(sorting, SORT_AZ);
        copy.showSalvagerRecipes = showSalvagerRecipes;
        copy.showHiddenItems = showHiddenItems;
        return copy;
    }

    public void resetFilters() {
        category = ALL;
        mod = ALL;
        craftable = ALL;
        droppable = ALL;
        pinned = ALL;
        sorting = SORT_AZ;
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }
}
