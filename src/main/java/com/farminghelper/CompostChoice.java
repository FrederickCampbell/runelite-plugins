package com.farminghelper;

import java.util.Map;
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
    // Keep explicit regular/super/none settings. Ultra and bottomless share
    // the same supply alternatives; the item ID cannot prove bucket contents.
    int plan(Map<Integer,Integer> bag,Map<Integer,Integer> stock,int storedUltra)
    {
        if(this!=ULTRA&&this!=BOTTOMLESS)return item;
        int bucket=BOTTOMLESS.item;
        if(bag.getOrDefault(bucket,0)>0)return bucket;
        if(storedUltra>0||bag.getOrDefault(ULTRA.item,0)>0)return ULTRA.item;
        return stock.getOrDefault(bucket,0)>0?bucket:ULTRA.item;
    }
    static void addRequired(Map<Integer,Integer> required,int item,int count,int storedUltra,
        Map<Integer,Integer> stock,Map<Integer,Integer> bag)
    {
        if(item!=ULTRA.item){Supplies.addCompost(required,item,count);return;}
        int deficit=Math.max(0,count-Math.max(0,storedUltra));
        // A known filled bucket in storage is an alternative to collecting
        // additional loose compost; no extra bucket if carried loose covers it.
        if(deficit>bag.getOrDefault(ULTRA.item,0)&&stock.getOrDefault(BOTTOMLESS.item,0)>0)
            Supplies.addCompost(required,BOTTOMLESS.item,1);
        else Supplies.addCompost(required,item,deficit);
    }
    static int localItem(int planned,Map<Integer,Integer> bag,int storedUltra)
    {
        if(planned!=ULTRA.item&&planned!=BOTTOMLESS.item)return planned;
        if(bag.getOrDefault(BOTTOMLESS.item,0)>0)return BOTTOMLESS.item;
        if(bag.getOrDefault(ULTRA.item,0)>0||storedUltra>0)return ULTRA.item;
        return planned;
    }
    static boolean withdrawUltra(int item,Map<Integer,Integer> bag,int storedUltra)
    {
        return item==ItemID.BUCKET_ULTRACOMPOST&&storedUltra>0&&
            bag.getOrDefault(ItemID.BUCKET_ULTRACOMPOST,0)==0&&
            bag.getOrDefault(ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED,0)==0;
    }
    static int stored(net.runelite.api.Client client,int id)
    {
        if(id==REGULAR.item)return Math.max(0,256*client.getVarbitValue(6266)+client.getVarbitValue(1442));
        if(id==SUPER.item)return Math.max(0,256*client.getVarbitValue(6267)+client.getVarbitValue(1443));
        if(id==ULTRA.item)return Math.max(0,client.getVarbitValue(LEPRECHAUN_ULTRACOMPOST));
        if(id==BOTTOMLESS.item)return client.getVarbitValue(7915)>1?1:0;
        return 0;
    }
    static boolean isOrdinary(int id)
    {
        return id == ItemID.BUCKET_COMPOST || id == ItemID.BUCKET_SUPERCOMPOST || id == ItemID.BUCKET_ULTRACOMPOST;
    }
    @Override public String toString() { return label; }
}
