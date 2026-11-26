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
    // Shared Tool leprechaun storage, read by the game's farming-tools exchange.
    static final int LEPRECHAUN_ULTRACOMPOST=5732;
    static int localItem(int planned,java.util.Map<Integer,Integer> bag,int storedUltra)
    {
        if(planned==ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED&&
            bag.getOrDefault(planned,0)==0&&
            (bag.getOrDefault(ItemID.BUCKET_ULTRACOMPOST,0)>0||storedUltra>0))
            return ItemID.BUCKET_ULTRACOMPOST;
        return planned;
    }
    static boolean withdrawUltra(int item,java.util.Map<Integer,Integer> bag,int storedUltra)
    {
        return item==ItemID.BUCKET_ULTRACOMPOST&&storedUltra>0&&
            bag.getOrDefault(ItemID.BUCKET_ULTRACOMPOST,0)==0&&
            bag.getOrDefault(ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED,0)==0;
    }
    static boolean isOrdinary(int id)
    {
        return id == ItemID.BUCKET_COMPOST || id == ItemID.BUCKET_SUPERCOMPOST || id == ItemID.BUCKET_ULTRACOMPOST;
    }
    @Override public String toString() { return label; }
}
