package com.farminghelper;

import java.util.Map;
import static net.runelite.api.gameval.VarbitID.*;
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
    static final int LEPRECHAUN_ULTRACOMPOST=FARMING_TOOLS_ULTRACOMPOST;
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
        addRequired(new SupplyLedger(required,new java.util.HashMap<>()),item,count,0,item==ULTRA.item?storedUltra:0,stock,bag);
    }
    static void addRequired(SupplyLedger demands,int item,int count,int guildCount,int stored,
        Map<Integer,Integer> stock,Map<Integer,Integer> bag)
    {
        int deficit=Math.max(0,count-Math.max(0,stored));
        int local=Math.max(0,guildCount-Math.max(0,stored));
        if(item<0||deficit==0)return;
        // Pick the physical representation once for the whole run, then scope it.
        if(item==ULTRA.item&&deficit>bag.getOrDefault(ULTRA.item,0)&&stock.getOrDefault(BOTTOMLESS.item,0)>0)item=BOTTOMLESS.item;
        if(item==BOTTOMLESS.item)demands.reuse(item,1,local>0);
        else demands.consumeSplit(item,deficit,local);
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
        if(id==REGULAR.item)return Math.max(0,256*client.getVarbitValue(FARMING_TOOLS_EXTRACOMPOST)+client.getVarbitValue(FARMING_TOOLS_COMPOST));
        if(id==SUPER.item)return Math.max(0,256*client.getVarbitValue(FARMING_TOOLS_EXTRASUPERCOMPOST)+client.getVarbitValue(FARMING_TOOLS_SUPERCOMPOST));
        if(id==ULTRA.item)return Math.max(0,client.getVarbitValue(LEPRECHAUN_ULTRACOMPOST));
        if(id==BOTTOMLESS.item)return client.getVarbitValue(FARMING_TOOLS_BOTTOMLESS_BUCKET_TYPE)>1?1:0;
        return 0;
    }
    /** Native compost overlay lookup: RuneLite cs2-scripts script9902 (a0cda81c).
     * IDs identify physical base objects, not the crop's transformed model.
     * The public VarbitID constants carry the same tiers as script9899 (0–3).
     * Grapevines are intentionally excluded by the existing compost policy. */
    static int liveVarbit(int objectId)
    {
        switch(objectId)
        {
            case 7572: return FARMING_COMPOST_VARBIT_BELLADONNA_TRANSMIT;
            case 56960: return FARMING_COMPOST_VARBIT_BELLADONNA_2_TRANSMIT;
            case 7577: return FARMING_COMPOST_VARBIT_BUSH_1_TRANSMIT;
            case 7578: return FARMING_COMPOST_VARBIT_BUSH_2_TRANSMIT;
            case 7579: return FARMING_COMPOST_VARBIT_BUSH_3_TRANSMIT;
            case 7580: return FARMING_COMPOST_VARBIT_BUSH_4_TRANSMIT;
            case 34006: return FARMING_COMPOST_VARBIT_BUSH_5_TRANSMIT;
            case 7771: return FARMING_COMPOST_VARBIT_CACTUS_TRANSMIT;
            case 33761: return FARMING_COMPOST_VARBIT_CACTUS_2_TRANSMIT;
            case 7807: return FARMING_COMPOST_VARBIT_CALQUAT_TREE_TRANSMIT;
            case 56954: return FARMING_COMPOST_VARBIT_CALQUAT_TREE_2_TRANSMIT;
            case 58675: return FARMING_COMPOST_VARBIT_CALQUAT_TREE_3_TRANSMIT;
            case 34629: return FARMING_CELASTRUS_COMPOST_1_TRANSMIT;
            case 34906: return FARMING_CRYSTAL_TREE_COMPOST_1_TRANSMIT;
            case 7847: return FARMING_COMPOST_VARBIT_FLOWER_1_TRANSMIT;
            case 7848: return FARMING_COMPOST_VARBIT_FLOWER_2_TRANSMIT;
            case 7849: return FARMING_COMPOST_VARBIT_FLOWER_3_TRANSMIT;
            case 7850: return FARMING_COMPOST_VARBIT_FLOWER_4_TRANSMIT;
            case 27111: return FARMING_COMPOST_VARBIT_FLOWER_5_TRANSMIT;
            case 33649: return FARMING_COMPOST_VARBIT_FLOWER_6_TRANSMIT;
            case 34919: return FARMING_COMPOST_VARBIT_FLOWER_7_TRANSMIT;
            case 50693: return FARMING_COMPOST_VARBIT_FLOWER_8_TRANSMIT;
            case 56959: return FARMING_COMPOST_VARBIT_FLOWER_9_TRANSMIT;
            case 7962: return FARMING_COMPOST_VARBIT_FRUIT_TREE_1_TRANSMIT;
            case 7963: return FARMING_COMPOST_VARBIT_FRUIT_TREE_2_TRANSMIT;
            case 7964: return FARMING_COMPOST_VARBIT_FRUIT_TREE_3_TRANSMIT;
            case 7965: return FARMING_COMPOST_VARBIT_FRUIT_TREE_4_TRANSMIT;
            case 26579: return FARMING_COMPOST_VARBIT_FRUIT_TREE_5_TRANSMIT;
            case 34007: return FARMING_COMPOST_VARBIT_FRUIT_TREE_6_TRANSMIT;
            case 56955: return FARMING_COMPOST_VARBIT_FRUIT_TREE_7_TRANSMIT;
            case 30482: return FARMING_COMPOST_VARBIT_HARDWOOD_1_TRANSMIT;
            case 30480: return FARMING_COMPOST_VARBIT_HARDWOOD_2_TRANSMIT;
            case 30481: return FARMING_COMPOST_VARBIT_HARDWOOD_3_TRANSMIT;
            case 50692: return FARMING_COMPOST_VARBIT_HARDWOOD_4_TRANSMIT;
            case 58834: return FARMING_COMPOST_VARBIT_HARDWOOD_5_TRANSMIT;
            case 8150: return FARMING_COMPOST_VARBIT_HERB_1_TRANSMIT;
            case 8151: return FARMING_COMPOST_VARBIT_HERB_2_TRANSMIT;
            case 8152: return FARMING_COMPOST_VARBIT_HERB_3_TRANSMIT;
            case 8153: return FARMING_COMPOST_VARBIT_HERB_4_TRANSMIT;
            case 9372: return FARMING_COMPOST_VARBIT_HERB_5_TRANSMIT;
            case 27115: return FARMING_COMPOST_VARBIT_HERB_6_TRANSMIT;
            case 33979: return FARMING_COMPOST_VARBIT_HERB_7_TRANSMIT;
            case 50697: return FARMING_COMPOST_VARBIT_HERB_8_TRANSMIT;
            case 18816: return MYARM_HERBPATCH_COMPOST_TRANSMIT;
            case 33176: return MY2ARM_HERBPATCH_COMPOST_TRANSMIT;
            case 8173: return FARMING_COMPOST_VARBIT_HOPS_1_TRANSMIT;
            case 8174: return FARMING_COMPOST_VARBIT_HOPS_2_TRANSMIT;
            case 8175: return FARMING_COMPOST_VARBIT_HOPS_3_TRANSMIT;
            case 8176: return FARMING_COMPOST_VARBIT_HOPS_4_TRANSMIT;
            case 55341: return FARMING_COMPOST_VARBIT_HOPS_5_TRANSMIT;
            case 8337: return FARMING_COMPOST_VARBIT_MUSHROOM_TRANSMIT;
            case 34055: return FARMING_REDWOOD_COMPOST_TRANSMIT;
            case 30500: return FARMING_COMPOST_VARBIT_SEAWEED_1_TRANSMIT;
            case 30501: return FARMING_COMPOST_VARBIT_SEAWEED_2_TRANSMIT;
            case 8338: return FARMING_COMPOST_VARBIT_SPIRIT_TREE_1_TRANSMIT;
            case 8382: return FARMING_COMPOST_VARBIT_SPIRIT_TREE_2_TRANSMIT;
            case 8383: return FARMING_COMPOST_VARBIT_SPIRIT_TREE_3_TRANSMIT;
            case 27116: return FARMING_COMPOST_VARBIT_SPIRIT_TREE_4_TRANSMIT;
            case 33733: return FARMING_COMPOST_VARBIT_SPIRIT_TREE_5_TRANSMIT;
            case 8388: return FARMING_COMPOST_VARBIT_TREE_1_TRANSMIT;
            case 8389: return FARMING_COMPOST_VARBIT_TREE_2_TRANSMIT;
            case 8390: return FARMING_COMPOST_VARBIT_TREE_3_TRANSMIT;
            case 8391: return FARMING_COMPOST_VARBIT_TREE_4_TRANSMIT;
            case 19147: return FARMING_TREE_COMPOST_5_TRANSMIT;
            case 33732: return FARMING_TREE_COMPOST_6_TRANSMIT;
            case 56953: return FARMING_TREE_COMPOST_7_TRANSMIT;
            case 8550: return FARMING_COMPOST_VARBIT_VEG_1_TRANSMIT;
            case 8551: return FARMING_COMPOST_VARBIT_VEG_2_TRANSMIT;
            case 8552: return FARMING_COMPOST_VARBIT_VEG_3_TRANSMIT;
            case 8553: return FARMING_COMPOST_VARBIT_VEG_4_TRANSMIT;
            case 8554: return FARMING_COMPOST_VARBIT_VEG_5_TRANSMIT;
            case 8555: return FARMING_COMPOST_VARBIT_VEG_6_TRANSMIT;
            case 8556: return FARMING_COMPOST_VARBIT_VEG_7_TRANSMIT;
            case 8557: return FARMING_COMPOST_VARBIT_VEG_8_TRANSMIT;
            case 21950: return FARMING_COMPOST_VARBIT_VEG_9_TRANSMIT;
            case 27113: return FARMING_COMPOST_VARBIT_VEG_10_TRANSMIT;
            case 27114: return FARMING_COMPOST_VARBIT_VEG_11_TRANSMIT;
            case 33694: return FARMING_COMPOST_VARBIT_VEG_12_TRANSMIT;
            case 33693: return FARMING_COMPOST_VARBIT_VEG_13_TRANSMIT;
            case 34922: return FARMING_COMPOST_VARBIT_VEG_14_TRANSMIT;
            case 34921: return FARMING_COMPOST_VARBIT_VEG_15_TRANSMIT;
            case 50696: return FARMING_COMPOST_VARBIT_VEG_16_TRANSMIT;
            case 50695: return FARMING_COMPOST_VARBIT_VEG_17_TRANSMIT;
            default: return -1;
        }
    }
    static int liveTier(net.runelite.api.Client client,int varbit)
    {
        if(varbit<0)return -1;
        int tier=client.getVarbitValue(varbit);
        return tier>=0&&tier<=3?tier:-1;
    }
    static int minimumTier(int item)
    {return item==ULTRA.item?3:item==SUPER.item?2:1;}
    static boolean isOrdinary(int id)
    {
        return id == ItemID.BUCKET_COMPOST || id == ItemID.BUCKET_SUPERCOMPOST || id == ItemID.BUCKET_ULTRACOMPOST;
    }
    @Override public String toString() { return label; }
}
