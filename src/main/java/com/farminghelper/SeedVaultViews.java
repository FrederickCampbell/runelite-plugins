package com.farminghelper;

import java.util.*;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.Point;
import net.runelite.api.ScriptID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.client.game.ItemManager;

/** Plugin-local views, adapted from the visual repositioning technique in
 * RuneLite Bank Tags LayoutManager (BSD-2-Clause). Never replace, reindex or
 * edit native withdrawal widgets, quantities, actions or listeners. */
final class SeedVaultViews
{
    static final int GROUP=631, INIT=2814, CATEGORY=2815, BUILD=2819;
    static final int LEFT=(GROUP<<16)|7, CATEGORIES=(GROUP<<16)|9, ITEMS=(GROUP<<16)|18, LABELS=(GROUP<<16)|19;
    static final int HEADERS=(GROUP<<16)|16, LINES=(GROUP<<16)|17, VIEWPORT=(GROUP<<16)|12, SCROLLBAR=(GROUP<<16)|11;
    private static final Set<String> STANDARD_HEADERS=Set.of("allotment","allotments","flowers","flower","herbs","hops","bushes","trees","fruit trees","exotic","anima","saplings","coral");
    private final Client client;
    private final ItemManager items;
    private final FarmingHelperPlugin plugin;
    private final Map<Widget,WidgetState> changed=new IdentityHashMap<>();
    private Widget scrollParent;
    private int nativeHeight,nativeScroll,nativeVarScroll,appliedHeight=-1,customScroll,lastWidth=-1;
    private boolean scrollbarDirty;
    private final EnumMap<VaultRanking.View,Widget> buttons=new EnumMap<>(VaultRanking.View.class);
    private final List<Widget> decorations=new ArrayList<>();
    private Set<Integer> runItems;
    private boolean opened;
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
        if(id==INIT||id==CATEGORY){leave();removeButtons();if(id==INIT)opened=false;building=true;}
        else if(id==BUILD){rememberScroll();restore(false);building=true;dirty=true;}
    }
    void afterScript(int id)
    {
        if(id==INIT||id==CATEGORY||id==BUILD){building=false;dirty=true;}
    }
    void nativeMenu(int component)
    {
        if(component==CATEGORIES||component==InterfaceID.SeedVault.SEARCH)leave();
    }
    void close(){leave();removeButtons();building=false;lastLevel=-1;lastConfig=Long.MIN_VALUE;lastWidth=-1;runItems=null;opened=false;dirty=true;}
    void leave(){restore(true);active=null;paintButtons();dirty=true;customScroll=0;}
    private void rememberScroll(){if(active!=null&&scrollParent!=null&&client.getWidget(VIEWPORT)==scrollParent)customScroll=scrollParent.getScrollY();}

    void tick()
    {
        if(client.getGameState()!=GameState.LOGGED_IN||!plugin.flag("seedVaultViews",true)) {close();flushScrollbar();return;}
        Widget root=client.getWidget(InterfaceID.SeedVault.UNIVERSE);
        if(root==null||root.isHidden()){close();return;}
        if(building)return;
        Widget left=client.getWidget(LEFT), categories=client.getWidget(CATEGORIES);
        if(left==null||categories==null||categories.isHidden())return;
        Set<Integer> nextRun=plugin.vaultRunItems();
        boolean starting=runItems==null&&nextRun!=null;
        boolean changedRun=(runItems==null)!=(nextRun==null);
        if(!Objects.equals(runItems,nextRun)){runItems=nextRun;dirty=true;}
        if(runItems==null&&active==VaultRanking.View.RUN)leave();
        if(left!=parent||changedRun||!buttonsIntact())
        {
            rememberScroll();restore(false);removeButtons();
            if(!addButtons(left,categories))return;
            dirty=true;
        }
        if((!opened||starting)&&runItems!=null)select(VaultRanking.View.RUN);
        opened=true;
        int level=client.getRealSkillLevel(Skill.FARMING);
        long revision=plugin.preferenceRevision();
        if(lastLevel!=level||lastConfig!=revision){lastLevel=level;lastConfig=revision;dirty=true;}
        Widget viewport=client.getWidget(VIEWPORT);
        if(viewport!=null&&lastWidth!=viewport.getWidth()){lastWidth=viewport.getWidth();dirty=true;}
        if(dirty&&active!=null){dirty=false;rememberScroll();filter(level);}
        flushScrollbar();
    }

    void flushScrollbar()
    {
        Widget viewport=client.getWidget(VIEWPORT);
        if(scrollbarDirty&&client.getGameState()==GameState.LOGGED_IN&&viewport!=null&&!viewport.isHidden()&&client.getWidget(SCROLLBAR)!=null)
        {
            scrollbarDirty=false;
            // Generic RuneLite scrollbar script, outside the native build/on-op callback.
            client.runScript(ScriptID.UPDATE_SCROLLBAR,SCROLLBAR,VIEWPORT,viewport.getScrollY());
        }
    }

    private boolean buttonsIntact()
    {
        if(parent==null||buttons.size()!=(runItems==null?3:4))return false;
        Widget[] children=parent.getChildren();
        if(children==null)return false;
        for(Widget w:buttons.values())if(w.getIndex()<0||w.getIndex()>=children.length||children[w.getIndex()]!=w)return false;
        for(Widget w:decorations)if(w.getIndex()<0||w.getIndex()>=children.length||children[w.getIndex()]!=w)return false;
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
        // Separate native categories from ours, preserving the alternating row phase.
        int dividerY=y+2; int gap=5;
        Widget dark=nativeBackground(categories,coral);
        Widget light=null;
        for(Widget row:nativeRows)if(row!=null&&"Saplings".equalsIgnoreCase(clean(row.getText())))light=nativeBackground(categories,row);
        decoration(left,WidgetType.RECTANGLE,Math.max(0,x-3),dividerY,Math.max(1,width-2),1,null,0x78654b,115);
        y+=gap;
        // Do not alter native scrolling scripts; extend only if necessary.
        int bottom=y+height*(runItems==null?3:4)+(runItems==null?0:5);
        if(bottom>left.getScrollHeight()&&bottom>left.getHeight())left.setScrollHeight(bottom);
        for(VaultRanking.View view:VaultRanking.View.values())
        {
            if(view==VaultRanking.View.RUN){if(runItems==null)continue;decoration(left,WidgetType.RECTANGLE,Math.max(0,x-3),y+2,Math.max(1,width-2),1,null,0x78654b,115);y+=5;}
            boolean darker=view==VaultRanking.View.TOP_THREE||view==VaultRanking.View.RUN;
            Widget template=darker?dark:light;
            if(template!=null||darker)
            {
                int bx=template==null?categories.getRelativeX():x-coral.getRelativeX()+template.getRelativeX();
                int bw=template==null?categories.getWidth():template.getWidth();
                decoration(left,template==null?WidgetType.RECTANGLE:template.getType(),bx,y,bw,height,template,0x000000,192);
            }
            Widget w=left.createChild(-1,WidgetType.TEXT);
            w.setText(view.label).setFontId(coral.getFontId()).setTextColor(0xff981f).setTextShadowed(true)
                .setOriginalX(x).setOriginalY(y).setOriginalWidth(width).setOriginalHeight(height);
            w.setXTextAlignment(0);w.setYTextAlignment(1);w.setHasListener(true);
            w.setAction(0,"View");
            w.setOnOpListener((JavaScriptCallback)e->select(view));
            w.revalidate();buttons.put(view,w);y+=height;
        }
        paintButtons();return true;
    }
    private Widget nativeBackground(Widget categories,Widget row)
    {
        Widget layer=client.getWidget((GROUP<<16)|8);
        if(layer==null)return null;
        int rowY=categories.getRelativeY()+row.getRelativeY();
        for(Widget w:children(layer))if(w!=null&&!w.isSelfHidden()&&
            (w.getType()==WidgetType.RECTANGLE||w.getType()==WidgetType.GRAPHIC)&&
            Math.abs(layer.getRelativeY()+w.getRelativeY()-rowY)<=1&&
            Math.abs(w.getHeight()-row.getHeight())<=2&&w.getWidth()>0)return w;
        return null;
    }
    private void decoration(Widget left,int type,int x,int y,int width,int height,Widget template,int color,int opacity)
    {
        Widget w=left.createChild(-1,type);
        w.setOriginalX(x).setOriginalY(y).setOriginalWidth(width).setOriginalHeight(height);
        if(type==WidgetType.GRAPHIC&&template!=null)
        {w.setSpriteId(template.getSpriteId());w.setSpriteTiling(template.getSpriteTiling());w.setOpacity(template.getOpacity());}
        else
        {w.setFilled(true);w.setTextColor(template==null?color:template.getTextColor());w.setOpacity(template==null?opacity:template.getOpacity());}
        w.revalidate();decorations.add(w);
    }
    void select(VaultRanking.View view)
    {if(view==VaultRanking.View.RUN&&runItems==null)return;if(active!=view){restore(true);customScroll=0;}active=view;dirty=true;paintButtons();}
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
                List<Widget> owned=new ArrayList<>(buttons.values());owned.addAll(decorations);
                for(Widget w:owned)
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
        buttons.clear();decorations.clear();parent=null;originalScrollHeight=-1;
    }

    private static final class WidgetState
    {
        final int x,y;final boolean hidden;
        int appliedX,appliedY;boolean appliedHidden;
        WidgetState(Widget w){x=appliedX=w.getOriginalX();y=appliedY=w.getOriginalY();hidden=appliedHidden=w.isSelfHidden();}
        void apply(Widget w,int x,int y,boolean hide)
        {
            appliedX=x;appliedY=y;appliedHidden=hide;
            w.setOriginalX(x);w.setOriginalY(y);w.setHidden(hide);w.revalidate();
        }
        void restore(Widget w)
        {
            // Respect a native rebuild or another plugin that changed a property.
            if(w.getOriginalX()==appliedX)w.setOriginalX(x);
            if(w.getOriginalY()==appliedY)w.setOriginalY(y);
            if(w.isSelfHidden()==appliedHidden)w.setHidden(hidden);
            w.revalidate();
        }
    }
    private void move(Widget w,int x,int y,boolean hide)
    {changed.computeIfAbsent(w,WidgetState::new).apply(w,x,y,hide);}
    private void hide(Widget w){move(w,w.getOriginalX(),w.getOriginalY(),true);}
    private void restore(boolean exit)
    {
        for(Map.Entry<Widget,WidgetState> e:changed.entrySet())
        {
            Widget w=e.getKey(),p=client.getWidget(w.getId());
            Widget[] children=p==null?null:p.getChildren();int i=w.getIndex();
            if(children!=null&&i>=0&&i<children.length&&children[i]==w)e.getValue().restore(w);
        }
        changed.clear();
        if(scrollParent!=null&&client.getWidget(VIEWPORT)==scrollParent&&appliedHeight==scrollParent.getScrollHeight())
        {
            scrollParent.setScrollHeight(nativeHeight);scrollParent.setScrollY(nativeScroll);scrollParent.revalidateScroll();
            client.setVarcIntValue(VarClientID.SEED_VAULT_SCROLLPOS,nativeVarScroll);
            scrollbarDirty=true;
        }
        scrollParent=null;appliedHeight=-1;
        if(exit)customScroll=0;
    }
    private static final class Pair
    {
        final Widget icon,label;final int id;final Crop crop;
        Pair(Widget icon,Widget label,Crop crop){this.icon=icon;this.label=label;id=icon.getItemId();this.crop=crop;}
    }
    private static final class Section
    {
        final Widget header;final int y;
        Widget line;boolean safe=true;
        final List<Pair> pairs=new ArrayList<>();
        Section(Widget header,int y){this.header=header;this.y=y;}
    }
    private static boolean absolute(Widget w){return w.getXPositionMode()==0&&w.getYPositionMode()==0;}
    private static Widget[] children(Widget w){return w==null||w.getChildren()==null?new Widget[0]:w.getChildren();}
    private static int yOffset(Widget from,Widget to)
    {
        Point a=from.getCanvasLocation(),b=to.getCanvasLocation();
        return a==null||b==null?Integer.MIN_VALUE:a.getY()-b.getY();
    }
    private static Section sectionAt(List<Section> sections,int y)
    {
        Section result=null;for(Section section:sections){if(section.y>y)break;result=section;}return result;
    }
    void filter(int level)
    {
        restore(false);
        Widget itemParent=client.getWidget(ITEMS),textParent=client.getWidget(LABELS),headerParent=client.getWidget(HEADERS),lineParent=client.getWidget(LINES),viewport=client.getWidget(VIEWPORT);
        if(itemParent==null||textParent==null||headerParent==null||viewport==null)return;
        int headerOffset=yOffset(headerParent,itemParent);
        if(headerOffset==Integer.MIN_VALUE||yOffset(textParent,itemParent)!=0)return;
        Point iconBase=itemParent.getCanvasLocation(),labelBase=textParent.getCanvasLocation();
        if(iconBase==null||labelBase==null||iconBase.getX()!=labelBase.getX())return;
        List<Section> sections=new ArrayList<>();
        boolean unknownHeader=false;
        for(Widget w:children(headerParent))if(w!=null&&!w.isSelfHidden()&&w.getType()==WidgetType.TEXT)
        {
            String name=clean(w.getText()).toLowerCase(Locale.ROOT);
            if(STANDARD_HEADERS.contains(name)&&absolute(w))sections.add(new Section(w,w.getOriginalY()+headerOffset));
            else if(!name.isEmpty()&&!name.equals("farming contract")&&!name.equals("favourite slots"))unknownHeader=true;
        }
        sections.sort(Comparator.comparingInt(a->a.y));
        if(sections.isEmpty()||unknownHeader)return; // cannot distinguish ordinary items from native contract/favourite controls
        for(int i=1;i<sections.size();i++)if(sections.get(i).y<=sections.get(i-1).y)return;
        Widget[] icons=children(itemParent),labels=children(textParent);
        Set<Widget> matched=Collections.newSetFromMap(new IdentityHashMap<>());
        List<VaultRanking.Entry> entries=new ArrayList<>();
        for(Widget icon:icons)
        {
            if(icon==null||icon.isSelfHidden())continue;
            Section section=sectionAt(sections,icon.getOriginalY());
            if(section==null)continue; // native top section is never changed
            int i=icon.getIndex();
            if(icon.getItemId()<=0||i<0||i>=labels.length||!pair(icon,labels[i])||!absolute(icon)||!absolute(labels[i])){section.safe=false;continue;}
            String actual=items.getItemComposition(icon.getItemId()).getName();
            if(!clean(actual).equalsIgnoreCase(clean(labels[i].getText()))){section.safe=false;continue;}
            Pair p=new Pair(icon,labels[i],VaultRanking.resolve(icon.getItemId(),actual));
            matched.add(p.label);section.pairs.add(p);
            entries.add(new VaultRanking.Entry(p.id,icon.getItemQuantity(),p.crop));
        }
        for(Widget label:labels)if(label!=null&&!label.isSelfHidden()&&!matched.contains(label))
        {Section section=sectionAt(sections,label.getOriginalY());if(section!=null)section.safe=false;}
        Set<Integer> keep=active==VaultRanking.View.RUN?VaultRanking.selectRun(entries,runItems==null?Collections.emptySet():runItems):
            VaultRanking.select(active,entries,level,plugin.vaultAllowed(level),plugin::priority,plugin.vaultOverrides(level));
        Map<Integer,Integer> rank=new HashMap<>();int index=0;for(int id:keep)rank.put(id,index++);
        // Full compaction requires all section widgets to be accounted for and the
        // native divider paired by index and nearby original Y. Otherwise compact
        // only verified item grids, leaving native section geometry in place.
        boolean full=lineParent!=null;
        int lineOffset=lineParent==null?Integer.MIN_VALUE:yOffset(lineParent,itemParent);
        int layerOffset=yOffset(itemParent,viewport);
        if(layerOffset!=Integer.MIN_VALUE)layerOffset+=viewport.getScrollY();
        int end=layerOffset==Integer.MIN_VALUE?Integer.MIN_VALUE:viewport.getScrollHeight()-layerOffset;
        if(lineOffset==Integer.MIN_VALUE||layerOffset==Integer.MIN_VALUE||end<=sections.get(sections.size()-1).y)full=false;
        Widget[] lines=children(lineParent);
        Set<Widget> usedLines=Collections.newSetFromMap(new IdentityHashMap<>());
        for(int j=0;j<sections.size();j++)
        {
            Section section=sections.get(j);int bottom=j+1<sections.size()?sections.get(j+1).y:end;
            Set<Long> positions=new HashSet<>();
            Pair first=section.pairs.isEmpty()?null:section.pairs.get(0);
            for(Pair p:section.pairs)
            {
                long key=((long)p.icon.getOriginalX()<<32)|(p.icon.getOriginalY()&0xffffffffL);
                if(!positions.add(key)||p.icon.getWidth()!=first.icon.getWidth()||p.icon.getHeight()!=first.icon.getHeight()||
                    p.label.getWidth()!=first.label.getWidth()||p.label.getHeight()!=first.label.getHeight()||
                    p.icon.getOriginalY()<section.y+section.header.getHeight()||
                    bottom!=Integer.MIN_VALUE&&p.label.getOriginalY()+p.label.getHeight()>bottom)section.safe=false;
            }
            int i=section.header.getIndex();Widget line=i>=0&&i<lines.length?lines[i]:null;
            if(line!=null&&!line.isSelfHidden()&&absolute(line)&&lineOffset!=Integer.MIN_VALUE&&
                Math.abs(line.getOriginalY()+lineOffset-section.y)<=Math.max(32,section.header.getHeight()+8))
            {section.line=line;usedLines.add(line);}
            else full=false;
            if(!section.safe)full=false;
        }
        for(Widget line:lines)if(line!=null&&!line.isSelfHidden()&&lineOffset!=Integer.MIN_VALUE&&
            sectionAt(sections,line.getOriginalY()+lineOffset)!=null&&!usedLines.contains(line))full=false;
        int shift=0;
        for(int j=0;j<sections.size();j++)
        {
            Section section=sections.get(j);int bottom=j+1<sections.size()?sections.get(j+1).y:end;
            List<VaultGrid.Slot> slots=new ArrayList<>();
            for(int i=0;i<section.pairs.size();i++)
            {
                Pair p=section.pairs.get(i);boolean selected=keep.contains(p.id)&&p.icon.getItemQuantity()>0;
                if(!selected){hide(p.icon);hide(p.label);}
                slots.add(new VaultGrid.Slot(i,p.icon.getOriginalX(),p.icon.getOriginalY(),selected,rank.getOrDefault(p.id,Integer.MAX_VALUE)));
            }
            if(!section.safe)continue;
            VaultGrid.Layout layout=VaultGrid.layout(new VaultGrid.Section(section.y,bottom,slots),full,shift);
            if(full)
            {
                move(section.header,section.header.getOriginalX(),section.header.getOriginalY()-shift,layout.hideHeader);
                move(section.line,section.line.getOriginalX(),section.line.getOriginalY()-shift,layout.hideHeader);
            }
            for(VaultGrid.Move m:layout.moves)
            {
                Pair p=section.pairs.get(m.key);int dx=p.label.getOriginalX()-p.icon.getOriginalX(),dy=p.label.getOriginalY()-p.icon.getOriginalY();
                move(p.icon,m.x,m.y,false);move(p.label,m.x+dx,m.y+dy,false);
            }
            shift+=layout.saved;
        }
        // Reset to the top on initial selection, or retain the local scroll on rebuild.
        {
            scrollParent=viewport;nativeHeight=viewport.getScrollHeight();nativeScroll=viewport.getScrollY();
            nativeVarScroll=client.getVarcIntValue(VarClientID.SEED_VAULT_SCROLLPOS);
            appliedHeight=Math.max(viewport.getHeight(),nativeHeight-(full?shift:0));
            viewport.setScrollHeight(appliedHeight);
            int scroll=Math.max(0,Math.min(customScroll,appliedHeight-viewport.getHeight()));
            viewport.setScrollY(scroll);viewport.revalidateScroll();
            client.setVarcIntValue(VarClientID.SEED_VAULT_SCROLLPOS,scroll);scrollbarDirty=true;
        }
    }
    static boolean pair(Widget icon,Widget label)
    {
        return label!=null&&!label.isSelfHidden()&&label.getIndex()==icon.getIndex()&&label.getType()==WidgetType.TEXT
            && icon.getType()==WidgetType.GRAPHIC && Math.abs(label.getOriginalX()-(icon.getOriginalX()-7))<=2
            && Math.abs(label.getOriginalY()-(icon.getOriginalY()+icon.getOriginalHeight()))<=2;
    }
    static String clean(String text){return text==null?"":text.replaceAll("(?i)<br\\s*/?>"," ").replaceAll("<[^>]*>","").replaceAll("\\s+"," ").trim();}
}
