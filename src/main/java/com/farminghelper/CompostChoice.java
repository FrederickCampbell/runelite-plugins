package com.farminghelper;

import net.runelite.api.gameval.ItemID;

/** Explicit compost preference; a filled bottomless bucket's contents cannot
 * be inferred from its item ID, so the player checks its type and charges. */
enum CompostChoice
{
    NONE("None", -1), REGULAR("Compost", ItemID.BUCKET_COMPOST),
    SUPER("Supercompost", ItemID.BUCKET_SUPERCOMPOST),
    ULTRA("Ultracompost", ItemID.BUCKET_ULTRACOMPOST),
    BOTTOMLESS("Bottomless bucket", ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED);
    final String label;
    final int item;
    CompostChoice(String label, int item) { this.label = label; this.item = item; }
    int resolve(boolean filledBucketAvailable)
    {
        return this == BOTTOMLESS && !filledBucketAvailable ? ItemID.BUCKET_ULTRACOMPOST : item;
    }
    static CompostChoice load(String value, boolean oldEnabled)
    {
        try { return valueOf(value); }
        catch (IllegalArgumentException | NullPointerException e) { return oldEnabled ? BOTTOMLESS : NONE; }
    }
    static boolean isOrdinary(int id)
    {
        return id == ItemID.BUCKET_COMPOST || id == ItemID.BUCKET_SUPERCOMPOST || id == ItemID.BUCKET_ULTRACOMPOST;
    }
    @Override public String toString() { return label; }
}
