package com.farminghelper;

import java.util.*;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.Point;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.game.ItemManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class VaultLayoutLifecycleTest
{
    private static Widget node(int parent,int index,int type,int x,int y,int width,int height,String text,int id,int quantity)
    {
        Widget w=mock(Widget.class);int[] xy={x,y};boolean[] hidden={false};
        when(w.getId()).thenReturn(parent);when(w.getIndex()).thenReturn(index);when(w.getType()).thenReturn(type);
        when(w.getOriginalX()).thenAnswer(a->xy[0]);when(w.getOriginalY()).thenAnswer(a->xy[1]);
        when(w.setOriginalX(anyInt())).thenAnswer(a->{xy[0]=a.getArgument(0);return w;});
        when(w.setOriginalY(anyInt())).thenAnswer(a->{xy[1]=a.getArgument(0);return w;});
        when(w.isSelfHidden()).thenAnswer(a->hidden[0]);when(w.setHidden(anyBoolean())).thenAnswer(a->{hidden[0]=a.getArgument(0);return w;});
        when(w.getWidth()).thenReturn(width);when(w.getHeight()).thenReturn(height);when(w.getOriginalHeight()).thenReturn(height);
        when(w.getText()).thenReturn(text);when(w.getItemId()).thenReturn(id);when(w.getItemQuantity()).thenReturn(quantity);
        return w;
    }
    private static Widget parent(Client c,int id,Widget... children)
    {
        Widget w=mock(Widget.class);when(c.getWidget(id)).thenReturn(w);when(w.getChildren()).thenReturn(children);
        when(w.getCanvasLocation()).thenReturn(new Point(0,0));return w;
    }
    private static void item(ItemManager manager,int id,String name)
    {ItemComposition c=mock(ItemComposition.class);when(manager.getItemComposition(id)).thenReturn(c);when(c.getName()).thenReturn(name);when(c.getPlaceholderTemplateId()).thenReturn(-1);}
    @Test public void nativeItemAndLabelMoveTogetherThenRestoreBeforeBuild()
    {
        Client c=mock(Client.class);ItemManager im=mock(ItemManager.class);FarmingHelperPlugin plugin=mock(FarmingHelperPlugin.class);
        when(plugin.vaultAllowed(99)).thenReturn(Set.of(PatchType.HERB));when(plugin.vaultOverrides(99)).thenReturn(Map.of());
        item(im,ItemID.GUAM_SEED,"Guam seed");item(im,ItemID.TORSTOL_SEED,"Torstol seed");
        Widget favorite=node(SeedVaultViews.ITEMS,0,WidgetType.GRAPHIC,14,50,36,32,"",ItemID.GUAM_SEED,5);
        Widget first=node(SeedVaultViews.ITEMS,1,WidgetType.GRAPHIC,14,228,36,32,"",ItemID.GUAM_SEED,5);
        Widget best=node(SeedVaultViews.ITEMS,3,WidgetType.GRAPHIC,79,300,36,32,"",ItemID.TORSTOL_SEED,7);
        Widget fl=node(SeedVaultViews.LABELS,0,WidgetType.TEXT,7,82,50,30,"Guam seed",-1,0);
        Widget l1=node(SeedVaultViews.LABELS,1,WidgetType.TEXT,7,260,50,30,"Guam seed",-1,0);
        Widget l2=node(SeedVaultViews.LABELS,3,WidgetType.TEXT,72,332,50,30,"Torstol seed",-1,0);
        parent(c,SeedVaultViews.ITEMS,favorite,first,null,best);parent(c,SeedVaultViews.LABELS,fl,l1,null,l2);
        Widget header=node(SeedVaultViews.HEADERS,0,WidgetType.TEXT,5,200,100,20,"Herbs",-1,0);
        parent(c,SeedVaultViews.HEADERS,header); // no verified dividers => safe per-section fallback
        Widget viewport=parent(c,SeedVaultViews.VIEWPORT);when(viewport.getHeight()).thenReturn(200);when(viewport.getScrollHeight()).thenReturn(500);
        SeedVaultViews view=new SeedVaultViews(c,im,plugin);view.select(VaultRanking.View.HIGHEST);view.filter(99);
        assertTrue(first.isSelfHidden());assertTrue(l1.isSelfHidden());assertFalse(favorite.isSelfHidden());
        assertEquals(14,best.getOriginalX());assertEquals(228,best.getOriginalY());assertEquals(7,l2.getOriginalX());assertEquals(260,l2.getOriginalY());
        assertEquals(3,best.getIndex());assertEquals(ItemID.TORSTOL_SEED,best.getItemId());assertEquals(7,best.getItemQuantity());
        verify(best,never()).setItemId(anyInt());verify(best,never()).setItemQuantity(anyInt());verify(best,never()).setAction(anyInt(),anyString());
        view.beforeScript(SeedVaultViews.BUILD);
        assertFalse(first.isSelfHidden());assertFalse(l1.isSelfHidden());assertEquals(79,best.getOriginalX());assertEquals(300,best.getOriginalY());assertEquals(72,l2.getOriginalX());assertEquals(332,l2.getOriginalY());
    }
    @Test public void verifiedEmptySectionCollapsesAndNativeScrollRestores()
    {
        Client c=mock(Client.class);ItemManager im=mock(ItemManager.class);FarmingHelperPlugin plugin=mock(FarmingHelperPlugin.class);
        when(plugin.vaultAllowed(99)).thenReturn(Set.of(PatchType.HERB));when(plugin.vaultOverrides(99)).thenReturn(Map.of());
        item(im,ItemID.POTATO_SEED,"Potato seed");item(im,ItemID.TORSTOL_SEED,"Torstol seed");
        Widget potato=node(SeedVaultViews.ITEMS,0,WidgetType.GRAPHIC,14,128,36,32,"",ItemID.POTATO_SEED,5);
        Widget best=node(SeedVaultViews.ITEMS,2,WidgetType.GRAPHIC,79,278,36,32,"",ItemID.TORSTOL_SEED,7);
        Widget l1=node(SeedVaultViews.LABELS,0,WidgetType.TEXT,7,160,50,30,"Potato seed",-1,0);
        Widget l2=node(SeedVaultViews.LABELS,2,WidgetType.TEXT,72,310,50,30,"Torstol seed",-1,0);
        parent(c,SeedVaultViews.ITEMS,potato,null,best);parent(c,SeedVaultViews.LABELS,l1,null,l2);
        Widget a=node(SeedVaultViews.HEADERS,0,WidgetType.TEXT,5,100,100,20,"Allotment",-1,0);
        Widget h=node(SeedVaultViews.HEADERS,1,WidgetType.TEXT,5,250,100,20,"Herbs",-1,0);
        Widget al=node(SeedVaultViews.LINES,0,3,5,120,300,1,"",-1,0),hl=node(SeedVaultViews.LINES,1,3,5,270,300,1,"",-1,0);
        parent(c,SeedVaultViews.HEADERS,a,h);parent(c,SeedVaultViews.LINES,al,hl);
        Widget viewport=parent(c,SeedVaultViews.VIEWPORT);int[] height={500},scroll={40};
        // Parent canvas position reflects scrolling; item layer is at content Y=0.
        Widget ip=c.getWidget(SeedVaultViews.ITEMS),lp=c.getWidget(SeedVaultViews.LABELS),hp=c.getWidget(SeedVaultViews.HEADERS),dp=c.getWidget(SeedVaultViews.LINES);
        for(Widget w:List.of(ip,lp,hp,dp))when(w.getCanvasLocation()).thenAnswer(x->new Point(0,-scroll[0]));
        when(viewport.getHeight()).thenReturn(200);when(viewport.getScrollHeight()).thenAnswer(x->height[0]);when(viewport.getScrollY()).thenAnswer(x->scroll[0]);
        when(viewport.setScrollHeight(anyInt())).thenAnswer(x->{height[0]=x.getArgument(0);return viewport;});
        when(viewport.setScrollY(anyInt())).thenAnswer(x->{scroll[0]=x.getArgument(0);return viewport;});
        SeedVaultViews view=new SeedVaultViews(c,im,plugin);view.select(VaultRanking.View.HIGHEST);view.filter(99);
        assertTrue(a.isSelfHidden());assertTrue(al.isSelfHidden());assertEquals(100,h.getOriginalY());assertEquals(128,best.getOriginalY());
        assertEquals(350,height[0]);assertEquals(0,scroll[0]);
        // An inventory transmit can invoke the inner procedure directly. Native
        // code replaces child widgets and writes the final scroll size after that call.
        SeedVaultViews lifecycle=spy(view);
        doAnswer(invocation->{assertTrue(lifecycle.filter(99));return null;}).when(lifecycle).refresh();
        lifecycle.beforeScript(SeedVaultViews.BUILD_CONTENTS);
        assertEquals(500,height[0]);assertFalse(a.isSelfHidden());
        Widget fresh=node(SeedVaultViews.ITEMS,2,WidgetType.GRAPHIC,79,278,36,32,"",ItemID.TORSTOL_SEED,6);
        when(ip.getChildren()).thenReturn(new Widget[]{potato,null,fresh});
        lifecycle.afterScript(SeedVaultViews.BUILD_CONTENTS);
        height[0]=520;
        lifecycle.invalidate();lifecycle.postClientTick();
        assertTrue(a.isSelfHidden());assertEquals(370,height[0]);assertEquals(128,fresh.getOriginalY());
        // Withdrawing the last stack leaves the section empty. Depositing it
        // again must re-use the new native widget and its real slot/quantity.
        lifecycle.beforeScript(SeedVaultViews.BUILD);
        when(ip.getChildren()).thenReturn(new Widget[]{potato});
        when(lp.getChildren()).thenReturn(new Widget[]{l1});
        lifecycle.afterScript(SeedVaultViews.BUILD);lifecycle.postClientTick();
        assertTrue(h.isSelfHidden());assertEquals(200,height[0]);
        lifecycle.beforeScript(SeedVaultViews.BUILD);
        when(ip.getChildren()).thenReturn(new Widget[]{potato,null,fresh});
        when(lp.getChildren()).thenReturn(new Widget[]{l1,null,l2});
        lifecycle.afterScript(SeedVaultViews.BUILD);lifecycle.postClientTick();
        assertFalse(h.isSelfHidden());assertEquals(128,fresh.getOriginalY());
        assertEquals(2,fresh.getIndex());assertEquals(6,fresh.getItemQuantity());
        lifecycle.leave();assertFalse(a.isSelfHidden());assertFalse(al.isSelfHidden());assertEquals(250,h.getOriginalY());assertEquals(278,fresh.getOriginalY());
        assertEquals(520,height[0]);assertEquals(40,scroll[0]);
    }
}
