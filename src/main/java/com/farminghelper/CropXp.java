package com.farminghelper;

import java.util.*;

/** Healthy-cycle XP per planting item. Values from the user's 2026-11-24
 * normalized OSRS Wiki crop tables. Variable yields are the documented
 * high-level reference estimates, NOT a prediction for the player's level.
 * Missing yield data is represented by NaN; use planting/health XP only in
 * that case. No invented CTS constants or disease probabilities. */
final class CropXp
{
    private static final Map<String,Score> DATA=new HashMap<>();
    static final class Score
    {
        final double planting,health,harvest,yield;
        final boolean estimated;
        Score(double p,double c,double h,double y,boolean estimate)
        {planting=p;health=c;harvest=h;yield=y;estimated=estimate;}
        double total(){return planting+health+(Double.isNaN(yield)?0:harvest*yield);}
        boolean unknownYield(){return Double.isNaN(yield);}
    }
    static
    {
        add(PatchType.ALLOTMENT,"Potato",8,0,9,27.5,true);
        add(PatchType.ALLOTMENT,"Onion",9.5,0,10.5,27.5,true);
        add(PatchType.ALLOTMENT,"Cabbage",10,0,11.5,27.5,true);
        add(PatchType.ALLOTMENT,"Tomato",12.5,0,14,27.5,true);
        add(PatchType.ALLOTMENT,"Sweetcorn",17,0,19,27.5,true);
        add(PatchType.ALLOTMENT,"Strawberry",26,0,29,27.5,true);
        add(PatchType.ALLOTMENT,"Watermelon",48.5,0,54.5,27.5,true);
        add(PatchType.ALLOTMENT,"Snape grass",82,0,82,37.5,true);
        // Flower XP is per patch, never multiplied by harvested roots/leaves.
        add(PatchType.FLOWER,"Marigold",8.5,0,47,1,false);
        add(PatchType.FLOWER,"Rosemary",12,0,66.5,1,false);
        add(PatchType.FLOWER,"Nasturtium",19.5,0,111,1,false);
        add(PatchType.FLOWER,"Woad",20.5,0,115.5,1,false);
        add(PatchType.FLOWER,"Limpwurt",21.5,0,120,1,false);
        add(PatchType.FLOWER,"White lily",42,0,250,1,false);
        String[] herbs={"Guam","Marrentill","Tarromin","Harralander","Ranarr","Toadflax","Irit","Avantoe","Kwuarm","Snapdragon","Huasca","Cadantine","Lantadyme","Dwarf weed","Torstol"};
        double[] hp={11,13.5,16,21.5,27,34,43,54.5,69,87.5,86.5,106.5,134.5,170.5,199.5};
        double[] hh={12.5,15,18,24,30.5,38.5,48.5,61.5,78,98.5,110,120,151.5,192,224.5};
        for(int i=0;i<herbs.length;i++)add(PatchType.HERB,herbs[i],hp[i],0,hh[i],9,true);
        String[] hops={"Barley","Hammerstone","Asgarnian","Jute","Yanillian","Flax","Krandorian","Wildblood","Hemp","Cotton"};
        double[] hopP={8.5,9,10.9,13,14.5,16,17.5,23,33,72};
        double[] hopH={9.5,10,12,14.5,16,17.5,19.5,26,37,82};
        double[] hopY={27,27,27,27,27,35,27,27,25,15};
        for(int i=0;i<hops.length;i++)add(PatchType.HOPS,hops[i],hopP[i],0,hopH[i],hopY[i],true);
        String[] bushes={"Redberry","Cadavaberry","Dwellberry","Jangerberry","Whiteberry","Poison ivy"};
        double[] bp={11.5,18,31.5,50.5,78,120},bc={64,102.5,177.5,284.5,437.5,675},bh={4.5,7,12,19,29,45};
        for(int i=0;i<bushes.length;i++)add(PatchType.BUSH,bushes[i],bp[i],bc[i],bh[i],17.5,true);
        tree(PatchType.TREE,"Oak",14,467.3,0,0,false);tree(PatchType.TREE,"Willow",25,1456.5,0,0,false);
        tree(PatchType.TREE,"Maple",45,3403.4,0,0,false);tree(PatchType.TREE,"Yew",81,7069.9,0,0,false);tree(PatchType.TREE,"Magic",145.5,13768.3,0,0,false);
        String[] fruit={"Apple","Banana","Orange","Curry","Pineapple","Papaya","Palm","Dragonfruit"};
        double[] fp={22,28,35.5,40,57,72,110.5,140},fc={1199.5,1750.5,2470.2,2906.9,4605.7,6146.4,10150.1,17335},fh={8.5,10.5,13.5,15,21.5,27,41.5,70};
        for(int i=0;i<fruit.length;i++)tree(PatchType.FRUIT_TREE,fruit[i],fp[i],fc[i],fh[i],6,false);
        tree(PatchType.HARDWOOD_TREE,"Teak",35,7290,0,0,false);tree(PatchType.HARDWOOD_TREE,"Mahogany",63,15720,0,0,false);
        tree(PatchType.HARDWOOD_TREE,"Camphor",88,17840,0,0,false);tree(PatchType.HARDWOOD_TREE,"Ironwood",145,20380,0,0,false);tree(PatchType.HARDWOOD_TREE,"Rosewood",252,23100,0,0,false);
        tree(PatchType.CALQUAT,"Calquat",129.5,12096,48.5,6,false);tree(PatchType.CRYSTAL_TREE,"Crystal",126,13240,0,0,false);
        tree(PatchType.SPIRIT_TREE,"Spirit",199.5,19301.8,0,0,false);tree(PatchType.CELASTRUS,"Celastrus",204,14130,23.5,9,true);
        tree(PatchType.REDWOOD,"Redwood",230,22450,0,0,false);
        add(PatchType.CACTUS,"Cactus",66.5,374,25,10,true);add(PatchType.CACTUS,"Potato cactus",68,230,68,17.5,true);
        add(PatchType.SEAWEED,"Seaweed",19,0,21,33,true);add(PatchType.GRAPEVINE,"Grape",31.5,625,40,Double.NaN,true);
        add(PatchType.MUSHROOM,"Mushroom",61.5,0,57.7,Double.NaN,true);add(PatchType.BELLADONNA,"Belladonna",91,0,512,8,true);
        add(PatchType.HESPORI,"Hespori",62,0,12600,1,false);
        add(PatchType.CORAL,"Elkhorn fragment",20.5,0,24,Double.NaN,true);add(PatchType.CORAL,"Pillar fragment",52.5,0,60,Double.NaN,true);add(PatchType.CORAL,"Umbral fragment",136,0,159,Double.NaN,true);
        for(String anima:List.of("Attas","Iasor","Kronos"))add(PatchType.ANIMA,anima,100,0,0,0,false);
    }
    private static String key(PatchType type,String name){return type.name()+":"+name;}
    private static void add(PatchType t,String n,double p,double c,double h,double y,boolean estimate){DATA.put(key(t,n),new Score(p,c,h,y,estimate));}
    private static void tree(PatchType t,String n,double p,double c,double h,double y,boolean estimate){add(t,n+" sapling",p,c,h,y,estimate);}
    static Score score(Crop crop){return DATA.get(key(crop.getPatchType(),crop.getName()));}
    static double perSeed(Crop crop){Score s=score(crop);return s==null?Double.NaN:s.total()/crop.getQuantity();}
    static List<Crop> ranked(PatchType type,int level)
    {
        List<Crop> out=new ArrayList<>();
        for(Crop c:CropCatalog.crops(type))if(c.getLevel()<=level)out.add(c);
        out.sort(Comparator.<Crop>comparingDouble(c->{double v=perSeed(c);return Double.isNaN(v)?-1:v;}).reversed()
            .thenComparing(Comparator.comparingInt(Crop::getLevel).reversed()));
        return out;
    }
    private CropXp(){}
}
