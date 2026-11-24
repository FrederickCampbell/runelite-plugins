package com.farminghelper;

import java.util.*;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.client.game.ItemManager;

/** Native-looking, plugin-local category rows. v0.18 intentionally filters
 * in place: never move/reindex native item/label children, change quantities,
 * invoke native scripts, edit game vars, or intercept withdrawal actions.
 * IDs are from the user's live 631 widget dump (2026-11-24). */
final class SeedVaultViews
{
    static final int GROUP=631, INIT=2814, CATEGORY=2815, BUILD=2819;
    static final int LEFT=(GROUP<<16)|7, CATEGORIES=(GROUP<<16)|9, ITEMS=(GROUP<<16)|18, LABELS=(GROUP<<16)|19;
    private final Client client;
    private final ItemManager items;
    private final FarmingHelperPlugin plugin;
    private final Map<Widget,Boolean> hidden=new IdentityHashMap<>();
    private final EnumMap<VaultRanking.View,Widget> buttons=new EnumMap<>(VaultRanking.View.class);
    private Widget parent;
    private int originalScrollHeight=-1,originalChildCount;
    private VaultRanking.View active;
    private boolean dirty=true, building;
    private int lastLevel=-1;
    private long lastConfig=Long.MIN_VALUE;

    SeedVaultViews(Client client,ItemManager items,FarmingHelperPlugin plugin)
    {this.client=client;this.items=items;this.plugin=plugin;}

    void invalidate(){dirty=true;}
    void beforeScript(int id)
    {
        if(id==INIT||id==CATEGORY){leave();removeButtons();building=true;}
        else if(id==BUILD){restore();building=true;dirty=true;}
    }
    void afterScript(int id)
    {
        if(id==INIT||id==CATEGORY||id==BUILD){building=false;dirty=true;}
    }
    void nativeMenu(int component)
    {
        if(component==CATEGORIES||component==InterfaceID.SeedVault.SEARCH)leave();
    }
    void close(){leave();removeButtons();building=false;lastLevel=-1;lastConfig=Long.MIN_VALUE;dirty=true;}
    void leave(){restore();active=null;paintButtons();dirty=true;}

    void tick()
    {
        if(client.getGameState()!=GameState.LOGGED_IN||!plugin.flag("seedVaultViews",true)) {close();return;}
        Widget root=client.getWidget(InterfaceID.SeedVault.UNIVERSE);
        if(root==null||root.isHidden()){close();return;}
        if(building)return;
        Widget left=client.getWidget(LEFT), categories=client.getWidget(CATEGORIES);
        if(left==null||categories==null||categories.isHidden())return;
        if(left!=parent||!buttonsIntact())
        {
            restore();removeButtons();
            if(!addButtons(left,categories))return;
            dirty=true;
        }
        int level=client.getRealSkillLevel(Skill.FARMING);
        long revision=plugin.preferenceRevision();
        if(lastLevel!=level||lastConfig!=revision){lastLevel=level;lastConfig=revision;dirty=true;}
        if(dirty&&active!=null){dirty=false;filter(level);}
    }

    private boolean buttonsIntact()
    {
        if(parent==null||buttons.size()!=VaultRanking.View.values().length)return false;
        Widget[] children=parent.getChildren();
        if(children==null)return false;
        for(Widget w:buttons.values())if(w.getIndex()<0||w.getIndex()>=children.length||children[w.getIndex()]!=w)return false;
        return true;
    }
    private boolean addButtons(Widget left,Widget categories)
    {
        Widget[] nativeRows=categories.getDynamicChildren();
        if(nativeRows==null)return false;
        Widget coral=null;
        for(Widget w:nativeRows)if(w!=null&&"Coral".equalsIgnoreCase(clean(w.getText())))coral=w;
        if(coral==null||coral.getHeight()<=0||categories.getWidth()<60)return false;
        parent=left;originalScrollHeight=left.getScrollHeight();
        originalChildCount=left.getChildren()==null?0:left.getChildren().length;
        int y=categories.getRelativeY()+coral.getRelativeY()+coral.getHeight();
        int x=categories.getRelativeX()+coral.getRelativeX();
        int height=coral.getHeight(),width=coral.getWidth();
        // Do not alter native scrolling scripts; extend only if necessary.
        if(y+height*3>left.getScrollHeight()&&y+height*3>left.getHeight())left.setScrollHeight(y+height*3);
        for(VaultRanking.View view:VaultRanking.View.values())
        {
            Widget w=left.createChild(-1,WidgetType.TEXT);
            w.setText(view.label).setFontId(coral.getFontId()).setTextColor(0xff981f).setTextShadowed(true)
                .setOriginalX(x).setOriginalY(y).setOriginalWidth(width).setOriginalHeight(height);
            w.setXTextAlignment(0);w.setYTextAlignment(1);w.setHasListener(true);
            w.setAction(0,"View");
            w.setOnOpListener((JavaScriptCallback)e->{active=view;dirty=true;paintButtons();tick();});
            w.revalidate();buttons.put(view,w);y+=height;
        }
        paintButtons();return true;
    }
    private void paintButtons()
    {for(Map.Entry<VaultRanking.View,Widget> e:buttons.entrySet())e.getValue().setTextColor(e.getKey()==active?0xffffff:0xff981f);}

