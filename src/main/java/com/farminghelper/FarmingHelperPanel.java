package com.farminghelper;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.util.HashMap;
import java.util.Map;
import java.awt.Font;
import java.awt.Component;
import java.awt.FlowLayout;
import java.util.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import static com.farminghelper.SidebarNavigation.Page.*;
import static com.farminghelper.FarmingIcons.Kind.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import javax.swing.*;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.api.gameval.ItemID;

final class FarmingHelperPanel extends PluginPanel
{
    private final FarmingHelperPlugin plugin;
    private final Map<String,Boolean> expanded = new HashMap<>();
    private FarmRunPatch lastActive;
    private final JPanel rows = new JPanel();
    private final JScrollPane scroll;
    private final JPanel toolbar=new JPanel(new BorderLayout(4,0));
    private final JPanel footer=new JPanel(new BorderLayout());
    private final SidebarNavigation navigation=new SidebarNavigation();
    private GuideView latestGuide;
    private long guideSession=Long.MIN_VALUE;
    private boolean requiredOpen=true,completedOpen=false;
    private JButton requirementsHeader;
    private boolean settingsFromRun=false,patchByLocation=true,showLocked=false,manualByLocation=false;
    private String patchSearch="",individualSearch="";
    private final Set<String> patchExpanded=new HashSet<>(),manualGroups=new HashSet<>(),presetExpanded=new HashSet<>();
    private PatchType priorityOpen;
    // BorderLayout.NORTH alone does not constrain a normal JPanel's preferred width.
    // Scrollable explicitly follows the viewport, including the scrollbar's width.
    static final class GuideScrollContent extends JPanel implements Scrollable
    {
        GuideScrollContent(){super(new BorderLayout());}
        @Override public Dimension getPreferredScrollableViewportSize(){return getPreferredSize();}
        @Override public int getScrollableUnitIncrement(java.awt.Rectangle r,int orientation,int direction){return 16;}
        @Override public int getScrollableBlockIncrement(java.awt.Rectangle r,int orientation,int direction){return Math.max(16,r.height-16);}
        @Override public boolean getScrollableTracksViewportWidth(){return true;}
        @Override public boolean getScrollableTracksViewportHeight(){return false;}
    }
    private static final Icon SATISFIED=new Icon()
    {
        public int getIconWidth(){return 14;}
        public int getIconHeight(){return 14;}
        public void paintIcon(java.awt.Component c,java.awt.Graphics g,int x,int y)
        {
            java.awt.Graphics2D p=(java.awt.Graphics2D)g.create();
            p.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            p.setColor(new Color(124,209,119));
            p.setStroke(new java.awt.BasicStroke(2f,java.awt.BasicStroke.CAP_ROUND,java.awt.BasicStroke.JOIN_ROUND));
            p.drawLine(x+2,y+7,x+6,y+11);p.drawLine(x+6,y+11,x+12,y+3);p.dispose();
        }
    };
    private GuideView previousGuide;
    private final Map<String,JTextArea> itemNames=new HashMap<>(),stepText=new HashMap<>();
    private final Map<String,JLabel> itemQuantities=new HashMap<>();
    private static final Color ACTIVE=FarmingTheme.ACTIVE, ACTIVE_TEXT=FarmingTheme.SUCCESS;
    private final Set<String> manual = new java.util.LinkedHashSet<>();
    private boolean groupedOpen=true,individualOpen=false;


