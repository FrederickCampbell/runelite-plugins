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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Plugin-local views, adapted from the visual repositioning technique in
 * RuneLite Bank Tags LayoutManager (BSD-2-Clause). Never replace, reindex or
 * edit native withdrawal widgets, quantities, actions or listeners. */
final class SeedVaultViews
{
    static final int GROUP=InterfaceID.SEED_VAULT, INIT=2818, CATEGORY=2814, BUILD=ScriptID.SEED_VAULT_BUILD;
    // The nested build procedure is also called directly from resize/search scripts.
    static final int BUILD_CONTENTS=2820;
    private static final Logger LOG=LoggerFactory.getLogger(SeedVaultViews.class);
    static final int LEFT=InterfaceID.SeedVault.LEFT_LIST, CATEGORIES=InterfaceID.SeedVault.CATEGORY_LIST,
        ITEMS=InterfaceID.SeedVault.OBJ_LIST, LABELS=InterfaceID.SeedVault.TEXT_LIST;
    static final int HEADERS=InterfaceID.SeedVault.CATEGORY_HEADERS, LINES=InterfaceID.SeedVault.CATEGORY_LINES,
        VIEWPORT=InterfaceID.SeedVault.LIST, SCROLLBAR=InterfaceID.SeedVault.RIGHT_SCROLL;
    private static final Set<String> STANDARD_HEADERS=Set.of("allotment","allotments","flowers","flower","herbs","hops","bushes","trees","fruit trees","exotic","anima","saplings","coral");
    private final Client client;
    private final ItemManager items;
    private final FarmingHelperPlugin plugin;
    private final Map<Widget,WidgetState> changed=new IdentityHashMap<>();
    private Widget scrollParent;
    private Widget[] projectedHeaders,projectedIcons,projectedLabels,projectedLines;
    private int nativeHeight,nativeScroll,nativeVarScroll,appliedHeight=-1,customScroll,lastWidth=-1;
    private boolean scrollbarDirty;
    private final EnumMap<VaultRanking.View,Widget> buttons=new EnumMap<>(VaultRanking.View.class);
    private final List<Widget> decorations=new ArrayList<>();
    private Set<Integer> runItems;
    private boolean opened;
    private Widget parent;
    private int originalScrollHeight=-1,appliedLeftHeight=-1,originalChildCount;
    private VaultRanking.View active;
    private boolean dirty=true, quantityDirty, nativeRefresh;
    private int buildDepth;
    private int lastLevel=-1;
    private long lastConfig=Long.MIN_VALUE,lastAccess=Long.MIN_VALUE;

    SeedVaultViews(Client client,ItemManager items,FarmingHelperPlugin plugin)
    {this.client=client;this.items=items;this.plugin=plugin;}

    void invalidate(){quantityDirty=true;}
    private static boolean nativeBuild(int id){return id==INIT||id==CATEGORY||id==BUILD||id==BUILD_CONTENTS;}
    void beforeScript(int id)
    {
        if(!nativeBuild(id))return;
        // Native scripts own item identity and quantity. Restore before they rebuild,
        // but do not confuse a deposit/withdraw rebuild with a player selecting a tab.
        if(buildDepth++==0){rememberScroll();restore(false);}
        // INIT can replace the left panel. Ordinary category and item builds
        // retain our own buttons unless the native interface actually replaces them.
        if(id==INIT||id==CATEGORY)removeButtons();
        dirty=true;
        LOG.debug("Farming vault pre {} depth {}",id,buildDepth);
    }
    void afterScript(int id)
    {
        if(!nativeBuild(id))return;
        if(buildDepth>0)--buildDepth;
        dirty=true;nativeRefresh=true;
        LOG.debug("Farming vault post {} depth {}",id,buildDepth);
    }
    void postClientTick()
    {
        if(buildDepth!=0)return;
        if(nativeRefresh||quantityDirty)
        {
            nativeRefresh=false;
            refresh();
        }
        flushScrollbar();
    }
    private Set<Integer> selection(List<VaultRanking.Entry> entries,int level)
    {
        return active==VaultRanking.View.RUN?VaultRanking.selectRun(entries,runItems==null?Collections.emptySet():runItems):
            VaultRanking.select(active,entries,level,plugin.vaultAllowed(level),plugin::priority,plugin.vaultOverrides(level));
    }
    void nativeMenu(int component)
    {
        if(component==CATEGORIES||component==InterfaceID.SeedVault.SEARCH)leave();
    }
    void close(){leave();removeButtons();buildDepth=0;nativeRefresh=false;quantityDirty=false;lastLevel=-1;lastConfig=Long.MIN_VALUE;lastAccess=Long.MIN_VALUE;lastWidth=-1;runItems=null;opened=false;dirty=true;}
    void leave(){restore(true);active=null;paintButtons();dirty=true;customScroll=0;}
    private void rememberScroll(){if(active!=null&&scrollParent!=null&&client.getWidget(VIEWPORT)==scrollParent)customScroll=scrollParent.getScrollY();}

