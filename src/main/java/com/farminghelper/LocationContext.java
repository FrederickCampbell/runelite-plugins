package com.farminghelper;

import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

/** Coordinate conversion adapted from Quest Helper QuestPerspective (BSD-2-Clause,
 * Zoinkwiz and contributors). See the existing upstream Quest Helper license.
 * POH template bounds and canonical path origin follow Shortest Path PohService
 * (BSD-2-Clause, Skretzo and contributors). No house scanner or pathfinder. */
public final class LocationContext
{
    final WorldPoint actual,normalized;
    final boolean instance;
    private LocationContext(WorldPoint actual,WorldPoint normalized,boolean instance)
    {this.actual=actual;this.normalized=normalized;this.instance=instance;}
    static LocationContext capture(Client client)
    {
        Player p=client.getLocalPlayer();
        if(p==null)return new LocationContext(null,null,false);
        WorldView view=p.getWorldView();
        WorldPoint actual=p.getWorldLocation();
        return new LocationContext(actual,normalize(client,p.getLocalLocation(),actual),view!=null&&view.isInstance());
    }
    public static WorldPoint normalize(Client client,LocalPoint local,WorldPoint fallback)
    {
        if(local==null)return fallback;
        WorldView view=client.getWorldView(local.getWorldView());
        if(view==null)return fallback;
        if(!view.isTopLevel())
        {
            WorldView top=client.getTopLevelWorldView();
            if(top==null||top.worldEntities()==null)return fallback;
            var entity=top.worldEntities().byIndex(view.getId());
            if(entity==null)return fallback;
            LocalPoint main=entity.transformToMainWorld(local);
            if(main==null)return fallback;
            WorldPoint result=WorldPoint.fromLocalInstance(client,main,top.getPlane());
            return result==null?fallback:result;
        }
        if(!view.isInstance())return fallback;
        WorldPoint result=WorldPoint.fromLocalInstance(client,local,view.getPlane());
        return result==null?fallback:result;
    }
    static boolean isHouse(WorldPoint point)
    {
        return point!=null&&point.getX()>=1856&&point.getX()<=2047&&point.getY()>=7040&&point.getY()<=7111;
    }
    boolean inHouse(){return instance&&isHouse(normalized);}
    WorldPoint pathStart(){return inHouse()?new WorldPoint(1858,7051,0):actual;}
}