    FarmingHelperPanel(FarmingHelperPlugin plugin)
    {
        super(false);
        this.plugin = plugin;
        setLayout(new BorderLayout());setBackground(FarmingTheme.BACKGROUND);
        toolbar.setBackground(FarmingTheme.CARD);toolbar.setBorder(BorderFactory.createEmptyBorder(5,5,5,5));
        footer.setBackground(FarmingTheme.BACKGROUND);add(toolbar,BorderLayout.NORTH);add(footer,BorderLayout.SOUTH);
        rows.setLayout(new BoxLayout(rows,BoxLayout.Y_AXIS));
        rows.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));
        JPanel top = new GuideScrollContent();
        top.add(rows, BorderLayout.NORTH);
        scroll = new JScrollPane(top);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        rows.setBackground(FarmingTheme.BACKGROUND);top.setBackground(FarmingTheme.BACKGROUND);
        scroll.getViewport().setBackground(FarmingTheme.BACKGROUND);add(scroll,BorderLayout.CENTER);
    }

    private void addRow(JComponent row)
    {
        JPanel wrapper = new JPanel(new BorderLayout())
        {
            @Override public Dimension getMinimumSize(){return new Dimension(0,super.getMinimumSize().height);}
            @Override public Dimension getMaximumSize(){return new Dimension(Integer.MAX_VALUE,getPreferredSize().height);}
        };
        wrapper.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        wrapper.setOpaque(false);
        wrapper.add(row);
        wrapper.setBorder(BorderFactory.createEmptyBorder(2,0,2,0));
        FarmingTheme.apply(row);rows.add(wrapper);
    }

    private void heading(String text)
    {
        JLabel label = new JLabel(text);
        label.setFont(FarmingTheme.font(Font.BOLD,12));
        label.setForeground(FarmingTheme.ACCENT);
        label.setBorder(BorderFactory.createEmptyBorder(5,3,1,3));addRow(label);
    }

    private void line(String text, boolean active)
    {
        JTextArea label = text(text,active ? FarmingTheme.ACCENT : Color.LIGHT_GRAY);
        label.setOpaque(active);
        label.setBackground(FarmingTheme.CARD);
        label.setForeground(active ? FarmingTheme.ACCENT : Color.LIGHT_GRAY);
        label.setFont(FarmingTheme.font(Font.PLAIN,12));
        label.setBorder(BorderFactory.createEmptyBorder(3,4,3,4));
        addRow(label);
    }

    private void button(String text, Runnable action)
    {
        JButton button = new JButton(text);
        button.setFont(FarmingTheme.font(Font.PLAIN,12));
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setBackground(FarmingTheme.INPUT);
        button.addActionListener(e -> action.run());
        addRow(button);
    }

    private void finish() { rows.revalidate(); rows.repaint(); toolbar.revalidate();toolbar.repaint();footer.revalidate();footer.repaint(); }

    private void header(String title,Runnable back,Runnable gear,boolean end)
    {
        toolbar.removeAll();
        if(back!=null)toolbar.add(FarmingIcons.button(BACK,"Back",FarmingTheme.MUTED,back),BorderLayout.WEST);
        JLabel label=new JLabel(title);label.setFont(FarmingTheme.font(Font.BOLD,14));label.setForeground(FarmingTheme.TEXT);label.setToolTipText(title);
        if(back==null&&navigation.page()==HOME){label.setIcon(FarmingIcons.of(LEAF,FarmingTheme.SUCCESS));label.setIconTextGap(5);}
        toolbar.add(label,BorderLayout.CENTER);
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.RIGHT,1,0));actions.setOpaque(false);
        if(gear!=null)actions.add(FarmingIcons.button(GEAR,"Settings",FarmingTheme.MUTED,gear));
        if(end)actions.add(FarmingIcons.button(END,"End farming run",FarmingTheme.DANGER,plugin::stop));
        toolbar.add(actions,BorderLayout.EAST);
        FarmingTheme.apply(toolbar);
    }
    private void page(SidebarNavigation.Page screen,String title,Runnable back,Runnable gear,boolean end)
    {navigation.show(screen);rows.removeAll();footer.removeAll();header(title,back,gear,end);}
    private void resetScroll(){scroll.getVerticalScrollBar().setValue(0);}
    private void start(Runnable action)
    {
        if(latestGuide!=null)return; // Only the explicit End action returns to run selection.
        navigation.starting();action.run();
    }
    private void returnToGuide(){if(latestGuide!=null){navigation.show(GUIDE);previousGuide=null;renderGuide(latestGuide);}}
    private void settings(boolean fromRun){settingsFromRun=fromRun;preferences();resetScroll();}
    private void searchField(String value,String placeholder,java.util.function.Consumer<String> change)
    {
        JTextField search=new JTextField(value);search.putClientProperty("JTextField.placeholderText",placeholder);search.setToolTipText(placeholder);
        search.getDocument().addDocumentListener(new DocumentListener(){
            private void update(){String v=search.getText();SwingUtilities.invokeLater(()->{if(SwingUtilities.isDescendingFrom(search,rows))change.accept(v);});}
            public void insertUpdate(DocumentEvent e){update();} public void removeUpdate(DocumentEvent e){update();} public void changedUpdate(DocumentEvent e){update();}
        });addRow(search);
    }
    private JButton collapse(String label,boolean open,Runnable action)
    {
        JButton b=new JButton(label,FarmingIcons.of(open?COLLAPSE:EXPAND,FarmingTheme.MUTED));b.setHorizontalAlignment(SwingConstants.LEFT);
        b.addActionListener(e->action.run());addRow(b);return b;
    }
    private void caption(String value){JTextArea t=text(value,FarmingTheme.MUTED);t.setFont(FarmingTheme.font(Font.PLAIN,11));addRow(t);}


    // Sidebar visibility is not the lifetime of an active run. The guide header
    // explicitly ends the session; game ticks continue updating it while hidden.
    @Override public void onDeactivate() { }

    void home()
    {
        latestGuide=null;guideSession=Long.MIN_VALUE;previousGuide=null;expanded.clear();lastActive=null;navigation.reset();requiredOpen=true;completedOpen=false;
        showHome();
    }
    private void showHome()
    {
        if(latestGuide!=null){returnToGuide();return;}
        page(HOME,"Farming Helper",null,()->settings(false),false);
        // The primary presets are the visual focus; no extra descriptive banner.
        sectionToggle("Grouped runs",groupedOpen,()->{groupedOpen=!groupedOpen;showHome();});
        if(groupedOpen)for(RunPreset preset:RunPreset.values())
        {
            String hint;int sprite;
            switch(preset)
            {
                case TREE:hint="Check, harvest & plant";sprite=ItemID.PLANTPOT_MAGIC_TREE_SAPLING;break;
                case CROP:hint="Herbs, allotments, flowers";sprite=ItemID.RANARR_SEED;break;
                case HOPS_BUSH:hint="Hops & secondary crops";sprite=ItemID.POISONIVY_BUSH_SEED;break;
                default:hint="Special crops & birdhouses";sprite=ItemID.SEAWEED_SEED;break;
            }
            addRow(navRow(preset.title,hint,LEAF,sprite,true,()->start(()->plugin.startPreset(preset))));
        }
        addRow(new JSeparator());
        sectionToggle("Individual runs",individualOpen,()->{individualOpen=!individualOpen;showHome();});
        if(individualOpen)
        {
            JPanel results=new JPanel();results.setOpaque(false);results.setLayout(new BoxLayout(results,BoxLayout.Y_AXIS));
            searchField(individualSearch,"Find a run…",v->{individualSearch=v;fillIndividual(results);});
            fillIndividual(results);addRow(results);
        }
        addRow(navRow("Manual Run","Choose your own patches",LIST,-1,false,this::openManual));
        JLabel version=new JLabel("Farming Helper · v0.34",SwingConstants.CENTER);version.setFont(FarmingTheme.font(Font.PLAIN,10));version.setForeground(FarmingTheme.MUTED);
        footer.add(version);finish();
    }
    private static int patchSprite(PatchType type)
    {
        List<Crop> crops=CropCatalog.crops(type);if(!crops.isEmpty())return crops.get(0).getItemId();
        if(type==PatchType.COMPOST_BIN)return ItemID.BUCKET_ULTRACOMPOST;
        if(type==PatchType.BIRDHOUSE)return 21512; // regular bird house
        return -1;
    }
    private JButton navRow(String title,String subtitle,FarmingIcons.Kind fallback,int sprite,boolean primary,Runnable action)
    {
        String label="<html><div style='width:130px'><b>"+escape(title)+"</b>"+(subtitle.isEmpty()?"":"<br><span style='color:#A6A6A6;font-size:10pt;font-weight:normal'>"+escape(subtitle)+"</span>")+"</div></html>";
        JButton button=new JButton(label,FarmingIcons.of(fallback,FarmingTheme.SUCCESS));button.setHorizontalAlignment(SwingConstants.LEFT);
        button.putClientProperty("farming.navigation",Boolean.TRUE);button.setBackground(primary?FarmingTheme.RUN_CARD:FarmingTheme.NAV);
        button.setForeground(FarmingTheme.TEXT);button.setIconTextGap(8);button.setToolTipText(title+(subtitle.isEmpty()?"":" — "+subtitle));
        button.getAccessibleContext().setAccessibleName(title);button.addActionListener(e->action.run());
        if(sprite>0)plugin.decorateSprite(button,sprite);return button;
    }
    private void sectionToggle(String title,boolean open,Runnable action)
    {
        JButton b=new JButton(title,FarmingIcons.of(open?COLLAPSE:EXPAND,FarmingTheme.SUCCESS));b.setForeground(FarmingTheme.SUCCESS);b.setBackground(FarmingTheme.BACKGROUND);
        b.putClientProperty("farming.navigation",Boolean.TRUE);b.setHorizontalAlignment(SwingConstants.LEFT);b.setFont(FarmingTheme.font(Font.BOLD,12));
        b.addActionListener(e->action.run());addRow(b);
    }
    private void fillIndividual(JPanel results)
    {
        results.removeAll();Map<String,Runnable> choices=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for(PatchType type:PatchType.values())choices.put(RunPreset.individualTitle(type),()->plugin.startIndividual(type));
        choices.put("Allotment & Flower Run",()->plugin.startCombinedShortcut(FarmRunType.ALLOTMENT_FLOWER));
        choices.put("Fruit Tree & Calquat Run",()->plugin.startCombinedShortcut(FarmRunType.FRUIT_TREE_CALQUAT));
        choices.forEach((name,action)->{if(name.toLowerCase(Locale.ROOT).contains(individualSearch.toLowerCase(Locale.ROOT))){
            int sprite=-1;for(PatchType type:PatchType.values())if(RunPreset.individualTitle(type).equals(name)){sprite=patchSprite(type);break;}
            if(name.equals("Allotment & Flower Run"))sprite=ItemID.MARIGOLD_SEED;
            if(name.equals("Fruit Tree & Calquat Run"))sprite=ItemID.PLANTPOT_CALQUAT_SAPLING;
            JButton b=navRow(name,"",LEAF,sprite,false,()->start(action));b.setAlignmentX(Component.LEFT_ALIGNMENT);
            FarmingTheme.apply(b);b.setMaximumSize(new Dimension(Integer.MAX_VALUE,b.getPreferredSize().height));results.add(b);results.add(Box.createVerticalStrut(3));}});
        results.revalidate();results.repaint();
    }
    void emptySelection(){showHome();line("No enabled patches for this run. Check patch preferences.",false);finish();}
    private void openManual()
    {
        manual.clear();manual.addAll(ManualSelection.decode(plugin.setting("manualPatchChoicesV1",null),plugin.patches()));
        manualGroups.clear();manualRun();resetScroll();
    }
    private boolean selectable(FarmRunPatch p){return plugin.enabled(p)&&plugin.accessNote(p).isEmpty();}
    private void saveManual(){plugin.save("manualPatchChoicesV1",ManualSelection.encode(manual));}
    private Map<String,List<FarmRunPatch>> groupedPatches(boolean byLocation,java.util.function.Predicate<FarmRunPatch> filter)
    {
        Map<String,List<FarmRunPatch>> result=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for(FarmRunPatch p:plugin.patches())if(filter.test(p))result.computeIfAbsent(byLocation?p.getLocation():p.getPatchType().getDisplayName(),k->new ArrayList<>()).add(p);
        for(List<FarmRunPatch> list:result.values())list.sort(Comparator.comparing(FarmRunPatch::getDisplayName,String.CASE_INSENSITIVE_ORDER).thenComparing(p->p.getPatchType().getDisplayName()));
        return result;
    }
    private String patchLabel(FarmRunPatch p,boolean byLocation)
    {return byLocation?p.getPatchType().getDisplayName()+(p.getName().isEmpty()?"":" · "+p.getName()):p.getDisplayName();}
    private void manualRun()
    {
        int y=scroll.getVerticalScrollBar().getValue();
        page(MANUAL,"Manual Run",this::showHome,null,latestGuide!=null);
        Map<String,Set<String>> templates=new LinkedHashMap<>();templates.put("Choose preset…",null);
        for(RunPreset preset:RunPreset.values())templates.put(preset.title,preset.select(plugin.patches(),plugin::flag));
        for(PatchType type:PatchType.values())
        {Set<String> keys=new LinkedHashSet<>();for(FarmRunPatch p:plugin.patches())if(p.getPatchType()==type)keys.add(p.key());templates.put(RunPreset.individualTitle(type),keys);}
        JComboBox<String> template=new JComboBox<>(templates.keySet().toArray(new String[0]));
        template.addActionListener(e->{Set<String> keys=templates.get(template.getSelectedItem());if(keys==null)return;manual.clear();for(FarmRunPatch p:plugin.patches())if(selectable(p)&&keys.contains(p.key()))manual.add(p.key());saveManual();manualRun();});addRow(template);
        JComboBox<String> sort=new JComboBox<>(new String[]{"Crop type · A–Z","Location · A–Z"});sort.setSelectedIndex(manualByLocation?1:0);
        sort.addActionListener(e->{manualByLocation=sort.getSelectedIndex()==1;manualGroups.clear();manualRun();});addRow(sort);
        JPanel controls=new JPanel(new java.awt.GridLayout(1,2,4,0));controls.setOpaque(false);
        JButton all=new JButton("Select all"),none=new JButton("Clear all");
        all.addActionListener(e->{ManualSelection.select(manual,plugin.patches(),this::selectable,true);saveManual();manualRun();});
        none.addActionListener(e->{manual.clear();saveManual();manualRun();});controls.add(all);controls.add(none);addRow(controls);
        for(var entry:groupedPatches(manualByLocation,this::selectable).entrySet())
        {
            String key=entry.getKey();List<FarmRunPatch> list=entry.getValue();long count=list.stream().filter(p->manual.contains(p.key())).count();
            JPanel group=new JPanel(new BorderLayout(2,0));group.setBackground(FarmingTheme.CARD);
            JCheckBox check=new JCheckBox(key+" ("+count+"/"+list.size()+")",count==list.size());
            if(count>0&&count<list.size())check.putClientProperty("JButton.selectedState","indeterminate");
            check.addActionListener(e->{ManualSelection.select(manual,list,this::selectable,count!=list.size());saveManual();manualRun();});group.add(check);
            group.add(FarmingIcons.button(manualGroups.contains(key)?COLLAPSE:EXPAND,"Expand patches",FarmingTheme.MUTED,()->{if(!manualGroups.add(key))manualGroups.remove(key);manualRun();}),BorderLayout.EAST);addRow(group);
            if(manualGroups.contains(key))for(FarmRunPatch p:list)
            {
                JCheckBox child=new JCheckBox("<html><div style='width:145px'>"+escape(patchLabel(p,manualByLocation))+"</div></html>",manual.contains(p.key()));
                child.setBorder(BorderFactory.createEmptyBorder(3,12,3,0));child.addActionListener(e->{if(child.isSelected())manual.add(p.key());else manual.remove(p.key());saveManual();manualRun();});addRow(child);
            }
        }
        long count=plugin.patches().stream().filter(this::selectable).filter(p->manual.contains(p.key())).count();
        JPanel bottom=new JPanel(new BorderLayout(2,4));bottom.setOpaque(false);bottom.setBorder(BorderFactory.createEmptyBorder(5,6,6,6));
        JLabel selected=new JLabel(count+" patches selected",SwingConstants.CENTER);selected.setForeground(FarmingTheme.MUTED);bottom.add(selected,BorderLayout.NORTH);
        JButton go=new JButton("Start Manual Run");go.setEnabled(count>0);go.addActionListener(e->start(()->{Set<String> keys=new LinkedHashSet<>();for(FarmRunPatch p:plugin.patches())if(selectable(p)&&manual.contains(p.key()))keys.add(p.key());plugin.startManual(keys);}));
        bottom.add(go);FarmingTheme.apply(bottom);footer.add(bottom);finish();SwingUtilities.invokeLater(()->{if(navigation.page()==MANUAL)scroll.getVerticalScrollBar().setValue(y);});
    }

    /* Layout adapted from Quest Helper QuestOverviewPanel, QuestRequirementsPanel
     * and QuestStepPanel (Zoinkwiz et al., BSD-2-Clause; see LICENSE-QUEST-HELPER).
     * Keep the same title/close, requirement groups and highlighted section hierarchy,
     * but use immutable farming view data instead of Quest Helper runtime dependencies. */
    private JPanel group(String title, Color background)
    {
        JPanel group = new JPanel(new BorderLayout(0,1));
        group.setBackground(background);
        group.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(5,0,0,0,FarmingTheme.BACKGROUND),
            BorderFactory.createEmptyBorder(5,5,5,5)));
        JLabel label = new JLabel(title);
        label.setFont(FarmingTheme.font(Font.BOLD,12));
        label.setForeground(FarmingTheme.ACCENT);
        label.setBorder(BorderFactory.createEmptyBorder(1,1,4,1));
        group.add(label,BorderLayout.NORTH);
        return group;
    }

    private JTextArea text(String value, Color color)
    {
        JTextArea label = new JTextArea(value);
        label.setFont(FarmingTheme.font(Font.PLAIN,12));
        label.setForeground(color);
        label.setEditable(false);label.setFocusable(false);label.setOpaque(false);
        label.setLineWrap(true);label.setWrapStyleWord(true);
        label.setBorder(BorderFactory.createEmptyBorder(3,2,3,2));
        return label;
    }

    private static String rowKey(GuideView.ItemRow row){return row.id+":"+row.recommended;}
    private static Color itemColor(GuideView.ItemRow row){return row.owned>=row.needed?new Color(124,209,119):new Color(236,125,120);}
    private static String stockTip(GuideView.ItemRow row){return "Carried: "+row.owned+" / "+row.needed+" · Last observed bank: "+row.bank+" · Seed Vault: "+row.vault;}
    static void updateQuantity(JLabel label,GuideView.ItemRow row)
    {
        boolean enough=row.owned>=row.needed;
        label.setText(enough?"":row.owned+"/"+row.needed);
        label.setIcon(enough?SATISFIED:null);
        label.setForeground(itemColor(row));label.setToolTipText(stockTip(row));
    }
    private boolean sameStructure(GuideView a,GuideView b)
    {
        if(a==null || !a.stats.signature().equals(b.stats.signature()) || !a.message.equals(b.message)&&a.active==null || a.hideSeedless!=b.hideSeedless || a.clearSeedless!=b.clearSeedless || a.compostStep!=b.compostStep || !a.preparation.equals(b.preparation) || !a.supplyNote.equals(b.supplyNote) || b.active!=a.active || !a.title.equals(b.title) || a.items.size()!=b.items.size() || a.stops.size()!=b.stops.size())return false;
        for(int i=0;i<a.items.size();i++)if(!rowKey(a.items.get(i)).equals(rowKey(b.items.get(i))))return false;
        for(int i=0;i<a.stops.size();i++)if(a.stops.get(i).patch!=b.stops.get(i).patch || a.stops.get(i).done!=b.stops.get(i).done || a.stops.get(i).active!=b.stops.get(i).active)return false;
        return true;
    }
    private void refreshRows(GuideView view)
    {
        if(requirementsHeader!=null)requirementsHeader.setText(requirementsTitle(view));
        for(GuideView.ItemRow r:view.items)
        {
            JTextArea name=itemNames.get(rowKey(r));JLabel quantity=itemQuantities.get(rowKey(r));
            if(name==null || quantity==null)continue;
            name.setText(r.name);name.setForeground(itemColor(r));name.setToolTipText(stockTip(r));
            updateQuantity(quantity,r);
        }
        for(GuideView.Stop stop:view.stops){JTextArea label=stepText.get(stop.patch.key());if(label!=null)label.setText((stop.active?"▶ ":"")+stop.instruction);}
        finish();
    }
    private void requirements(String name, List<GuideView.ItemRow> items, boolean recommended)
    {
        JPanel group=group(name,FarmingTheme.CARD);
        JPanel body=new JPanel();body.setLayout(new BoxLayout(body,BoxLayout.Y_AXIS));body.setOpaque(false);
        for(GuideView.ItemRow r:items)
        {
            if(r.recommended!=recommended)continue;
            JPanel row=new JPanel(new BorderLayout(4,0));row.setOpaque(false);
            Color c=itemColor(r);
            JTextArea label=text(r.name,c);
            JLabel quantity=new JLabel();updateQuantity(quantity,r);
            quantity.setFont(FarmingTheme.font(Font.PLAIN,12));
            String tip=stockTip(r);
            itemNames.put(rowKey(r),label);itemQuantities.put(rowKey(r),quantity);
            label.setToolTipText(tip);quantity.setToolTipText(tip);
            Icon icon=plugin.icon(r.id);
            if(icon!=null){JLabel sprite=new JLabel(icon);sprite.setPreferredSize(new Dimension(32,25));sprite.setToolTipText(tip);row.add(sprite,BorderLayout.WEST);}
            row.add(label,BorderLayout.CENTER);row.add(quantity,BorderLayout.EAST);body.add(row);
        }
        if(body.getComponentCount()==0)return;
        group.add(body,BorderLayout.CENTER);addRow(group);
    }

    void guide(GuideView view,long session)
    {
        // A new backend session must open the guide even if it was started via
        // the plugin API rather than by a sidebar button. Same-session ticks
        // must never change the user's selected screen.
        if(guideSession!=session){guideSession=session;previousGuide=null;lastActive=null;expanded.clear();requiredOpen=true;completedOpen=false;navigation.starting();}
        latestGuide=view;
        if(navigation.acceptGuide())renderGuide(view);
    }
    private String requirementsTitle(GuideView view)
    {long missing=view.items.stream().filter(r->!r.recommended&&r.owned<r.needed).count();return "Required items"+(missing>0?" · "+missing+" missing":"");}
    private void renderGuide(GuideView view)
    {
        if(sameStructure(previousGuide,view)){previousGuide=view;refreshRows(view);return;}
        itemNames.clear();itemQuantities.clear();stepText.clear();
        int scrollY=scroll.getVerticalScrollBar().getValue();
        boolean sameActive=lastActive==view.active && previousGuide!=null;
        previousGuide=view;
        if(lastActive!=view.active)
        {
            if(lastActive!=null)expanded.put(lastActive.key(),false);
            if(view.active!=null)expanded.put(view.active.key(),true);
            lastActive=view.active;
        }
        page(GUIDE,view.title,null,this::runSettings,true);
        long done=view.stops.stream().filter(stop->stop.done).count();
        long pending=view.stops.size()-done;
        if(!plugin.diseaseInterruptActive()&&!view.stops.isEmpty())
        {
            caption(done+" listed complete · "+pending+" remaining");
            JProgressBar progress=new JProgressBar(0,view.stops.size());progress.setValue((int)done);progress.setForeground(FarmingTheme.SUCCESS);
            progress.setPreferredSize(new Dimension(1,6));addRow(progress);
        }
        requirementsHeader=collapse(requirementsTitle(view),requiredOpen,()->{requiredOpen=!requiredOpen;previousGuide=null;renderGuide(latestGuide);});
        if(requiredOpen)requirements("Supplies",view.items,false);
        if(requiredOpen||!view.preparation.isEmpty())button("Show farming bank",plugin::showActiveRunBank);


        if(!view.supplyNote.isEmpty())line(view.supplyNote,false);
        if(!view.preparation.isEmpty())
        {
            heading("Preparation / resupply");line(view.preparation,true);
            button("Continue run",plugin::continueRun);
        }
        else if(view.active==null){heading("Run status");line(view.message,true);
            if(view.stats.processed>=0){caption("Processed (including preserved): "+view.stats.processed);caption("Skipped manually: "+view.stats.skipped);if(view.stats.deferred>0)caption("Deferred this stage: "+view.stats.deferred);if(view.stats.seedless>0)caption("Hidden for missing seed stock: "+view.stats.seedless);}
            button("Return home",plugin::stop);}
        if(plugin.diseaseInterruptActive())heading("Disease detected");
        if(done>0)collapse("Completed · "+done,completedOpen,()->{completedOpen=!completedOpen;previousGuide=null;renderGuide(latestGuide);});
        if(completedOpen)for(GuideView.Stop stop:view.stops)if(stop.done)caption("✓ "+stop.patch.guideLabel());
        List<GuideView.Stop> displayed=new ArrayList<>(view.stops);
        displayed.sort(Comparator.comparing((GuideView.Stop stop)->!stop.active));
        for(GuideView.Stop stop:displayed)
        {
            if(stop.done)continue;
            JPanel section=new JPanel(new BorderLayout(0,1));section.setBorder(BorderFactory.createEmptyBorder(5,0,0,0));
            boolean open=expanded.getOrDefault(stop.patch.key(),stop.active);
            Color bg=stop.active?ACTIVE:FarmingTheme.CARD.darker();
            JButton fold=new JButton("<html><div style='width:140px'>"+escape(stop.patch.guideLabel())+"<br><small>"+escape(stop.patch.getPatchType().getDisplayName())+"</small></div></html>",FarmingIcons.of(open?COLLAPSE:EXPAND,stop.active?FarmingTheme.SUCCESS:FarmingTheme.MUTED));
            fold.setToolTipText(stop.patch.getDisplayName());
            fold.setFont(FarmingTheme.font(Font.BOLD,12));fold.setHorizontalAlignment(SwingConstants.LEFT);
            fold.setBackground(bg);fold.putClientProperty("farming.preserveBackground",Boolean.TRUE);fold.setForeground(stop.active?ACTIVE_TEXT:Color.WHITE);
            fold.setBorder(BorderFactory.createEmptyBorder(7,7,5,7));
            fold.addActionListener(e->{expanded.put(stop.patch.key(),!open);GuideView latest=previousGuide;previousGuide=null;renderGuide(latest==null?view:latest);});section.add(fold,BorderLayout.NORTH);
            if(open)
            {
                JPanel body=new JPanel(new BorderLayout());body.setBackground(FarmingTheme.CARD);
                body.setBorder(BorderFactory.createEmptyBorder(5,5,5,5));
                JTextArea detail=text((stop.active?"▶ ":"")+stop.instruction,stop.done?new Color(100,205,100):stop.active?ACTIVE_TEXT:Color.WHITE);
                stepText.put(stop.patch.key(),detail);body.add(detail,BorderLayout.CENTER);
                if(stop.active)
                {
                    JButton skip=new JButton(plugin.diseaseInterruptActive()?"Skip cure / resume run":"Skip patch");skip.addActionListener(e->plugin.skip(stop.patch));
                    JPanel actions=new JPanel(new java.awt.GridLayout(0,1,0,2));actions.setOpaque(false);
                    if(view.compostStep){JButton applied=new JButton("Compost applied / skip");applied.addActionListener(e->plugin.acknowledgeCompost(stop.patch));actions.add(applied);}
                    actions.add(skip);body.add(actions,BorderLayout.SOUTH);
                }
                section.add(body,BorderLayout.CENTER);
            }
            addRow(section);
        }
        if(view.preparation.isEmpty()&&view.active!=null&&!plugin.diseaseInterruptActive())button("Resupply…",plugin::resupply);
        finish();
        SwingUtilities.invokeLater(()->{if(navigation.page()==GUIDE)scroll.getVerticalScrollBar().setValue(sameActive?scrollY:0);});
    }

    private static String escape(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");}
    private void preferences()
    {
        page(SETTINGS,"Settings",settingsFromRun?this::runSettings:this::showHome,null,latestGuide!=null);
        if(!plugin.canSave())caption("Log in to edit character preferences.");
        else
        {
            addRow(navRow("General","Default crop care",SLIDERS,-1,false,this::general));
            addRow(navRow("Grouped runs","Customize presets",LAYERS,-1,false,this::groupedPreferences));
            addRow(navRow("Patches","Manage locations",PIN,-1,false,this::patchBrowser));
            addRow(navRow("Crop priorities","Preferred seeds",LEAF,-1,false,this::priorities));
            addRow(navRow("Bank & Seed Vault","Storage integration",FarmingIcons.Kind.STORAGE,-1,false,this::storagePreferences));
            addRow(navRow("Advanced","Diagnostics & maintenance",TOOLS,-1,false,this::advanced));
            caption("Preferences save automatically.");
        }
        finish();
    }
    private void general()
    {
        page(GENERAL,"General",this::preferences,null,latestGuide!=null);
        heading("Default crop care");caption("Compost type");
        JComboBox<CompostChoice> compost=new JComboBox<>(CompostChoice.values());
        compost.setSelectedItem(CompostChoice.load(plugin.setting("directorCompostChoice",""),plugin.flag("directorUseCompost",true)));
        compost.setToolTipText("Bottomless bucket uses ultracompost if the bucket is unavailable.");
        compost.addActionListener(e->plugin.save("directorCompostChoice",((CompostChoice)compost.getSelectedItem()).name()));addRow(compost);
        preferenceCheck("Pay for crop protection","directorProtectTrees",true);
        heading("Default tree removal");preferenceCheck("Pay gardener to remove trees","directorPayRemoval",true);
        caption("Saved run policies override these defaults.");finish();
    }
    private void preferenceCheck(String title,String key,boolean fallback)
    {JCheckBox check=new JCheckBox(title,plugin.flag(key,fallback));check.addActionListener(e->plugin.save(key,Boolean.toString(check.isSelected())));addRow(check);}
    private void groupedPreferences()
    {
        int y=scroll.getVerticalScrollBar().getValue();page(GROUPS,"Grouped runs",this::preferences,null,latestGuide!=null);
        caption("Applies to grouped runs only.");
        for(RunPreset preset:RunPreset.values())
        {
            long count=preset.types.stream().filter(t->plugin.flag(preset.key(t),preset.defaultEnabled(t))).count();
            boolean open=presetExpanded.contains(preset.name());
            collapse(preset.title+" · "+count+"/"+preset.types.size(),open,()->{if(!presetExpanded.add(preset.name()))presetExpanded.remove(preset.name());groupedPreferences();});
            if(open)for(PatchType type:preset.types)
            {JCheckBox check=new JCheckBox(type.getDisplayName(),plugin.flag(preset.key(type),preset.defaultEnabled(type)));check.setBorder(BorderFactory.createEmptyBorder(2,10,2,0));check.addActionListener(e->{plugin.save(preset.key(type),Boolean.toString(check.isSelected()));groupedPreferences();});addRow(check);}
        }
        finish();SwingUtilities.invokeLater(()->{if(navigation.page()==GROUPS)scroll.getVerticalScrollBar().setValue(y);});
    }
    private void storagePreferences()
    {
        page(SidebarNavigation.Page.STORAGE,"Bank & Seed Vault",this::preferences,null,latestGuide!=null);
        heading("Bank");preferenceCheck("Farming bank view","activeRunBank",true);caption("Open organized supplies during a run.");
        heading("Seed Vault");preferenceCheck("Custom Seed Vault views","seedVaultViews",true);caption("Highest XP · Top 3 · Custom · Active Run");finish();
    }
    private void advanced()
    {
        page(ADVANCED,"Advanced",this::preferences,null,latestGuide!=null);
        heading("Observed storage");
        JButton reset=new JButton("Reset observed stock");reset.setEnabled(latestGuide!=null);
        reset.setToolTipText("Clears cached quantities only; reopen the bank and Seed Vault.");
        reset.addActionListener(e->{if(JOptionPane.showConfirmDialog(this,"Clear cached bank and Seed Vault quantities? Your items are unchanged.","Reset observed stock",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION)plugin.resetObservedStock();});addRow(reset);
        if(latestGuide==null)caption("Available during an active run.");
        heading("Integrations");for(String name:List.of("Shortest Path","Bank Tags","Time Tracking")){boolean active=plugin.integrationActive(name);caption(name+" · "+(active?"Enabled":"Not enabled"));}
        finish();
    }
    private void runSettings()
    {
        if(latestGuide==null){showHome();return;}
        page(RUN_SETTINGS,"Run settings",this::returnToGuide,null,true);
        GuideView view=latestGuide;
        heading("Patch selection");
        JCheckBox seedless=new JCheckBox("Hide seedless patches",view.hideSeedless);
        JCheckBox clear=new JCheckBox("Clear occupied patches",view.clearSeedless);clear.setEnabled(view.hideSeedless);
        seedless.setToolTipText("Hide patches with insufficient planting supplies; current run only.");
        seedless.addActionListener(e->{plugin.setHideSeedless(seedless.isSelected());clear.setEnabled(seedless.isSelected());});addRow(seedless);
        clear.setBorder(BorderFactory.createEmptyBorder(2,12,2,0));clear.setToolTipText("Harvest or check health without replanting.");
        clear.addActionListener(e->plugin.setClearSeedless(clear.isSelected()));addRow(clear);
        heading("Crop care");JComboBox<RunPolicy> care=new JComboBox<>(RunPolicy.values());care.setSelectedItem(plugin.runPolicy());
        care.addActionListener(e->plugin.setRunPolicy((RunPolicy)care.getSelectedItem(),null));addRow(care);
        if(plugin.runHasTrees()){heading("Tree removal");JComboBox<String> remove=new JComboBox<>(new String[]{"Pay to remove","Manual removal"});remove.setSelectedIndex(plugin.runPayRemoval()?0:1);remove.addActionListener(e->plugin.setRunPolicy(null,remove.getSelectedIndex()==0));addRow(remove);}
        button("All settings",()->settings(true));finish();
    }
    private void patchBrowser()
    {
        page(PATCHES,"Patches",this::preferences,null,latestGuide!=null);
        JPanel results=new JPanel();results.setOpaque(false);results.setLayout(new BoxLayout(results,BoxLayout.Y_AXIS));
        searchField(patchSearch,"Search patches…",v->{patchSearch=v;fillPatches(results);});
        JComboBox<String> sort=new JComboBox<>(new String[]{"Location · A–Z","Crop type · A–Z"});sort.setSelectedIndex(patchByLocation?0:1);
        sort.addActionListener(e->{patchByLocation=sort.getSelectedIndex()==0;patchExpanded.clear();fillPatches(results);});addRow(sort);
        JCheckBox locked=new JCheckBox("Show locked patches",showLocked);locked.addActionListener(e->{showLocked=locked.isSelected();fillPatches(results);});addRow(locked);
        fillPatches(results);addRow(results);finish();
    }
    private void resultRow(JPanel result,JComponent c)
    {
        JPanel row=new JPanel(new BorderLayout());row.setOpaque(false);row.setBorder(BorderFactory.createEmptyBorder(2,0,2,0));row.add(c);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);row.setMaximumSize(new Dimension(Integer.MAX_VALUE,row.getPreferredSize().height));FarmingTheme.apply(row);result.add(row);
    }
    private void fillPatches(JPanel results)
    {
        results.removeAll();String q=patchSearch.toLowerCase(Locale.ROOT).trim();
        for(var entry:groupedPatches(patchByLocation,p->(showLocked||plugin.accessNote(p).isEmpty())&&(p.getDisplayName()+" "+p.getPatchType().getDisplayName()).toLowerCase(Locale.ROOT).contains(q)).entrySet())
        {
            String key=entry.getKey();List<FarmRunPatch> list=entry.getValue();
            boolean open=patchExpanded.contains(key)||!q.isEmpty();
            JButton fold=new JButton(key+" · "+list.stream().filter(plugin::enabled).count()+"/"+list.size(),FarmingIcons.of(open?COLLAPSE:EXPAND,FarmingTheme.MUTED));
            fold.setHorizontalAlignment(SwingConstants.LEFT);fold.addActionListener(e->{if(!patchExpanded.add(key))patchExpanded.remove(key);fillPatches(results);});resultRow(results,fold);
            if(open)for(FarmRunPatch p:list)
            {
                JPanel row=new JPanel(new BorderLayout(2,0));row.setOpaque(false);row.setBorder(BorderFactory.createEmptyBorder(2,8,2,0));
                JCheckBox enabled=new JCheckBox("<html><div style='width:120px'>"+escape(patchLabel(p,patchByLocation))+"</div></html>",plugin.enabled(p));
                String issue=plugin.accessNote(p);enabled.setToolTipText(issue.isEmpty()?p.getDisplayName():issue);
                if(!issue.isEmpty())enabled.setForeground(FarmingTheme.MUTED);
                enabled.addActionListener(e->{plugin.save("patchEnabled_"+p.key(),Boolean.toString(enabled.isSelected()));fillPatches(results);});row.add(enabled);
                if(p.getPatchType()!=PatchType.BIRDHOUSE&&p.getPatchType()!=PatchType.COMPOST_BIN)
                {
                    String old=plugin.override(p);JButton edit=FarmingIcons.button(MORE,"Preferred crop: "+(old.isEmpty()?"Automatic":old),old.isEmpty()?FarmingTheme.MUTED:FarmingTheme.SUCCESS,()->{
                        JComboBox<String> choose=crops(p.getPatchType(),"Automatic");if(!old.isEmpty())choose.setSelectedItem(old);FarmingTheme.apply(choose);
                        if(JOptionPane.showConfirmDialog(this,choose,p.getDisplayName()+" crop",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION){plugin.save("directorCrop_"+p.key(),choose.getSelectedIndex()==0?"":String.valueOf(choose.getSelectedItem()));fillPatches(results);}
                    });row.add(edit,BorderLayout.EAST);
                }
                resultRow(results,row);
            }
        }
        results.revalidate();results.repaint();
    }
    private void priorities()
    {
        int y=scroll.getVerticalScrollBar().getValue();page(PRIORITIES,"Crop priorities",this::preferences,null,latestGuide!=null);
        List<PatchType> types=new ArrayList<>(Arrays.asList(PatchType.values()));types.sort(Comparator.comparing(PatchType::getDisplayName,String.CASE_INSENSITIVE_ORDER));
        for(PatchType type:types)if(type!=PatchType.BIRDHOUSE&&type!=PatchType.COMPOST_BIN)
        {
            addRow(navRow(type.getDisplayName(),plugin.priority(type).isBlank()?"Automatic":"Custom priorities",priorityOpen==type?COLLAPSE:EXPAND,patchSprite(type),false,()->{priorityOpen=priorityOpen==type?null:type;priorities();}));
            if(priorityOpen==type)ranking(type);
        }
        finish();SwingUtilities.invokeLater(()->{if(navigation.page()==PRIORITIES)scroll.getVerticalScrollBar().setValue(y);});
    }
    private JComboBox<String> crops(PatchType type,String first)
    {
        JComboBox<String> combo = new JComboBox<>();
        combo.addItem(first);
        for (Crop crop : CropCatalog.crops(type)) combo.addItem(crop.getName());
        return combo;
    }

    private void saveRanking(PatchType type,List<String> names)
    {
        plugin.save("cropPriority_"+type.name(),String.join(", ",names));
        plugin.save("cropCustom_"+type.name(),String.valueOf(!names.isEmpty()));
        priorities();
    }
    private void ranking(PatchType type)
    {
        String csv=plugin.priority(type);
        List<String> names=new ArrayList<>();if(!csv.isBlank())names.addAll(Arrays.asList(csv.split(",\\s*")));
        line(names.isEmpty()?"Automatic · highest estimated XP per seed in stock":"Preferred crops first, then highest XP per seed",false);
        for(int i=0;i<names.size();i++)
        {
            final int n=i;
            JPanel row=new JPanel(new BorderLayout(2,0));row.setOpaque(false);JLabel name=new JLabel("<html>"+(i+1)+". "+escape(names.get(i))+"</html>");Crop crop=CropCatalog.findByName(type,names.get(i));if(crop!=null)plugin.decorateSprite(name,crop.getItemId());row.add(name,BorderLayout.CENTER);
            JPanel buttons=new JPanel(new FlowLayout(FlowLayout.RIGHT,0,0));buttons.setOpaque(false);
            JButton up=new JButton(FarmingIcons.of(UP,FarmingTheme.MUTED)),down=new JButton(FarmingIcons.of(DOWN,FarmingTheme.MUTED)),remove=new JButton(FarmingIcons.of(END,FarmingTheme.DANGER));
            up.setToolTipText("Move up");down.setToolTipText("Move down");remove.setToolTipText("Remove preference");
            up.setEnabled(i>0);down.setEnabled(i+1<names.size());
            up.addActionListener(e->{java.util.Collections.swap(names,n,n-1);saveRanking(type,names);});
            down.addActionListener(e->{java.util.Collections.swap(names,n,n+1);saveRanking(type,names);});
            remove.addActionListener(e->{names.remove(n);saveRanking(type,names);});
            for(JButton b:List.of(up,down,remove)){b.putClientProperty("farming.icon",Boolean.TRUE);b.putClientProperty("FlatLaf.styleClass","iconButton legacyIconButton");b.setPreferredSize(new Dimension(26,26));b.setMargin(new java.awt.Insets(1,3,1,3));buttons.add(b);}row.add(buttons,BorderLayout.EAST);addRow(row);
        }
        JComboBox<String> ranking=crops(type,"Add a preferred crop…");
        ranking.addActionListener(e->{if(ranking.getSelectedIndex()>0){String n=(String)ranking.getSelectedItem();if(!names.contains(n))names.add(n);saveRanking(type,names);}});addRow(ranking);
        if(!names.isEmpty())button("Reset to automatic",()->saveRanking(type,new ArrayList<>()));
    }
}
