package com.farminghelper;

enum RunPolicy
{
    BOTH("Compost + protection",true,true), COMPOST("Compost only",true,false),
    PROTECTION("Pay for protection",false,true), NONE("Neither",false,false);
    final String label; final boolean compost,protection;
    RunPolicy(String label,boolean compost,boolean protection){this.label=label;this.compost=compost;this.protection=protection;}
    static RunPolicy load(String stored,boolean compost,boolean protection)
    {
        try{return valueOf(stored);}catch(IllegalArgumentException|NullPointerException e)
        {return compost?(protection?BOTH:COMPOST):(protection?PROTECTION:NONE);}
    }
    @Override public String toString(){return label;}
}