    private void removeButtons()
    {
        if(parent!=null)
        {
            Widget[] children=parent.getChildren();
            if(children!=null)
            {
                Widget[] copy=children.clone();boolean changed=false;
                for(Widget w:buttons.values())
                {
                    int i=w.getIndex();
                    if(i>=0&&i<copy.length&&copy[i]==w){w.setHidden(true);copy[i]=null;changed=true;}
                }
                if(changed)
                {
                    int n=copy.length;while(n>originalChildCount&&copy[n-1]==null)n--;
                    parent.setChildren(Arrays.copyOf(copy,n));
                    if(originalScrollHeight>=0)parent.setScrollHeight(originalScrollHeight);
                }
            }
        }
        buttons.clear();parent=null;originalScrollHeight=-1;
    }

    private void restore()
    {
        // Only restore widgets in the same native generation. References to a
        // destroyed/rebuilt widget must never be applied to its replacement.
        for(Map.Entry<Widget,Boolean> e:hidden.entrySet())
        {
            Widget w=e.getKey(),p=client.getWidget(w.getId());
            Widget[] children=p==null?null:p.getChildren();int i=w.getIndex();
            if(children!=null&&i>=0&&i<children.length&&children[i]==w)w.setHidden(e.getValue());
        }
        hidden.clear();
    }

    private void filter(int level)
    {
        restore();
        Widget itemParent=client.getWidget(ITEMS),textParent=client.getWidget(LABELS);
        if(itemParent==null||textParent==null)return;
        Widget[] icons=itemParent.getChildren(),labels=textParent.getChildren();
        if(icons==null||labels==null)return;
        List<VaultRanking.Entry> entries=new ArrayList<>();
        List<Widget[]> pairs=new ArrayList<>();
        for(Widget icon:icons)
        {
            if(icon==null||icon.isSelfHidden()||icon.getItemId()<=0||icon.getItemQuantity()<=0)continue;
            int i=icon.getIndex();
            if(i<0||i>=labels.length||!pair(icon,labels[i]))continue;
            String actual=items.getItemComposition(icon.getItemId()).getName();
            if(!clean(actual).equalsIgnoreCase(clean(labels[i].getText())))continue;
            pairs.add(new Widget[]{icon,labels[i]});
            entries.add(new VaultRanking.Entry(icon.getItemId(),icon.getItemQuantity(),VaultRanking.resolve(icon.getItemId(),actual)));
        }
        Set<Integer> keep=VaultRanking.select(active,entries,level,plugin.vaultAllowed(level),plugin::priority,plugin.vaultOverrides(level));
        for(Widget[] pair:pairs)if(!keep.contains(pair[0].getItemId()))for(Widget w:pair)
        {hidden.put(w,w.isSelfHidden());w.setHidden(true);}
        // Ambiguous/unpaired widgets are untouched; the native headers and
        // scroll geometry are unchanged. Compaction requires live menu tests.
    }
    static boolean pair(Widget icon,Widget label)
    {
        return label!=null&&!label.isSelfHidden()&&label.getIndex()==icon.getIndex()&&label.getType()==WidgetType.TEXT
            && icon.getType()==WidgetType.GRAPHIC && Math.abs(label.getOriginalX()-(icon.getOriginalX()-7))<=2
            && Math.abs(label.getOriginalY()-(icon.getOriginalY()+icon.getOriginalHeight()))<=2;
    }
    static String clean(String text){return text==null?"":text.replaceAll("(?i)<br\\s*/?>"," ").replaceAll("<[^>]*>","").replaceAll("\\s+"," ").trim();}
}
