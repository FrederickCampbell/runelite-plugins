package com.farminghelper;

import java.util.*;

/** Character-local last observed stock, not a saved run. Never reads unopened game storage. */
final class StockSnapshot
{
    static long time(String raw,long now)
    {
        try {long value=Long.parseLong(raw);return value>0&&value<=now?value:0;}
        catch(NumberFormatException e){return 0;}
    }
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
        for(int start=0,end;start<raw.length();start=end+1)
        {
            end=raw.indexOf(',',start);
            if(end<0)end=raw.length();
            int colon=start;
            while(colon<end&&raw.charAt(colon)!=':')colon++;
            if(colon==start||colon>=end-1)continue;
            try {int id=Integer.parseInt(raw,start,colon,10),count=Integer.parseInt(raw,colon+1,end,10);if(id>0&&count>0)result.put(id,count);}
            catch(NumberFormatException ignored) { /* Ignore malformed entry, not the whole snapshot. */ }
        }
        return result;
    }
}