    void tick()
    {
        if(scrollParent!=null)
        {
            Widget[] headers=children(client.getWidget(HEADERS));
            if(scrollParent!=client.getWidget(VIEWPORT)||scrollParent.getScrollHeight()!=appliedHeight||
                !Arrays.equals(projectedHeaders,headers)||
                !Arrays.equals(projectedIcons,children(client.getWidget(ITEMS)))||
                !Arrays.equals(projectedLabels,children(client.getWidget(LABELS)))||
                !Arrays.equals(projectedLines,children(client.getWidget(LINES))))dirty=true;
            else for(Map.Entry<Widget,WidgetState> entry:changed.entrySet())
                if(!entry.getValue().matches(entry.getKey())){dirty=true;break;}
        }
        refresh();
        if(buildDepth==0)flushScrollbar();
    }

    /** Apply the projection only after native scripts finish rebuilding their children and viewport. */
    void refresh()
    {
        if(buildDepth>0)return;
        if(client.getGameState()!=GameState.LOGGED_IN||!plugin.flag("seedVaultViews",true)) {if(opened||parent!=null)close();return;}
        Widget root=client.getWidget(InterfaceID.SeedVault.UNIVERSE);
        if(root==null||root.isHidden()){if(opened||parent!=null)close();return;}
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
        long revision=plugin.preferenceRevision(),access=plugin.vaultAccessRevision();
        if(lastLevel!=level||lastConfig!=revision||lastAccess!=access)
        {lastLevel=level;lastConfig=revision;lastAccess=access;dirty=true;}
        // Do not project an empty/old eligibility cache during login or a level change.
        if(active!=null&&active!=VaultRanking.View.RUN&&!plugin.vaultAccessReady(level))
        {dirty=true;return;}
        Widget viewport=client.getWidget(VIEWPORT);
        if(viewport!=null&&lastWidth!=viewport.getWidth()){lastWidth=viewport.getWidth();dirty=true;}
        if(quantityDirty)
        {
            quantityDirty=false;
            dirty=true;
        }
        if(dirty&&active!=null)
        {
            rememberScroll();
            // Failed geometry validation leaves the refresh pending for the
            // post-client-tick fallback or next game tick.
            dirty=!filter(level);
            LOG.debug("Farming vault projection {} for {}",dirty?"not ready":"applied",active);
        }
    }

