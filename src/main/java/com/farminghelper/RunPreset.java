package com.farminghelper;

import java.util.*;
import java.util.function.BiPredicate;

/** Presets are selection data, not separate guide implementations. Reference order
 * from supplied Wiki Farming runs export; location keys remain stable aliases. */
enum RunPreset
{
    TREE("Combined Tree Run", EnumSet.of(PatchType.TREE,PatchType.FRUIT_TREE,PatchType.HARDWOOD_TREE,PatchType.CALQUAT,PatchType.CELASTRUS,PatchType.CRYSTAL_TREE,PatchType.REDWOOD),
        "Farming Guild|Gnome Stronghold|Tree Gnome Village|Lumbridge Castle|Varrock|Falador Park|Taverley|Catherby|Brimhaven|Tai Bwo Wannai|Lletya|Prifddinas|Kastori|Auburnvale|Avium Savannah|Great Conch|Anglers' Retreat|Fossil Island"),
    CROP("Crop Run", EnumSet.of(PatchType.HERB,PatchType.ALLOTMENT,PatchType.FLOWER,PatchType.COMPOST_BIN),
        "Farming Guild|South Falador|Morytania|North Ardougne|Catherby|Hosidius|Prifddinas|Civitas illa Fortis|Kastori|Harmony Island|Troll Stronghold|Weiss"),
    HOPS_BUSH("Hops & Bush Run", EnumSet.of(PatchType.HOPS,PatchType.BUSH),
        "Farming Guild|Champions' Guild|Ardougne Monastery|Aldarin|Seers' Village|Etceteria|Yanille|Rimmington|Entrana"),
    SPECIALTY("Specialty Run", EnumSet.of(PatchType.CACTUS,PatchType.BELLADONNA,PatchType.MUSHROOM,PatchType.SEAWEED,PatchType.CORAL,PatchType.GRAPEVINE,PatchType.BIRDHOUSE),
        "Farming Guild|Al Kharid|Draynor Manor|Canifis mushroom|Hosidius Vinery|Auburnvale belladonna|Great Conch|Fossil Island");

    final String title; final Set<PatchType> types; final List<String> reference;
    RunPreset(String title,Set<PatchType> types,String reference)
    {this.title=title;this.types=Collections.unmodifiableSet(types);this.reference=List.of(reference.split("\\|"));}
    boolean defaultEnabled(PatchType type){return type!=PatchType.GRAPEVINE && type!=PatchType.COMPOST_BIN;}
    String key(PatchType type){return "preset_"+name()+"_"+type.name();}
    Set<String> select(List<FarmRunPatch> patches,BiPredicate<String,Boolean> flag)
    {
        Set<String> keys=new LinkedHashSet<>();
        for(FarmRunPatch p:patches)if(types.contains(p.getPatchType())&&flag.test(key(p.getPatchType()),defaultEnabled(p.getPatchType())))keys.add(p.key());
        return keys;
    }
    static List<String> individual(PatchType type)
    {
        switch(type)
        {
            case HERB:return List.of("Farming Guild","South Falador","Morytania","North Ardougne","Catherby","Hosidius","Civitas illa Fortis","Troll Stronghold","Weiss","Harmony Island");
            case TREE:return List.of("Farming Guild","Lumbridge Castle","Varrock","Falador Park","Taverley","Gnome Stronghold","Auburnvale");
            case FRUIT_TREE:return List.of("Farming Guild","Gnome Stronghold","Tree Gnome Village","Catherby","Lletya","Brimhaven","Kastori");
            case HARDWOOD_TREE:return List.of("Fossil Island","Avium Savannah","Anglers' Retreat");
            case HOPS:return List.of("Champions' Guild","Aldarin","Seers' Village","Yanille","Entrana");
            case BUSH:return List.of("Farming Guild","Champions' Guild","Rimmington","Ardougne Monastery","Etceteria");
            case ALLOTMENT:case FLOWER:case COMPOST_BIN:return CROP.reference;
            case CALQUAT:return TREE.reference;
            default:return List.of("Farming Guild");
        }
    }
    static String individualTitle(PatchType type)
    {String n=type.getDisplayName();return Character.toUpperCase(n.charAt(0))+n.substring(1)+" Run";}
}
