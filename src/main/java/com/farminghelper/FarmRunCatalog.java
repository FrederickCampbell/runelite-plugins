package com.farminghelper;

import com.farminghelper.queststate.QuestFarmingStateReader;
import java.util.ArrayList;
import java.util.List;
import static com.farminghelper.PatchType.*;

/** v1.0 display and allocation order. The farming registry supplies every physical plot name. */
final class FarmRunCatalog
{
    private static final List<FarmRunPatch> PATCHES=build();
    static List<FarmRunPatch> patches(){return PATCHES;}

    private static List<FarmRunPatch> build()
    {
        List<FarmRunPatch> result=new ArrayList<>();
        for(String site:List.of("Falador","Ardougne","Catherby","Morytania","Kourend"))farm(result,site);
        add(result,HERB,"Troll Stronghold");
        add(result,ALLOTMENT,"Harmony Island");
        add(result,HERB,"Weiss");
        farm(result,"Farming Guild");
        add(result,HERB,"Harmony Island");
        farm(result,"Civitas illa Fortis");
        add(result,ALLOTMENT,"Prifddinas");
        add(result,FLOWER,"Prifddinas");
        add(result,TREE,"Lumbridge","Varrock","Gnome Stronghold","Falador","Taverley","Farming Guild","Auburnvale");
        add(result,FRUIT_TREE,"Gnome Stronghold","Tree Gnome Village","Catherby","Farming Guild","Lletya","Brimhaven");
        add(result,CALQUAT,"Tai Bwo Wannai","Kastori");
        add(result,FRUIT_TREE,"Kastori");
        add(result,CALQUAT,"Great Conch");
        add(result,HARDWOOD_TREE,"Fossil Island","Avium Savannah","Anglers' Retreat");
        add(result,HOPS,"Yanille","Seers' Village","Lumbridge","Entrana","Aldarin");
        add(result,BUSH,"Champions' Guild","Rimmington","Ardougne","Etceteria","Farming Guild");
        add(result,CACTUS,"Al Kharid","Farming Guild");
        for(PatchType type:List.of(HESPORI,ANIMA,CELASTRUS,REDWOOD,SPIRIT_TREE))add(result,type,"Farming Guild");
        add(result,SEAWEED,"Seaweed");
        add(result,MUSHROOM,"Morytania");
        add(result,BELLADONNA,"Draynor Manor","Auburnvale");
        add(result,GRAPEVINE,"Kourend");
        add(result,SPIRIT_TREE,"Kourend");
        add(result,CRYSTAL_TREE,"Prifddinas");
        add(result,SPIRIT_TREE,"Port Sarim","Etceteria","Brimhaven");
        add(result,CORAL,"Great Conch");
        add(result,FLOWER,"Kastori");
        add(result,COMPOST_BIN,"Farming Guild","Falador","Morytania","Ardougne","Catherby","Kourend","Civitas illa Fortis","Prifddinas");
        return List.copyOf(result);
    }

    private static void farm(List<FarmRunPatch> patches,String location)
    {
        for(PatchType type:List.of(ALLOTMENT,FLOWER,HERB))add(patches,type,location);
    }

    private static void add(List<FarmRunPatch> patches,PatchType type,String... locations)
    {
        for(String location:locations)
            for(String name:QuestFarmingStateReader.patchNamesForType(type.name(),location))
                patches.add(new FarmRunPatch(location,name,type));
    }
}
