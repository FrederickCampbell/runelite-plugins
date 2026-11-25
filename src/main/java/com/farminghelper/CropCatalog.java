package com.farminghelper;

import java.util.*;
import net.runelite.api.gameval.ItemID;
import static com.farminghelper.PatchType.*;

/** Each plantable item owns its XP model, planting quantity and protection costs. */
final class CropCatalog
{
    private static final Map<PatchType,List<Crop>> byType=new EnumMap<>(PatchType.class);
    private static final Map<Integer,Crop> byItem=new HashMap<>();
    private static final Map<Integer,String> paymentNames=new HashMap<>();
    static
    {
        register(ALLOTMENT, "Potato", "Potato seed", 1, ItemID.POTATO_SEED, 3, estimate(8, 0, 9, 27.5),
            pay("Compost", ItemID.BUCKET_COMPOST, 2));
        register(ALLOTMENT, "Onion", "Onion seed", 5, ItemID.ONION_SEED, 3, estimate(9.5, 0, 10.5, 27.5),
            pay("Potatoes(10)", ItemID.SACK_POTATO_10, 1));
        register(ALLOTMENT, "Cabbage", "Cabbage seed", 7, ItemID.CABBAGE_SEED, 3, estimate(10, 0, 11.5, 27.5),
            pay("Onions(10)", ItemID.SACK_ONION_10, 1));
        register(ALLOTMENT, "Tomato", "Tomato seed", 12, ItemID.TOMATO_SEED, 3, estimate(12.5, 0, 14, 27.5),
            pay("Cabbages(10)", ItemID.SACK_CABBAGE_10, 2));
        register(ALLOTMENT, "Sweetcorn", "Sweetcorn seed", 20, ItemID.SWEETCORN_SEED, 3, estimate(17, 0, 19, 27.5),
            pay("Jute fibre", ItemID.JUTE_FIBRE, 10));
        register(ALLOTMENT, "Strawberry", "Strawberry seed", 31, ItemID.STRAWBERRY_SEED, 3, estimate(26, 0, 29, 27.5),
            pay("Apples(5)", ItemID.BASKET_APPLE_5, 1));
        register(ALLOTMENT, "Watermelon", "Watermelon seed", 47, ItemID.WATERMELON_SEED, 3, estimate(48.5, 0, 54.5, 27.5),
            pay("Curry leaf", ItemID.CURRY_LEAF, 10));
        register(ALLOTMENT, "Snape grass", "Snape grass seed", 61, ItemID.SNAPE_GRASS_SEED, 3, estimate(82, 0, 82, 37.5),
            pay("Jangerberries", ItemID.JANGERBERRIES, 5));

        register(FLOWER, "Marigold", "Marigold seed", 2, ItemID.MARIGOLD_SEED, 1, xp(8.5, 0, 47, 1));
        register(FLOWER, "Rosemary", "Rosemary seed", 11, ItemID.ROSEMARY_SEED, 1, xp(12, 0, 66.5, 1));
        register(FLOWER, "Nasturtium", "Nasturtium seed", 24, ItemID.NASTURTIUM_SEED, 1, xp(19.5, 0, 111, 1));
        register(FLOWER, "Woad", "Woad seed", 25, ItemID.WOAD_SEED, 1, xp(20.5, 0, 115.5, 1));
        register(FLOWER, "Limpwurt", "Limpwurt seed", 26, ItemID.LIMPWURT_SEED, 1, xp(21.5, 0, 120, 1));
        register(FLOWER, "White lily", "White lily seed", 58, ItemID.WHITE_LILY_SEED, 1, xp(42, 0, 250, 1));

        register(HERB, "Guam", "Guam seed", 9, ItemID.GUAM_SEED, 1, estimate(11, 0, 12.5, 9));
        register(HERB, "Marrentill", "Marrentill seed", 14, ItemID.MARRENTILL_SEED, 1, estimate(13.5, 0, 15, 9));
        register(HERB, "Tarromin", "Tarromin seed", 19, ItemID.TARROMIN_SEED, 1, estimate(16, 0, 18, 9));
        register(HERB, "Harralander", "Harralander seed", 26, ItemID.HARRALANDER_SEED, 1, estimate(21.5, 0, 24, 9));
        register(HERB, "Ranarr", "Ranarr seed", 32, ItemID.RANARR_SEED, 1, estimate(27, 0, 30.5, 9));
        register(HERB, "Toadflax", "Toadflax seed", 38, ItemID.TOADFLAX_SEED, 1, estimate(34, 0, 38.5, 9));
        register(HERB, "Irit", "Irit seed", 44, ItemID.IRIT_SEED, 1, estimate(43, 0, 48.5, 9));
        register(HERB, "Avantoe", "Avantoe seed", 50, ItemID.AVANTOE_SEED, 1, estimate(54.5, 0, 61.5, 9));
        register(HERB, "Kwuarm", "Kwuarm seed", 56, ItemID.KWUARM_SEED, 1, estimate(69, 0, 78, 9));
        register(HERB, "Snapdragon", "Snapdragon seed", 62, ItemID.SNAPDRAGON_SEED, 1, estimate(87.5, 0, 98.5, 9));
        register(HERB, "Huasca", "Huasca seed", 65, ItemID.HUASCA_SEED, 1, estimate(86.5, 0, 110, 9));
        register(HERB, "Cadantine", "Cadantine seed", 67, ItemID.CADANTINE_SEED, 1, estimate(106.5, 0, 120, 9));
        register(HERB, "Lantadyme", "Lantadyme seed", 73, ItemID.LANTADYME_SEED, 1, estimate(134.5, 0, 151.5, 9));
        register(HERB, "Dwarf weed", "Dwarf weed seed", 79, ItemID.DWARF_WEED_SEED, 1, estimate(170.5, 0, 192, 9));
        register(HERB, "Torstol", "Torstol seed", 85, ItemID.TORSTOL_SEED, 1, estimate(199.5, 0, 224.5, 9));

        register(HOPS, "Barley", "Barley seed", 3, ItemID.BARLEY_SEED, 4, estimate(8.5, 0, 9.5, 27),
            pay("Compost", ItemID.BUCKET_COMPOST, 3));
        register(HOPS, "Hammerstone", "Hammerstone seed", 4, ItemID.HAMMERSTONE_HOP_SEED, 4, estimate(9, 0, 10, 27),
            pay("Marigolds", ItemID.MARIGOLD, 1));
        register(HOPS, "Asgarnian", "Asgarnian seed", 8, ItemID.ASGARNIAN_HOP_SEED, 4, estimate(10.9, 0, 12, 27),
            pay("Onions(10)", ItemID.SACK_ONION_10, 1));
        register(HOPS, "Jute", "Jute seed", 13, ItemID.JUTE_SEED, 3, estimate(13, 0, 14.5, 27),
            pay("Barley malt", ItemID.BARLEY_MALT, 6));
        register(HOPS, "Yanillian", "Yanillian seed", 16, ItemID.YANILLIAN_HOP_SEED, 4, estimate(14.5, 0, 16, 27),
            pay("Tomatoes(5)", ItemID.BASKET_TOMATO_5, 1));
        register(HOPS, "Flax", "Flax seed", 18, ItemID.FLAX_SEED, 3, estimate(16, 0, 17.5, 35),
            pay("Grain", ItemID.GRAIN, 6));
        register(HOPS, "Krandorian", "Krandorian seed", 21, ItemID.KRANDORIAN_HOP_SEED, 4, estimate(17.5, 0, 19.5, 27),
            pay("Cabbages(10)", ItemID.SACK_CABBAGE_10, 3));
        register(HOPS, "Wildblood", "Wildblood seed", 28, ItemID.WILDBLOOD_HOP_SEED, 4, estimate(23, 0, 26, 27),
            pay("Nasturtiums", ItemID.NASTURTIUM, 1));
        register(HOPS, "Hemp", "Hemp seed", 37, ItemID.HEMP_SEED, 3, estimate(33, 0, 37, 25),
            pay("Flax", ItemID.FLAX, 6));
        register(HOPS, "Cotton", "Cotton seed", 71, ItemID.COTTON_SEED, 3, estimate(72, 0, 82, 15),
            pay("Hemp", ItemID.HEMP, 6));

        register(BUSH, "Redberry", "Redberry seed", 10, ItemID.REDBERRY_BUSH_SEED, 1, estimate(11.5, 64, 4.5, 17.5),
            pay("Cabbages(10)", ItemID.SACK_CABBAGE_10, 4));
        register(BUSH, "Cadavaberry", "Cadavaberry seed", 22, ItemID.CADAVABERRY_BUSH_SEED, 1, estimate(18, 102.5, 7, 17.5),
            pay("Tomatoes(5)", ItemID.BASKET_TOMATO_5, 3));
        register(BUSH, "Dwellberry", "Dwellberry seed", 36, ItemID.DWELLBERRY_BUSH_SEED, 1, estimate(31.5, 177.5, 12, 17.5),
            pay("Strawberries(5)", ItemID.BASKET_STRAWBERRY_5, 3));
        register(BUSH, "Jangerberry", "Jangerberry seed", 48, ItemID.JANGERBERRY_BUSH_SEED, 1, estimate(50.5, 284.5, 19, 17.5),
            pay("Watermelon", ItemID.WATERMELON, 6));
        register(BUSH, "Whiteberry", "Whiteberry seed", 59, ItemID.WHITEBERRY_BUSH_SEED, 1, estimate(78, 437.5, 29, 17.5),
            pay("Bittercap mushroom", ItemID.BITTERCAP_MUSHROOM, 8));
        register(BUSH, "Poison ivy", "Poison ivy seed", 70, ItemID.POISONIVY_BUSH_SEED, 1, estimate(120, 675, 45, 17.5));

        register(TREE, "Oak sapling", "Oak sapling", 15, ItemID.PLANTPOT_OAK_SAPLING, 1, xp(14, 467.3, 0, 0),
            pay("Tomatoes(5)", ItemID.BASKET_TOMATO_5, 1));
        register(TREE, "Willow sapling", "Willow sapling", 30, ItemID.PLANTPOT_WILLOW_SAPLING, 1, xp(25, 1456.5, 0, 0),
            pay("Apples(5)", ItemID.BASKET_APPLE_5, 1));
        register(TREE, "Maple sapling", "Maple sapling", 45, ItemID.PLANTPOT_MAPLE_SAPLING, 1, xp(45, 3403.4, 0, 0),
            pay("Oranges(5)", ItemID.BASKET_ORANGE_5, 1));
        register(TREE, "Yew sapling", "Yew sapling", 60, ItemID.PLANTPOT_YEW_SAPLING, 1, xp(81, 7069.9, 0, 0),
            pay("Cactus spine", ItemID.CACTUS_SPINE, 10));
        register(TREE, "Magic sapling", "Magic sapling", 75, ItemID.PLANTPOT_MAGIC_TREE_SAPLING, 1, xp(145.5, 13768.3, 0, 0),
            pay("Coconut", ItemID.COCONUT, 25));

        register(FRUIT_TREE, "Apple sapling", "Apple sapling", 27, ItemID.PLANTPOT_APPLE_SAPLING, 1, xp(22, 1199.5, 8.5, 6),
            pay("Sweetcorn", ItemID.SWEETCORN, 9));
        register(FRUIT_TREE, "Banana sapling", "Banana sapling", 33, ItemID.PLANTPOT_BANANA_SAPLING, 1, xp(28, 1750.5, 10.5, 6),
            pay("Apples(5)", ItemID.BASKET_APPLE_5, 4));
        register(FRUIT_TREE, "Orange sapling", "Orange sapling", 39, ItemID.PLANTPOT_ORANGE_SAPLING, 1, xp(35.5, 2470.2, 13.5, 6),
            pay("Strawberries(5)", ItemID.BASKET_STRAWBERRY_5, 3));
        register(FRUIT_TREE, "Curry sapling", "Curry sapling", 42, ItemID.PLANTPOT_CURRY_SAPLING, 1, xp(40, 2906.9, 15, 6),
            pay("Bananas(5)", ItemID.BASKET_BANANA_5, 5));
        register(FRUIT_TREE, "Pineapple sapling", "Pineapple sapling", 51, ItemID.PLANTPOT_PINEAPPLE_SAPLING, 1, xp(57, 4605.7, 21.5, 6),
            pay("Watermelon", ItemID.WATERMELON, 10));
        register(FRUIT_TREE, "Papaya sapling", "Papaya sapling", 57, ItemID.PLANTPOT_PAPAYA_SAPLING, 1, xp(72, 6146.4, 27, 6),
            pay("Pineapple", ItemID.PINEAPPLE, 10));
        register(FRUIT_TREE, "Palm sapling", "Palm sapling", 68, ItemID.PLANTPOT_PALM_SAPLING, 1, xp(110.5, 10150.1, 41.5, 6),
            pay("Papaya fruit", ItemID.PAPAYA, 15));
        register(FRUIT_TREE, "Dragonfruit sapling", "Dragonfruit sapling", 81, ItemID.PLANTPOT_DRAGONFRUIT_SAPLING, 1, xp(140, 17335, 70, 6),
            pay("Coconut", ItemID.COCONUT, 15));

        register(HARDWOOD_TREE, "Teak sapling", "Teak sapling", 35, ItemID.PLANTPOT_TEAK_SAPLING, 1, xp(35, 7290, 0, 0),
            pay("Limpwurt root", ItemID.LIMPWURT_ROOT, 15));
        register(HARDWOOD_TREE, "Mahogany sapling", "Mahogany sapling", 55, ItemID.PLANTPOT_MAHOGANY_SAPLING, 1, xp(63, 15720, 0, 0),
            pay("Yanillian hops", ItemID.YANILLIAN_HOPS, 25));
        register(HARDWOOD_TREE, "Camphor sapling", "Camphor sapling", 66, ItemID.PLANTPOT_CAMPHOR_SAPLING, 1, xp(88, 17840, 0, 0),
            pay("White berries", ItemID.WHITE_BERRIES, 10));
        register(HARDWOOD_TREE, "Ironwood sapling", "Ironwood sapling", 80, ItemID.PLANTPOT_IRONWOOD_SAPLING, 1, xp(145, 20380, 0, 0),
            pay("Curry leaf", ItemID.CURRY_LEAF, 10));
        register(HARDWOOD_TREE, "Rosewood sapling", "Rosewood sapling", 92, ItemID.PLANTPOT_ROSEWOOD_SAPLING, 1, xp(252, 23100, 0, 0),
            pay("Dragonfruit", ItemID.DRAGONFRUIT, 8));

        register(CACTUS, "Cactus", "Cactus seed", 55, ItemID.CACTUS_SEED, 1, estimate(66.5, 374, 25, 10),
            pay("Cadava berries", ItemID.CADAVABERRIES, 6));
        register(CACTUS, "Potato cactus", "Potato cactus seed", 64, ItemID.POTATO_CACTUS_SEED, 1, estimate(68, 230, 68, 17.5),
            pay("Snape grass", ItemID.SNAPE_GRASS, 8));

        register(MUSHROOM, "Mushroom", "Mushroom spore", 53, ItemID.MUSHROOM_SEED, 1, estimate(61.5, 0, 57.7, Double.NaN));

        register(BELLADONNA, "Belladonna", "Belladonna seed", 63, ItemID.BELLADONNA_SEED, 1, estimate(91, 0, 512, 8));

        register(CALQUAT, "Calquat sapling", "Calquat sapling", 72, ItemID.PLANTPOT_CALQUAT_SAPLING, 1, xp(129.5, 12096, 48.5, 6),
            pay("Poison ivy berries", ItemID.POISONIVY_BERRIES, 8));

        register(SPIRIT_TREE, "Spirit sapling", "Spirit sapling", 83, ItemID.PLANTPOT_SPIRIT_TREE_SAPLING, 1, xp(199.5, 19301.8, 0, 0),
            pay("Monkey nuts", ItemID.MM_MONKEY_NUTS, 5), pay("Monkey bar", ItemID.MM_MONKEY_BAR, 1), pay("Ground tooth", ItemID.LUNAR_GROUNDTOOTH, 1));

        register(SEAWEED, "Seaweed", "Seaweed spore", 23, ItemID.SEAWEED_SEED, 1, estimate(19, 0, 21, 33),
            pay("Numulite", ItemID.FOSSIL_NUMULITE, 200));

        register(GRAPEVINE, "Grape", "Grape seed", 36, ItemID.GRAPE_SEED, 1, estimate(31.5, 625, 40, Double.NaN));

        register(CELASTRUS, "Celastrus sapling", "Celastrus sapling", 85, ItemID.PLANTPOT_CELASTRUS_TREE_SAPLING, 1, estimate(204, 14130, 23.5, 9),
            pay("Potato cactus", ItemID.CACTUS_POTATO, 8));

        register(REDWOOD, "Redwood sapling", "Redwood sapling", 90, ItemID.PLANTPOT_REDWOOD_TREE_SAPLING, 1, xp(230, 22450, 0, 0),
            pay("Dragonfruit", ItemID.DRAGONFRUIT, 6));

        register(HESPORI, "Hespori", "Hespori seed", 65, ItemID.HESPORI_SEED, 1, xp(62, 0, 12600, 1));

        register(ANIMA, "Attas", "Attas seed", 76, ItemID.ATTAS_SEED, 1, xp(100, 0, 0, 0));
        register(ANIMA, "Iasor", "Iasor seed", 76, ItemID.IASOR_SEED, 1, xp(100, 0, 0, 0));
        register(ANIMA, "Kronos", "Kronos seed", 76, ItemID.KRONOS_SEED, 1, xp(100, 0, 0, 0));

        register(CRYSTAL_TREE, "Crystal sapling", "Crystal sapling", 74, ItemID.PLANTPOT_CRYSTAL_TREE_SAPLING, 1, xp(126, 13240, 0, 0));

        register(CORAL, "Elkhorn fragment", "Elkhorn frag", 28, ItemID.CORAL_ELKHORN_FRAG, 1, estimate(20.5, 0, 24, Double.NaN),
            pay("Giant seaweed", ItemID.GIANT_SEAWEED, 5));
        register(CORAL, "Pillar fragment", "Pillar frag", 52, ItemID.CORAL_PILLAR_FRAG, 1, estimate(52.5, 0, 60, Double.NaN),
            pay("Elkhorn coral", ItemID.CORAL_ELKHORN, 5));
        register(CORAL, "Umbral fragment", "Umbral frag", 77, ItemID.CORAL_UMBRAL_FRAG, 1, estimate(136, 0, 159, Double.NaN),
            pay("Pillar coral", ItemID.CORAL_PILLAR, 5));
        byType.replaceAll((type,crops)->List.copyOf(crops));
    }

