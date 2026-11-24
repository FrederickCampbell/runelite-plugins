package com.farminghelper;

import java.util.*;
import java.util.function.IntFunction;

/** Raw seeds are a separate observed resource; virtual stock is allocation-only. */
final class TreePreparation
{
    static final int FILLED_POT=5354, WATERING_CAN=5331;
    static Map<Integer,Integer> aliases(Map<Integer,Integer> stock,IntFunction<String> name)
    {
        Map<Integer,Integer> result=new TreeMap<>();
        for(int id:stock.keySet())if(stock.get(id)>0&&CropCatalog.findByItemId(id)==null)
        {
            Crop c=VaultRanking.resolve(id,name.apply(id));
            if(c!=null&&c.getName().endsWith(" sapling")&&c.getItemId()!=id)result.put(id,c.getItemId());
        }
        return result;
    }
    static Map<Integer,Integer> allocationStock(Map<Integer,Integer> stock,Map<Integer,Integer> aliases)
    {
        Map<Integer,Integer> result=new HashMap<>(stock);
        aliases.forEach((raw,sapling)->result.merge(sapling,stock.getOrDefault(raw,0),Integer::sum));
        return result;
    }
    static Map<Integer,Integer> preparation(Map<FarmRunPatch,Crop> allocated,Map<Integer,Integer> stock,Map<Integer,Integer> aliases)
    {
        Map<Integer,Integer> need=new LinkedHashMap<>(), result=new LinkedHashMap<>();
        for(Crop c:allocated.values())if(c.getName().endsWith(" sapling"))need.merge(c.getItemId(),c.getQuantity(),Integer::sum);
        for(Map.Entry<Integer,Integer> e:aliases.entrySet())
        {
            int sapling=e.getValue();
            int missing=Math.max(0,need.getOrDefault(sapling,0)-stock.getOrDefault(sapling,0));
            int n=Math.min(missing,stock.getOrDefault(e.getKey(),0));
            if(n>0){result.put(e.getKey(),n);need.merge(sapling,-n,Integer::sum);}
        }
        return result;
    }
    /** Explain the physical raw stock used by the virtual sapling allocation. */
    static String evidence(Map<Integer,Integer> preparation,Map<Integer,Integer> carried,Map<Integer,Integer> bank,Map<Integer,Integer> vault,IntFunction<String> name,boolean bankObserved,boolean vaultObserved)
    {
        StringJoiner out=new StringJoiner("\n");
        new TreeMap<>(preparation).forEach((id,n)->out.add(name.apply(id)+" ×"+n+" needed; carried "+carried.getOrDefault(id,0)+", bank "+bank.getOrDefault(id,0)+(bankObserved?" (observed)":" (cached/unknown)")+", vault "+vault.getOrDefault(id,0)+(vaultObserved?" (observed)":" (cached/unknown)")+"."));
        return out.toString();
    }
    private TreePreparation(){}
}
