package com.farminghelper;

import com.farminghelper.queststate.QuestFarmingStateReader;
import com.farminghelper.queststate.QuestFarmingStateReader.Observation;

/** Species preferences use the actual planted produce, not the proposed replacement.
 * Only plants that can remain planted are offered. Celastrus is not a renewable bark source. */
enum KeepCrop
{
    WHITE_LILY(PatchType.FLOWER,"White lily",true),
    WHITEBERRIES(PatchType.BUSH,"Whiteberry bush",true),
    POISON_IVY(PatchType.BUSH,"Poison ivy bush",true),
    CACTUS(PatchType.CACTUS,"Cactus",true),
    POTATO_CACTUS(PatchType.CACTUS,"Potato cactus",true),
    PAPAYA(PatchType.FRUIT_TREE,"Papaya tree",true),
    PALM(PatchType.FRUIT_TREE,"Palm tree",true),
    DRAGONFRUIT(PatchType.FRUIT_TREE,"Dragonfruit tree",true),
    REDWOOD(PatchType.REDWOOD,"Redwood tree",true),
    REDBERRIES(PatchType.BUSH,"Redberry bush",false),
    CADAVABERRIES(PatchType.BUSH,"Cadavaberry bush",false),
    DWELLBERRIES(PatchType.BUSH,"Dwellberry bush",false),
    JANGERBERRIES(PatchType.BUSH,"Jangerberry bush",false),
    APPLE(PatchType.FRUIT_TREE,"Apple tree",false),
    BANANA(PatchType.FRUIT_TREE,"Banana tree",false),
    ORANGE(PatchType.FRUIT_TREE,"Orange tree",false),
    CURRY(PatchType.FRUIT_TREE,"Curry tree",false),
    PINEAPPLE(PatchType.FRUIT_TREE,"Pineapple plant",false),
    CALQUAT(PatchType.CALQUAT,"Calquat tree",false),
    OAK(PatchType.TREE,"Oak tree",false),
    WILLOW(PatchType.TREE,"Willow tree",false),
    MAPLE(PatchType.TREE,"Maple tree",false),
    YEW(PatchType.TREE,"Yew tree",false),
    MAGIC(PatchType.TREE,"Magic tree",false),
    TEAK(PatchType.HARDWOOD_TREE,"Teak tree",false),
    MAHOGANY(PatchType.HARDWOOD_TREE,"Mahogany tree",false),
    CAMPHOR(PatchType.HARDWOOD_TREE,"Camphor tree",false),
    IRONWOOD(PatchType.HARDWOOD_TREE,"Ironwood tree",false),
    ROSEWOOD(PatchType.HARDWOOD_TREE,"Rosewood tree",false),
    MARIGOLD(PatchType.FLOWER,"Marigold (leave unpicked)",false),
    ROSEMARY(PatchType.FLOWER,"Rosemary (leave unpicked)",false),
    NASTURTIUM(PatchType.FLOWER,"Nasturtium (leave unpicked)",false),
    WOAD(PatchType.FLOWER,"Woad (leave unpicked)",false),
    LIMPWURT(PatchType.FLOWER,"Limpwurt (leave unpicked)",false);

    final PatchType type;
    final String label;
    final boolean common;
    KeepCrop(PatchType type,String label,boolean common){this.type=type;this.label=label;this.common=common;}
    String key(){return "keepSpeciesV1_"+name();}
    Crop crop()
    {
        String name=label.split(" \\(")[0];
        if(type==PatchType.BUSH)name=name.replace(" bush","");
        if(type.isTree()||type==PatchType.CALQUAT)
            name=name.replace(" tree","").replace(" plant","")+" sapling";
        return CropCatalog.findByName(type,name);
    }
    @Override public String toString(){return label;}
    private static final java.util.Map<String,KeepCrop> PRODUCE=new java.util.HashMap<>();
    static{for(KeepCrop crop:values())PRODUCE.put(crop.name(),crop);}
    static KeepCrop observed(PatchType type,Observation o)
    {
        String key=QuestFarmingStateReader.livingProduceKey(type.name(),o);
        KeepCrop crop=PRODUCE.get(key);
        return crop!=null&&crop.type==type?crop:null;
    }
    static boolean explicit(String mode){return "PRESERVE".equals(mode)||"REPLANT".equals(mode);}
    static boolean resolve(PatchType type,KeepCrop species,String typeMode,String speciesMode,String patchMode,String runMode)
    {
        if(type==PatchType.SPIRIT_TREE)return true;
        if(!CropReplacement.preferenceType(type))return false;
        if(explicit(runMode))return "PRESERVE".equals(runMode);
        if(explicit(patchMode))return "PRESERVE".equals(patchMode);
        if(species!=null&&explicit(speciesMode))return "PRESERVE".equals(speciesMode);
        if(explicit(typeMode))return "PRESERVE".equals(typeMode);
        return type==PatchType.CACTUS||species==WHITE_LILY;
    }
}
