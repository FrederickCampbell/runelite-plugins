package com.farminghelper;

import java.util.*;

/** Character-local last observed stock, not a saved run. Never reads unopened game storage. */
final class StockSnapshot
{
    static String encode(Map<Integer,Integer> items)
    {
        StringJoiner out=new StringJoiner(",");
        new TreeMap<>(items).forEach((id,n)->{if(id>0 && n>0)out.add(id+":"+n);});
        return out.toString();
    }
    static Map<Integer,Integer> decode(String raw)
    {
        Map<Integer,Integer> result=new HashMap<>();
        if(raw==null || raw.isEmpty())return result;
        for(String pair:raw.split(","))
        {
            String[] fields=pair.split(":",-1);
            if(fields.length!=2)continue;
            try {int id=Integer.parseInt(fields[0]),count=Integer.parseInt(fields[1]);if(id>0 && count>0)result.put(id,count);}
            catch(NumberFormatException ignored) { /* Ignore malformed entry, not the whole snapshot. */ }
        }
        return result;
    }
}
