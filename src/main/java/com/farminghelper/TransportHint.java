package com.farminghelper;

import java.util.*;
import net.runelite.api.coords.WorldPoint;

/** Read-only view of native Shortest Path query transport metadata. */
final class TransportHint
{
    final WorldPoint origin,destination;
    final int objectId;
    final String action,display;
    TransportHint(WorldPoint origin,WorldPoint destination,int objectId,String action,String display)
    {this.origin=origin;this.destination=destination;this.objectId=objectId;this.action=action;this.display=display;}
    static List<TransportHint> read(Object raw)
    {
        List<TransportHint> result=new ArrayList<>();
        if(!(raw instanceof Collection))return result;
        for(Object entry:(Collection<?>)raw)
        {
            if(!(entry instanceof Map))continue;
            Map<?,?> m=(Map<?,?>)entry;
            if(!(m.get("origin") instanceof WorldPoint)||!(m.get("destination") instanceof WorldPoint)||!(m.get("objectInfo") instanceof String))continue;
            String info=(String)m.get("objectInfo");
            String lower=info.toLowerCase(Locale.ROOT);
            int split=info.lastIndexOf(' ');if(split<0)continue;
            try
            {
                int id=Integer.parseInt(info.substring(split+1));
                if(id<=0)continue;
                String action=info.substring(0,split).trim();
                if(action.isEmpty())continue;
                String display=m.get("displayInfo") instanceof String?(String)m.get("displayInfo"):"";
                WorldPoint origin=(WorldPoint)m.get("origin"),destination=(WorldPoint)m.get("destination");
                if(display.isBlank()&&lower.contains("rowboat")&&id==30919)display="Return to Fossil Island";
                result.add(new TransportHint(origin,destination,id,action,display));
            }catch(NumberFormatException ignored){}
        }
        return result;
    }
    static TransportHint nearby(List<TransportHint> hints,WorldPoint player)
    {
        if(player==null)return null;
        for(TransportHint h:hints)
            if(h.origin.getPlane()==player.getPlane()&&player.distanceTo(h.origin)<=12&&player.distanceTo(h.origin)<player.distanceTo(h.destination))return h;
        return null;
    }
    String instruction(){return action+(display.isBlank()?"":" — "+display);}
}
