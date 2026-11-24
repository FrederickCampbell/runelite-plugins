package com.farminghelper;

import java.time.Instant;
import java.util.*;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.config.ConfigManager;
import com.farminghelper.queststate.CropState;
import com.farminghelper.queststate.QuestFarmingStateReader.Observation;
import com.farminghelper.queststate.QuestFarmingStateReader.Source;

/** Read-only adapter for native RuneLite Time Tracking birdhouse snapshots.
 * Native varp states and config keys: runelite timetracking/hunter/BirdHouseTracker,
 * BirdHouseSpace and BirdHouseState (BSD-2-Clause; see LICENSE-BIRDHOUSE).
 * Local object IDs: hong-niu/runelite-birdhouse-overlay BirdhouseOverlayPlugin.
 * Mushtree approach points: RuneLite TransportationPointLocation.
 * No native config writes or game interactions. */
final class BirdhouseRun
{
    enum Site
    {
        VALLEY_NORTH("Verdant Valley (Northeast)",VarPlayerID.BIRDHOUSE_TRANSMIT_C,30567,3757,3756),
        VALLEY_SOUTH("Verdant Valley (Southwest)",VarPlayerID.BIRDHOUSE_TRANSMIT_D,30568,3757,3756),
        MEADOW_NORTH("Mushroom Meadow (North)",VarPlayerID.BIRDHOUSE_TRANSMIT_A,30565,3674,3871),
        MEADOW_SOUTH("Tar Swamp entrance",VarPlayerID.BIRDHOUSE_TRANSMIT_B,30566,3676,3755);
        final String label;final int varp,object;final WorldPoint approach;
        Site(String label,int varp,int object,int x,int y){this.label=label;this.varp=varp;this.object=object;this.approach=new WorldPoint(x,y,0);}
    }
    static List<FarmRunPatch> patches()
    {
        List<FarmRunPatch> result=new ArrayList<>();
        for(Site site:Site.values())result.add(new FarmRunPatch("Fossil Island",site.label,PatchType.BIRDHOUSE));
        return result;
    }
    static Site site(FarmRunPatch p)
    {for(Site s:Site.values())if(s.label.equals(p.getName()))return s;return null;}
    static boolean isObject(int id)
    {for(Site s:Site.values())if(s.object==id)return true;return false;}
    static boolean seeded(int raw){return raw>0&&raw<=27&&raw%3==0;}
    static CropState decode(int raw,long timestamp,long now)
    {
        if(raw<0||raw>27)return null;
        if(raw==0)return CropState.EMPTY;
        if(!seeded(raw))return CropState.FILLING;
        if(timestamp<=0||timestamp>now)return null;
        return now-timestamp>=3000?CropState.HARVESTABLE:CropState.GROWING;
    }
    static Observation observe(Client client,ConfigManager config,FarmRunPatch p,boolean local)
    {
        Site s=site(p);int cached=-1;long time=0;
        if(s==null)return new Observation(null,Source.NONE,0,null,p.getName());
        String value=config.getRSProfileConfiguration("timetracking","birdhouse."+s.varp);
        if(value!=null)try{String[] parts=value.split(":");if(parts.length==2){cached=Integer.parseInt(parts[0]);time=Long.parseLong(parts[1]);}}catch(NumberFormatException ignored){}
        int raw=local?client.getVarpValue(s.varp):cached;
        long now=Instant.now().getEpochSecond();
        if(raw!=cached)time=0;
        return new Observation(decode(raw,time,now),local?Source.LIVE:cached>=0?Source.TIME_TRACKING:Source.NONE,time*1000,null,p.getName(),raw);
    }
    private static final int[] HOUSES={ItemID.BIRDHOUSE_NORMAL,ItemID.BIRDHOUSE_OAK,ItemID.BIRDHOUSE_WILLOW,ItemID.BIRDHOUSE_TEAK,ItemID.BIRDHOUSE_MAPLE,ItemID.BIRDHOUSE_MAHOGANY,ItemID.BIRDHOUSE_YEW,ItemID.BIRDHOUSE_MAGIC,ItemID.BIRDHOUSE_REDWOOD};
    private static final int[] LOGS={1511,1521,1519,6333,1517,6332,1515,1513,19669};
    private static final int[] HUNTER={5,14,24,34,44,49,59,74,89};
    private static final int[] CRAFT={5,15,25,35,45,50,60,75,90};
    private static final int[] BAIT={ItemID.BARLEY_SEED,ItemID.HAMMERSTONE_HOP_SEED,ItemID.ASGARNIAN_HOP_SEED,ItemID.JUTE_SEED,ItemID.YANILLIAN_HOP_SEED,ItemID.KRANDORIAN_HOP_SEED,ItemID.POTATO_SEED,ItemID.ONION_SEED,ItemID.CABBAGE_SEED,ItemID.TOMATO_SEED,ItemID.SWEETCORN_SEED,ItemID.STRAWBERRY_SEED};
    static Map<FarmRunPatch,String> supplies(List<FarmRunPatch> ready,Map<FarmRunPatch,Observation> states,
        Map<Integer,Integer> stock,Map<Integer,Integer> required,int hunter,int crafting)
    {
        Map<Integer,Integer> left=new HashMap<>(stock);
        required.forEach((id,n)->left.merge(id,-n,Integer::sum));
        Map<FarmRunPatch,String> result=new LinkedHashMap<>();
        int bait=0,clockworks=0;
        for(FarmRunPatch p:ready)
        {
            if(p.getPatchType()!=PatchType.BIRDHOUSE)continue;
            Observation o=states.get(p);
            if(o.state==CropState.FILLING)
            {bait+=10;result.put(p,"Fill the existing birdhouse with suitable seeds.");continue;}
            String instruction=o.state==CropState.EMPTY?"Build and bait the empty birdhouse spot.":o.state==CropState.HARVESTABLE?"Empty the ready birdhouse, recover the clockwork, replace and bait it.":"Inspect the birdhouse; if ready, empty, replace and bait it. Do not dismantle a growing trap.";
            bait+=10;
            boolean found=false;
            for(int i=HOUSES.length-1;i>=0;i--)
            {
                if(hunter<HUNTER[i])continue;
                if(left.getOrDefault(HOUSES[i],0)>0)
                {required.merge(HOUSES[i],1,Integer::sum);left.merge(HOUSES[i],-1,Integer::sum);found=true;break;}
                if(crafting>=CRAFT[i]&&left.getOrDefault(LOGS[i],0)>0)
                {
                    required.merge(LOGS[i],1,Integer::sum);left.merge(LOGS[i],-1,Integer::sum);
                    required.put(2347,1);required.put(1755,1);
                    if(o.state!=CropState.HARVESTABLE)clockworks++;
                    found=true;break;
                }
            }
            if(!found)
            {
                int i=HOUSES.length-1;while(i>0&&hunter<HUNTER[i])i--;
                required.merge(HOUSES[i],1,Integer::sum);
            }
            result.put(p,instruction);
        }
        if(clockworks>0)required.merge(8792,clockworks,Integer::sum);
        for(int id:BAIT)
        {
            int n=Math.min(bait,Math.max(0,left.getOrDefault(id,0)));
            if(n>0){required.merge(id,n,Integer::sum);bait-=n;}
            if(bait==0)break;
        }
        if(bait>0)required.merge(ItemID.BARLEY_SEED,bait,Integer::sum);
        return result;
    }
    static Set<Integer> stepItems(FarmRunPatch p,Observation o,Map<Integer,Integer> stock,Map<Integer,Integer> bag,Map<Integer,Integer> farming,int hunter,int crafting)
    {
        if(o.state!=CropState.EMPTY&&o.state!=CropState.FILLING)return Collections.emptySet();
        Map<Integer,Integer> needed=new HashMap<>(farming);
        supplies(List.of(p),Map.of(p,o),stock,needed,hunter,crafting);
        if(o.state==CropState.FILLING)
        {
            for(int id:BAIT)if(needed.getOrDefault(id,0)>farming.getOrDefault(id,0)&&bag.getOrDefault(id,0)>0)return Set.of(id);
            return Collections.emptySet();
        }
        for(int id:HOUSES)if(needed.getOrDefault(id,0)>farming.getOrDefault(id,0)&&bag.getOrDefault(id,0)>0)return Set.of(id);
        for(int id:LOGS)if(needed.getOrDefault(id,0)>farming.getOrDefault(id,0)&&bag.getOrDefault(id,0)>0&&bag.getOrDefault(8792,0)>0)return Set.of(id,8792);
        return Collections.emptySet();
    }
    private BirdhouseRun(){}
}
