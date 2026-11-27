package com.waylo.hotelservice.automation;

/**
 * Result of one generator run. The first five fields are the contract record (rows, inventory and rates together).
 * {@code skippedManual} counts nights (room type + date) that hold at least one MANUAL row the run left alone.
 * The last three fields split the totals so the web can say "N gece stok, M fiyat olusturuldu".
 */
public record AutomationSummary(
        int created,
        int updated,
        int skippedExisting,
        int skippedManual,
        int skippedPast,
        int inventoryCreated,
        int ratesCreated,
        int skippedUnpriced
) {
    public static final AutomationSummary EMPTY = new AutomationSummary(0, 0, 0, 0, 0, 0, 0, 0);

    public AutomationSummary plus(AutomationSummary o) {
        return new AutomationSummary(created + o.created, updated + o.updated,
                skippedExisting + o.skippedExisting, skippedManual + o.skippedManual,
                skippedPast + o.skippedPast, inventoryCreated + o.inventoryCreated,
                ratesCreated + o.ratesCreated, skippedUnpriced + o.skippedUnpriced);
    }
}