    private static void register(PatchType type,String name,String itemName,int level,int itemId,int quantity,
        CropXp.Score score,ProtectionPayment... costs)
    {
        Crop crop=new Crop(type,name,itemName,level,itemId,quantity,score,List.of(costs));
        byItem.put(itemId,crop);
        byType.computeIfAbsent(type,t->new ArrayList<>()).add(crop);
        for(ProtectionPayment cost:costs)paymentNames.putIfAbsent(cost.getItemId(),cost.getName());
    }

    private static ProtectionPayment pay(String name,int id,int quantity){return new ProtectionPayment(name,id,quantity);}
    private static CropXp.Score xp(double planting,double health,double harvest,double yield)
    {return new CropXp.Score(planting,health,harvest,yield,false);}
    private static CropXp.Score estimate(double planting,double health,double harvest,double yield)
    {return new CropXp.Score(planting,health,harvest,yield,true);}

    static String paymentName(int id){return paymentNames.get(id);}
    static List<ProtectionPayment> protection(Crop crop){return crop==null?List.of():crop.getPayments();}
    static Crop findByItemId(int id){return byItem.get(id);}
    static List<Crop> crops(PatchType type){return byType.getOrDefault(type,List.of());}

    static Crop findByName(PatchType type,String name)
    {
        for(Crop crop:crops(type))if(crop.getName().equals(name)||crop.getItemName().equals(name))return crop;
        return null;
    }

    static Crop recommend(PatchType type,int level)
    {
        List<Crop> ranked=CropXp.ranked(type,level);
        return ranked.isEmpty()?null:ranked.get(0);
    }

    static List<Crop> preferences(PatchType type,String names)
    {
        List<Crop> result=new ArrayList<>();
        for(String name:names.split(","))
        {
            Crop crop=findByName(type,name.trim());
            if(crop!=null&&!result.contains(crop))result.add(crop);
        }
        return result;
    }

    private CropCatalog(){}
}
