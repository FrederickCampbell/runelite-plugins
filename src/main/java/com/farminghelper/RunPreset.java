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
    boolean included(PatchType type,BiPredicate<String,Boolean> flag)
    {return types.contains(type)&&flag.test(key(type),defaultEnabled(type))&&!flag.test("skipType_"+type.name(),false);}
    Set<String> select(List<FarmRunPatch> patches,BiPredicate<String,Boolean> flag)
    {
        return ManualSelection.keys(patches,p->included(p.getPatchType(),flag));
    }
    static String individualTitle(PatchType type)
    {String n=type.getDisplayName();return Character.toUpperCase(n.charAt(0))+n.substring(1)+" Run";}
}