    void flushScrollbar()
    {
        if (!scrollbarDirty) return;
        Widget viewport=client.getWidget(VIEWPORT);
        if(client.getGameState()==GameState.LOGGED_IN&&viewport!=null&&!viewport.isHidden()&&client.getWidget(SCROLLBAR)!=null)
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
        appliedLeftHeight=left.getScrollHeight();
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
        Widget layer=client.getWidget(InterfaceID.SeedVault.CATEGORY_BACKGROUNDS);
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
    {if(view==VaultRanking.View.RUN&&runItems==null)return;if(active!=view){restore(true);customScroll=0;}active=view;dirty=true;nativeRefresh=true;paintButtons();}
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
                    if(originalScrollHeight>=0&&parent.getScrollHeight()==appliedLeftHeight)parent.setScrollHeight(originalScrollHeight);
                }
            }
        }
        buttons.clear();decorations.clear();parent=null;originalScrollHeight=-1;appliedLeftHeight=-1;
    }

    private static final class WidgetState
    {
        final int x,y,itemId;final boolean hidden;
        int appliedX,appliedY;boolean appliedHidden;
        WidgetState(Widget w){x=appliedX=w.getOriginalX();y=appliedY=w.getOriginalY();hidden=appliedHidden=w.isSelfHidden();itemId=w.getItemId();}
        void apply(Widget w,int x,int y,boolean hide)
        {
            appliedX=x;appliedY=y;appliedHidden=hide;
            if(w.getOriginalX()!=x||w.getOriginalY()!=y||w.isSelfHidden()!=hide)
            {w.setOriginalX(x);w.setOriginalY(y);w.setHidden(hide);w.revalidate();}
        }
        boolean matches(Widget w){return w.getItemId()==itemId&&w.getOriginalX()==appliedX&&w.getOriginalY()==appliedY&&w.isSelfHidden()==appliedHidden;}
        void restore(Widget w)
        {
            // Respect a native rebuild or another plugin that changed a property.
            if(w.getItemId()!=itemId)return;
            boolean changed=false;
            if(w.getOriginalX()==appliedX&&appliedX!=x){w.setOriginalX(x);changed=true;}
            if(w.getOriginalY()==appliedY&&appliedY!=y){w.setOriginalY(y);changed=true;}
            if(w.isSelfHidden()==appliedHidden&&appliedHidden!=hidden){w.setHidden(hidden);changed=true;}
            if(changed)w.revalidate();
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
            scroll(scrollParent,nativeHeight,nativeScroll,nativeVarScroll);
        scrollParent=null;projectedHeaders=null;projectedIcons=null;projectedLabels=null;projectedLines=null;appliedHeight=-1;
        if(exit)customScroll=0;
    }
    private static final class Pair
    {
        final Widget icon,label;final int id,base;final boolean placeholder;
        Pair(Widget icon,Widget label,int base,boolean placeholder){this.icon=icon;this.label=label;id=icon.getItemId();this.base=base;this.placeholder=placeholder;}
    }
    private static final class Section
    {
        final Widget header;final int y;
        Widget line;boolean safe=true;
        final List<Pair> pairs=new ArrayList<>();
        Section(Widget header,int y){this.header=header;this.y=y;}
    }
    private static boolean absolute(Widget w){return w.getXPositionMode()==0&&w.getYPositionMode()==0;}
    private static final Widget[] NO_CHILDREN = new Widget[0];
    private static Widget[] children(Widget w)
    {
        Widget[] children = w == null ? null : w.getChildren();
        return children == null ? NO_CHILDREN : children;
    }
    private static int yOffset(Widget from,Widget to)
    {
        Point a=from.getCanvasLocation(),b=to.getCanvasLocation();
        return a==null||b==null?Integer.MIN_VALUE:a.getY()-b.getY();
    }
    private static Section sectionAt(List<Section> sections,int y)
    {
        Section result=null;for(Section section:sections){if(section.y>y)break;result=section;}return result;
    }
    boolean filter(int level)
    {
        restore(false);
        Widget itemParent=client.getWidget(ITEMS),textParent=client.getWidget(LABELS),headerParent=client.getWidget(HEADERS),lineParent=client.getWidget(LINES),viewport=client.getWidget(VIEWPORT);
        if(itemParent==null||textParent==null||headerParent==null||viewport==null)return false;
        int headerOffset=yOffset(headerParent,itemParent);
        if(headerOffset==Integer.MIN_VALUE||yOffset(textParent,itemParent)!=0)return false;
        Point iconBase=itemParent.getCanvasLocation(),labelBase=textParent.getCanvasLocation();
        if(iconBase==null||labelBase==null||iconBase.getX()!=labelBase.getX())return false;
        List<Section> sections=new ArrayList<>();
        boolean unknownHeader=false;
        for(Widget w:children(headerParent))if(w!=null&&!w.isSelfHidden()&&w.getType()==WidgetType.TEXT)
        {
            String name=clean(w.getText()).toLowerCase(Locale.ROOT);
            if(STANDARD_HEADERS.contains(name)&&absolute(w))sections.add(new Section(w,w.getOriginalY()+headerOffset));
            else if(!name.isEmpty()&&!name.equals("farming contract")&&!name.equals("favourite slots"))unknownHeader=true;
        }
        sections.sort(Comparator.comparingInt(a->a.y));
        if(sections.isEmpty()||unknownHeader)return false; // cannot distinguish ordinary items from native contract/favourite controls
        for(int i=1;i<sections.size();i++)if(sections.get(i).y<=sections.get(i-1).y)return false;
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
            net.runelite.api.ItemComposition definition=items.getItemComposition(icon.getItemId());
            if(definition==null){section.safe=false;continue;}
            String actual=definition.getName();
            if(!clean(actual).equalsIgnoreCase(clean(labels[i].getText()))){section.safe=false;continue;}
            boolean placeholder=definition.getPlaceholderTemplateId()!=-1;
            Pair p=new Pair(icon,labels[i],placeholder?definition.getPlaceholderId():icon.getItemId(),placeholder);
            matched.add(p.label);section.pairs.add(p);
            // Native placeholders retain their widgets, labels and Remove-Placeholder actions.
            // Only actual stacks participate in crop rankings or satisfy a run.
            if (!p.placeholder) entries.add(new VaultRanking.Entry(p.id,icon.getItemQuantity(),VaultRanking.resolve(p.id,actual)));
        }
        for(Widget label:labels)if(label!=null&&!label.isSelfHidden()&&!matched.contains(label))
        {Section section=sectionAt(sections,label.getOriginalY());if(section!=null)section.safe=false;}
        Set<Integer> keep=selection(entries,level);
        Map<Integer,Integer> rank=new HashMap<>();int index=0;for(int id:keep)rank.put(id,index++);
        // Full compaction requires all section widgets to be accounted for and the
        // native divider paired by index and nearby original Y. Otherwise compact
        // only verified item grids, leaving native section geometry in place.
        boolean full=lineParent!=null;
        int lineOffset=lineParent==null?Integer.MIN_VALUE:yOffset(lineParent,itemParent);
        int layerOffset=yOffset(itemParent,viewport);
        if(layerOffset!=Integer.MIN_VALUE)layerOffset+=viewport.getScrollY();
        // A native scroll size of zero denotes a list that fits the viewport.
        int nativeExtent=Math.max(viewport.getHeight(),viewport.getScrollHeight());
        int end=layerOffset==Integer.MIN_VALUE?Integer.MIN_VALUE:nativeExtent-layerOffset;
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
        // An unverified section must not produce a partially filtered Active Run.
        // Keep the restored native interface, including every required item.
        if(sections.stream().anyMatch(s->!s.safe))return true;
        int projectedExtent=nativeExtent;
        if(full)
        {
            List<Pair> all=new ArrayList<>();
            List<VaultGrid.Slot> slots=new ArrayList<>();
            Set<Integer> columns=new TreeSet<>();
            int rowHeight=1;
            for(Section section:sections)
            {
                for(Pair p:section.pairs)
                {
                    boolean selected=p.placeholder?placeholderRelevant(p.base,keep,active==VaultRanking.View.RUN?runItems:null):keep.contains(p.id);
                    slots.add(new VaultGrid.Slot(all.size(),p.icon.getOriginalX(),p.icon.getOriginalY(),selected,rank.getOrDefault(p.base,Integer.MAX_VALUE)));
                    columns.add(p.icon.getOriginalX());
                    rowHeight=Math.max(rowHeight,p.label.getOriginalY()-p.icon.getOriginalY()+p.label.getHeight());
                    all.add(p);
                }
            }
            // Check that the native columns do not overlap any item label before
            // using them across categories. Otherwise leave native layout intact.
            List<Integer> xs=new ArrayList<>(columns);
            int left=0,right=0;
            for(Pair p:all)
            {
                int dx=p.label.getOriginalX()-p.icon.getOriginalX();
                left=Math.max(left,-dx);right=Math.max(right,Math.max(p.icon.getWidth(),dx+p.label.getWidth()));
            }
            for(int i=1;i<xs.size();i++)if(xs.get(i)-xs.get(i-1)<left+right)return true;
            int start=sections.get(0).y+4;
            // Do not move into the native contract / favourites area.
            for(Widget label:labels)if(label!=null&&!label.isSelfHidden()&&label.getOriginalY()<sections.get(0).y
                &&label.getOriginalY()+label.getHeight()>start)return true;
            for(Section section:sections){hide(section.header);hide(section.line);}
            for(VaultGrid.Slot slot:slots)if(!slot.keep){Pair p=all.get(slot.key);hide(p.icon);hide(p.label);}
            List<VaultGrid.Move> moves=VaultGrid.flat(slots,xs,start,rowHeight+4);
            int bottom=sections.get(0).y;
            for(VaultGrid.Move m:moves)
            {
                Pair p=all.get(m.key);int dx=p.label.getOriginalX()-p.icon.getOriginalX(),dy=p.label.getOriginalY()-p.icon.getOriginalY();
                move(p.icon,m.x,m.y,false);move(p.label,m.x+dx,m.y+dy,false);
                bottom=Math.max(bottom,m.y+dy+p.label.getHeight()+4);
            }
            projectedExtent=Math.max(viewport.getHeight(),layerOffset+bottom);
        }
        else for(int j=0;j<sections.size();j++)
        {
            Section section=sections.get(j);int bottom=j+1<sections.size()?sections.get(j+1).y:end;
            // Unsupported native geometry is left entirely native, including
            // visibility. Never hide items in an unverified section.
            if(!section.safe)continue;
            List<VaultGrid.Slot> slots=new ArrayList<>();
            for(int i=0;i<section.pairs.size();i++)
            {
                Pair p=section.pairs.get(i);boolean selected=p.placeholder?placeholderRelevant(p.base,keep,active==VaultRanking.View.RUN?runItems:null):keep.contains(p.id);
                if(!selected){hide(p.icon);hide(p.label);}
                slots.add(new VaultGrid.Slot(i,p.icon.getOriginalX(),p.icon.getOriginalY(),selected,rank.getOrDefault(p.base,Integer.MAX_VALUE)));
            }
            VaultGrid.Layout layout=VaultGrid.layout(new VaultGrid.Section(section.y,bottom,slots),false,0);
            for(VaultGrid.Move m:layout.moves)
            {
                Pair p=section.pairs.get(m.key);int dx=p.label.getOriginalX()-p.icon.getOriginalX(),dy=p.label.getOriginalY()-p.icon.getOriginalY();
                move(p.icon,m.x,m.y,false);move(p.label,m.x+dx,m.y+dy,false);
            }
        }
        // Reset to the top on initial selection, or retain the local scroll on rebuild.
        {
            projectedHeaders=children(headerParent).clone();
            projectedIcons=icons.clone();projectedLabels=labels.clone();projectedLines=lines.clone();
            scrollParent=viewport;nativeHeight=viewport.getScrollHeight();nativeScroll=viewport.getScrollY();
            nativeVarScroll=client.getVarcIntValue(VarClientID.SEED_VAULT_SCROLLPOS);
            appliedHeight=projectedExtent;
            int scroll=Math.max(0,Math.min(customScroll,appliedHeight-viewport.getHeight()));
            scroll(viewport,appliedHeight,scroll,scroll);
        }
        return true;
    }
    private void scroll(Widget viewport,int height,int offset,int varc)
    {
        if(viewport.getScrollHeight()!=height || viewport.getScrollY()!=offset)
        {
            viewport.setScrollHeight(height);viewport.setScrollY(offset);viewport.revalidateScroll();
            scrollbarDirty=true;
        }
        if(client.getVarcIntValue(VarClientID.SEED_VAULT_SCROLLPOS)!=varc)
            client.setVarcIntValue(VarClientID.SEED_VAULT_SCROLLPOS,varc);
    }
    static boolean placeholderRelevant(int base,Set<Integer> keep,Set<Integer> run)
    {return keep.contains(base)||run!=null&&run.contains(base);}
    static boolean pair(Widget icon,Widget label)
    {
        return label!=null&&!label.isSelfHidden()&&label.getIndex()==icon.getIndex()&&label.getType()==WidgetType.TEXT
            && icon.getType()==WidgetType.GRAPHIC && Math.abs(label.getOriginalX()-(icon.getOriginalX()-7))<=2
            && Math.abs(label.getOriginalY()-(icon.getOriginalY()+icon.getOriginalHeight()))<=2;
    }
    static String clean(String text){return text==null?"":text.replaceAll("(?i)<br\\s*/?>"," ").replaceAll("<[^>]*>","").replaceAll("\\s+"," ").trim();}
}
