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
    private boolean requiredOpen=true,completedOpen=false;
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
    private static final Color ACTIVE=new Color(58,74,54), ACTIVE_TEXT=new Color(178,222,159);
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
        rows.setBorder(BorderFactory.createEmptyBorder(6,6,6,6));
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
        addRow(label);
    }

    private void line(String text, boolean active)
    {
        JTextArea label = text(text,active ? ColorScheme.BRAND_ORANGE : Color.LIGHT_GRAY);
        label.setOpaque(active);
        label.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        label.setForeground(active ? ColorScheme.BRAND_ORANGE : Color.LIGHT_GRAY);
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
        if(latestGuide!=null&&JOptionPane.showConfirmDialog(this,"End the current run and start a new one?","Replace active run",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
        navigation.starting();action.run();
    }
    private void resume(){if(latestGuide!=null){navigation.show(GUIDE);previousGuide=null;renderGuide(latestGuide);}}
    private void settings(boolean fromRun){settingsFromRun=fromRun;preferences();resetScroll();}
    private void searchField(String value,String placeholder,java.util.function.Consumer<String> change)
    {
        JTextField search=new JTextField(value);search.putClientProperty("JTextField.placeholderText",placeholder);search.setToolTipText(placeholder);
        search.getDocument().addDocumentListener(new DocumentListener(){
            private void update(){String v=search.getText();SwingUtilities.invokeLater(()->{if(SwingUtilities.isDescendingFrom(search,rows))change.accept(v);});}
            public void insertUpdate(DocumentEvent e){update();} public void removeUpdate(DocumentEvent e){update();} public void changedUpdate(DocumentEvent e){update();}
        });addRow(search);
    }
    private void collapse(String label,boolean open,Runnable action)
    {
        JButton b=new JButton(label,FarmingIcons.of(open?COLLAPSE:EXPAND,FarmingTheme.MUTED));b.setHorizontalAlignment(SwingConstants.LEFT);
        b.addActionListener(e->action.run());addRow(b);
    }
    private void caption(String value){JTextArea t=text(value,FarmingTheme.MUTED);t.setFont(FarmingTheme.font(Font.PLAIN,11));addRow(t);}


    // Sidebar visibility is not the lifetime of an active run. The guide header
    // explicitly ends the session; game ticks continue updating it while hidden.
    @Override public void onDeactivate() { }

    void home()
    {
        latestGuide=null;previousGuide=null;expanded.clear();lastActive=null;navigation.reset();requiredOpen=true;completedOpen=false;
        showHome();
    }
    private void showHome()
    {
        page(HOME,"Farming Helper",null,()->settings(false),false);
        if(latestGuide!=null)
        {
            heading(latestGuide.title+" active");button("Resume run",this::resume);
            JButton end=FarmingIcons.button(END,"End farming run",FarmingTheme.DANGER,plugin::stop);addRow(end);
        }
        collapse("Grouped runs",groupedOpen,()->{groupedOpen=!groupedOpen;showHome();});
        if(groupedOpen)for(RunPreset preset:RunPreset.values())button(preset.title,()->start(()->plugin.startPreset(preset)));
        collapse("Individual runs",individualOpen,()->{individualOpen=!individualOpen;showHome();});
        if(individualOpen)
        {
            JPanel results=new JPanel();results.setOpaque(false);results.setLayout(new BoxLayout(results,BoxLayout.Y_AXIS));
            searchField(individualSearch,"Find a run…",v->{individualSearch=v;fillIndividual(results);});
            fillIndividual(results);addRow(results);
        }
        button("Manual Run",this::openManual);
        JLabel version=new JLabel("Farming Helper · v0.31",SwingConstants.CENTER);version.setFont(FarmingTheme.font(Font.PLAIN,10));version.setForeground(FarmingTheme.MUTED);
        footer.add(version);finish();
    }
    private void fillIndividual(JPanel results)
    {
        results.removeAll();Map<String,Runnable> choices=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for(PatchType type:PatchType.values())choices.put(RunPreset.individualTitle(type),()->plugin.startIndividual(type));
        choices.put("Allotment & Flower Run",()->plugin.startCombinedShortcut(FarmRunType.ALLOTMENT_FLOWER));
        choices.put("Fruit Tree & Calquat Run",()->plugin.startCombinedShortcut(FarmRunType.FRUIT_TREE_CALQUAT));
        choices.forEach((name,action)->{if(name.toLowerCase(Locale.ROOT).contains(individualSearch.toLowerCase(Locale.ROOT))){
            JButton b=new JButton(name);b.setHorizontalAlignment(SwingConstants.LEFT);b.setAlignmentX(Component.LEFT_ALIGNMENT);
            FarmingTheme.apply(b);b.setMaximumSize(new Dimension(Integer.MAX_VALUE,b.getPreferredSize().height));b.addActionListener(e->start(action));results.add(b);results.add(Box.createVerticalStrut(3));}});
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
                JCheckBox child=new JCheckBox(patchLabel(p,manualByLocation),manual.contains(p.key()));
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
            BorderFactory.createMatteBorder(5,0,0,0,ColorScheme.DARK_GRAY_COLOR),
            BorderFactory.createEmptyBorder(5,5,5,5)));
        JLabel label = new JLabel(title);
        label.setFont(FontManager.getRunescapeBoldFont());
        label.setForeground(Color.WHITE);
        label.setBorder(BorderFactory.createEmptyBorder(1,1,4,1));
        group.add(label,BorderLayout.NORTH);
        return group;
    }

    private JTextArea text(String value, Color color)
    {
        JTextArea label = new JTextArea(value);
        label.setFont(FontManager.getRunescapeFont());
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
        if(a==null || a.hideSeedless!=b.hideSeedless || a.clearSeedless!=b.clearSeedless || a.compostStep!=b.compostStep || !a.preparation.equals(b.preparation) || !a.supplyNote.equals(b.supplyNote) || a.active==null || b.active!=a.active || !a.title.equals(b.title) || a.items.size()!=b.items.size() || a.stops.size()!=b.stops.size())return false;
        for(int i=0;i<a.items.size();i++)if(!rowKey(a.items.get(i)).equals(rowKey(b.items.get(i))))return false;
        for(int i=0;i<a.stops.size();i++)if(a.stops.get(i).patch!=b.stops.get(i).patch || a.stops.get(i).done!=b.stops.get(i).done || a.stops.get(i).active!=b.stops.get(i).active)return false;
        return true;
    }
    private void refreshRows(GuideView view)
    {
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
        JPanel group=group(name,ColorScheme.DARKER_GRAY_COLOR);
        JPanel body=new JPanel();body.setLayout(new BoxLayout(body,BoxLayout.Y_AXIS));body.setOpaque(false);
        for(GuideView.ItemRow r:items)
        {
            if(r.recommended!=recommended)continue;
            JPanel row=new JPanel(new BorderLayout(4,0));row.setOpaque(false);
            Color c=itemColor(r);
            JTextArea label=text(r.name,c);
            JLabel quantity=new JLabel();updateQuantity(quantity,r);
            quantity.setFont(FontManager.getRunescapeFont());
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

    void guide(GuideView view)
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
        rows.removeAll();
        JPanel header=new JPanel(new BorderLayout());header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        header.setBorder(BorderFactory.createEmptyBorder(5,5,5,5));
        JLabel title=new JLabel(view.title);title.setForeground(Color.WHITE);title.setFont(FontManager.getRunescapeBoldFont());
        header.add(title,BorderLayout.CENTER);
        JButton close=new JButton("×");close.setBorderPainted(false);close.setContentAreaFilled(false);
        close.setToolTipText("End farming run");close.addActionListener(e->plugin.stop());header.add(close,BorderLayout.EAST);addRow(header);
        JCheckBox seedless=new JCheckBox("Hide Seedless Patches",view.hideSeedless);
        seedless.setFont(FontManager.getRunescapeFont());seedless.setOpaque(false);
        seedless.setToolTipText("Skip replanting patches without enough seeds or saplings; uses observed bank, vault and inventory. Applies only to this run.");
        seedless.addActionListener(e->plugin.setHideSeedless(seedless.isSelected()));addRow(seedless);
        if(view.hideSeedless)
        {
            JCheckBox clear=new JCheckBox("Clear Patches",view.clearSeedless);clear.setFont(FontManager.getRunescapeFont());clear.setOpaque(false);
            clear.setBorder(BorderFactory.createEmptyBorder(0,14,0,0));clear.setToolTipText("Visit occupied, actionable seedless patches for XP and clearing. Do not replant or visit empty patches. This preference is saved for your character.");clear.addActionListener(e->plugin.setClearSeedless(clear.isSelected()));addRow(clear);
        }
        JComboBox<RunPolicy> care=new JComboBox<>(RunPolicy.values());care.setSelectedItem(plugin.runPolicy());care.setToolTipText("Compost and crop protection for this run type");care.addActionListener(e->plugin.setRunPolicy((RunPolicy)care.getSelectedItem(),null));addRow(care);
        if(plugin.runHasTrees())
        {
            JComboBox<String> removal=new JComboBox<>(new String[]{"Pay to remove","Manual removal"});removal.setSelectedIndex(plugin.runPayRemoval()?0:1);removal.setToolTipText("Tree removal for this run type");removal.addActionListener(e->plugin.setRunPolicy(null,removal.getSelectedIndex()==0));addRow(removal);
        }
        requirements("Item requirements:",view.items,false);

        if(!view.supplyNote.isEmpty())line(view.supplyNote,false);
        if(view.hideSeedless)
        {
            JButton resetStock=new JButton("Reset observed stock");
            resetStock.setToolTipText("Forget saved bank and Seed Vault observations only. No game items are changed. Reopen both storage interfaces before relying on the seedless filter.");
            resetStock.addActionListener(e->{int choice=JOptionPane.showConfirmDialog(this,"Forget cached bank and Seed Vault quantities? Reopen both before relying on the seedless filter. Your actual items are unchanged.","Reset observed stock",JOptionPane.OK_CANCEL_OPTION);if(choice==JOptionPane.OK_OPTION)plugin.resetObservedStock();});addRow(resetStock);
        }
        button("Show Active Run in bank",plugin::showActiveRunBank);
        if(!view.preparation.isEmpty())
        {
            heading("Preparation / resupply");line(view.preparation,true);
            button("Continue run",plugin::continueRun);
        }
        else if(view.active!=null&&!plugin.diseaseInterruptActive())button("Resupply…",plugin::resupply);
        else line(view.message,true);
        for(GuideView.Stop stop:view.stops)
        {
            JPanel section=new JPanel(new BorderLayout(0,1));section.setBorder(BorderFactory.createEmptyBorder(5,0,0,0));
            boolean open=expanded.getOrDefault(stop.patch.key(),stop.active);
            Color bg=stop.active?ACTIVE:ColorScheme.DARKER_GRAY_COLOR.darker();
            JButton fold=new JButton((open?"▾ ":"▸ ")+(stop.done?"✓ ":"")+stop.patch.guideLabel()+(" · "+stop.patch.getPatchType().getDisplayName()));
            fold.setToolTipText(fold.getText());
            fold.setFont(FontManager.getRunescapeBoldFont());fold.setHorizontalAlignment(SwingConstants.LEFT);
            fold.setBackground(bg);fold.setForeground(stop.active?ACTIVE_TEXT:Color.WHITE);
            fold.setBorder(BorderFactory.createEmptyBorder(7,7,5,7));
            fold.addActionListener(e->{expanded.put(stop.patch.key(),!open);GuideView latest=previousGuide;previousGuide=null;guide(latest==null?view:latest);});section.add(fold,BorderLayout.NORTH);
            if(open)
            {
                JPanel body=new JPanel(new BorderLayout());body.setBackground(ColorScheme.DARKER_GRAY_COLOR);
                body.setBorder(BorderFactory.createEmptyBorder(5,5,5,5));
                JTextArea detail=text((stop.active?"▶ ":"")+stop.instruction,stop.done?new Color(100,205,100):stop.active?ACTIVE_TEXT:Color.WHITE);
                stepText.put(stop.patch.key(),detail);body.add(detail,BorderLayout.CENTER);
                if(stop.active)
                {
                    JButton skip=new JButton(plugin.diseaseInterruptActive()?"Skip cure / resume run":"Skip step");skip.addActionListener(e->plugin.skip(stop.patch));
                    JPanel actions=new JPanel(new java.awt.GridLayout(0,1,0,2));actions.setOpaque(false);
                    if(view.compostStep){JButton applied=new JButton("Compost applied / skip");applied.addActionListener(e->plugin.acknowledgeCompost(stop.patch));actions.add(applied);}
                    actions.add(skip);body.add(actions,BorderLayout.SOUTH);
                }
                section.add(body,BorderLayout.CENTER);
            }
            addRow(section);
        }
        finish();
        if(sameActive)SwingUtilities.invokeLater(()->scroll.getVerticalScrollBar().setValue(scrollY));
    }

    private void preferences()
    {
        rows.removeAll();
        heading("Farming preferences");
        if (!plugin.canSave()) line("Log in to edit your character’s preferences.",false);
        else
        {
            JComboBox<CompostChoice> compost = new JComboBox<>(CompostChoice.values());
            compost.setSelectedItem(CompostChoice.load(plugin.setting("directorCompostChoice",""),plugin.flag("directorUseCompost",true)));
            compost.addActionListener(e -> plugin.save("directorCompostChoice",((CompostChoice)compost.getSelectedItem()).name()));
            line("Compost",false);addRow(compost);
            JCheckBox remove = new JCheckBox("Pay gardener to remove trees",plugin.flag("directorPayRemoval",true));
            remove.addActionListener(e->plugin.save("directorPayRemoval",String.valueOf(remove.isSelected())));addRow(remove);
            JCheckBox protect = new JCheckBox("Pay for tree protection",plugin.flag("directorProtectTrees",true));
            protect.addActionListener(e->plugin.save("directorProtectTrees",String.valueOf(protect.isSelected())));addRow(protect);
            JCheckBox vaultViews = new JCheckBox("Seed Vault views",plugin.flag("seedVaultViews",true));
            vaultViews.addActionListener(e->plugin.save("seedVaultViews",String.valueOf(vaultViews.isSelected())));addRow(vaultViews);
            JCheckBox activeBank = new JCheckBox("Curated farming bank view",plugin.flag("activeRunBank",true));
            activeBank.addActionListener(e->plugin.save("activeRunBank",String.valueOf(activeBank.isSelected())));addRow(activeBank);
            heading("Grouped run components");
            for(RunPreset preset:RunPreset.values())button(preset.title,()->presetPreferences(preset));
            heading("Crop priorities and patches");
            for (PatchType type : PatchType.values()) button(type.getDisplayName(), () -> patches(type));
        }
        button("Back",this::home);
        finish();
    }

    private void presetPreferences(RunPreset preset)
    {
        rows.removeAll();heading(preset.title);line("Applies to this preset only. Individual and Manual Run are unaffected.",false);
        for(PatchType type:preset.types)
        {
            JCheckBox box=new JCheckBox(type.getDisplayName(),plugin.flag(preset.key(type),preset.defaultEnabled(type)));
            box.addActionListener(e->plugin.save(preset.key(type),String.valueOf(box.isSelected())));addRow(box);
        }
        button("Back",this::preferences);finish();
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
        patches(type);
    }

    private void patches(PatchType type)
    {
        rows.removeAll();heading(type.getDisplayName());
        if(type==PatchType.COMPOST_BIN||type==PatchType.BIRDHOUSE)
        {
            line(type==PatchType.BIRDHOUSE?"Select birdhouse sites to visit. Readiness follows native Time Tracking.":"Select compost bins to visit. Supplies follow the current contents.",false);
            for(FarmRunPatch p:plugin.patches())if(p.getPatchType()==type)
            {JCheckBox box=new JCheckBox(p.getDisplayName(),plugin.enabled(p));box.addActionListener(e->plugin.save("patchEnabled_"+p.key(),String.valueOf(box.isSelected())));addRow(box);}
            button("Back",this::preferences);finish();return;
        }
        String csv=plugin.priority(type);
        List<String> names=new ArrayList<>();if(!csv.isBlank())names.addAll(Arrays.asList(csv.split(",\\s*")));
        line(names.isEmpty()?"Automatic · highest estimated XP per seed in stock":"Preferred crops first, then highest XP per seed",false);
        for(int i=0;i<names.size();i++)
        {
            final int n=i;
            JPanel row=new JPanel(new BorderLayout(2,0));row.add(new JLabel((i+1)+". "+names.get(i)),BorderLayout.CENTER);
            JPanel buttons=new JPanel();
            JButton up=new JButton("↑"),down=new JButton("↓"),remove=new JButton("×");
            up.setEnabled(i>0);down.setEnabled(i+1<names.size());
            up.addActionListener(e->{java.util.Collections.swap(names,n,n-1);saveRanking(type,names);});
            down.addActionListener(e->{java.util.Collections.swap(names,n,n+1);saveRanking(type,names);});
            remove.addActionListener(e->{names.remove(n);saveRanking(type,names);});
            for(JButton b:List.of(up,down,remove)){b.setMargin(new java.awt.Insets(1,3,1,3));buttons.add(b);}row.add(buttons,BorderLayout.EAST);addRow(row);
        }
        JComboBox<String> ranking=crops(type,"Add a preferred crop…");
        ranking.addActionListener(e->{if(ranking.getSelectedIndex()>0){String n=(String)ranking.getSelectedItem();if(!names.contains(n))names.add(n);saveRanking(type,names);}});addRow(ranking);
        if(!names.isEmpty())button("Reset to automatic",()->saveRanking(type,new ArrayList<>()));
        heading("Patches");
        for(FarmRunPatch p:plugin.patches())
        {
            if(p.getPatchType()!=type)continue;
            JPanel row=new JPanel(new BorderLayout());
            JCheckBox enabled=new JCheckBox(p.getDisplayName(),plugin.enabled(p));
            String issue=plugin.accessNote(p);if(!issue.isEmpty()){enabled.setForeground(Color.GRAY);enabled.setToolTipText(issue);}
            enabled.addActionListener(e->plugin.save("patchEnabled_"+p.key(),String.valueOf(enabled.isSelected())));row.add(enabled,BorderLayout.CENTER);
            JButton edit=new JButton("⋯");edit.setToolTipText("Preferred crop (falls back when exhausted): "+(plugin.override(p).isEmpty()?"Automatic":plugin.override(p)));
            edit.addActionListener(e->{JComboBox<String> choose=crops(type,"Automatic");String old=plugin.override(p);if(!old.isEmpty())choose.setSelectedItem(old);
                if(JOptionPane.showConfirmDialog(this,choose,p.getDisplayName()+" crop",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION){plugin.save("directorCrop_"+p.key(),choose.getSelectedIndex()==0?"":String.valueOf(choose.getSelectedItem()));patches(type);}});
            row.add(edit,BorderLayout.EAST);addRow(row);
        }
        button("Back",this::preferences);finish();
    }
}
