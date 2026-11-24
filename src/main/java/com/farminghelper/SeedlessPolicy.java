package com.farminghelper;

/** One user choice represents the existing two planner flags. */
enum SeedlessPolicy
{
    GUIDE("Guide anyway",false,false),
    CLEAR("Harvest / clear",true,true),
    SKIP("Skip patch",true,false);
    final String label;final boolean hide,clear;
    SeedlessPolicy(String label,boolean hide,boolean clear){this.label=label;this.hide=hide;this.clear=clear;}
    static SeedlessPolicy of(boolean hide,boolean clear){return !hide?GUIDE:clear?CLEAR:SKIP;}
    static SeedlessPolicy load(String value){try{return valueOf(value);}catch(IllegalArgumentException|NullPointerException e){return GUIDE;}}
    @Override public String toString(){return label;}
}
