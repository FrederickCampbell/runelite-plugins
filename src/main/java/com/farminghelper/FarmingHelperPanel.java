package com.farminghelper;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Container;
import java.awt.KeyboardFocusManager;
import java.util.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import static com.farminghelper.SidebarNavigation.Page.*;
import static com.farminghelper.FarmingIcons.Kind.*;
import java.util.List;
import java.util.function.Consumer;
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
    private final Map<SidebarNavigation.Page,Position> positions=new EnumMap<>(SidebarNavigation.Page.class);
    private SidebarNavigation.Page mountedPage;
    private boolean returning;
    private long renderGeneration;
    private JPanel patchResults;
    private ManualPicker manualPicker;
    private GuideView latestGuide;
    private long guideSession=Long.MIN_VALUE;
    private boolean requiredOpen=false,completedOpen=false,lastPreparing=false,teleportsOpen=false;
    private JButton requirementsHeader;
    private boolean guideRendered;
    private boolean settingsFromRun=false,patchByLocation=true,showLocked=false,manualByLocation=false;
    private String patchSearch="",individualSearch="";
    private final Set<String> patchExpanded=new HashSet<>(),manualGroups=new HashSet<>(),presetExpanded=new HashSet<>();
    private PatchType priorityOpen;
    private boolean keepPlantsOpen,runReplacementOpen,keepTypesOpen;
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
            p.setColor(FarmingTheme.SUCCESS);
            p.setStroke(new java.awt.BasicStroke(2f,java.awt.BasicStroke.CAP_ROUND,java.awt.BasicStroke.JOIN_ROUND));
            p.drawLine(x+2,y+7,x+6,y+11);p.drawLine(x+6,y+11,x+12,y+3);p.dispose();
        }
    };
    private GuideView previousGuide;
    private final Map<String,SupplyRow> supplyRows=new HashMap<>();
    private final Map<String,JTextArea> stepText=new HashMap<>();
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

    private static JPanel transparent(java.awt.LayoutManager layout)
    {
        JPanel panel=new JPanel(layout);panel.setOpaque(false);return panel;
    }
    private static JCheckBox check(String label,boolean value,Consumer<Boolean> change)
    {
        JCheckBox box=new JCheckBox(label,value);
        box.addActionListener(e->change.accept(box.isSelected()));return box;
    }
    /** A control identity and its offset survive rebuilding a list or returning from a child page. */
    private static final class Position
    {
        int scrollY, offset, dot, mark;
        String focus, anchor, fallback;
        boolean search;
    }

    private static String key(Component control)
    {
        if (!(control instanceof AbstractButton || control instanceof JTextField || control instanceof JComboBox)) return null;
        if (control.getName() != null) return control.getName();
        String name=control.getAccessibleContext().getAccessibleName();
        return name == null || name.isEmpty() ? null : name;
    }

    private static void controls(Container owner, List<JComponent> list)
    {
        for (Component child : owner.getComponents())
        {
            if (!child.isVisible()) continue;
            if (key(child) != null) list.add((JComponent) child);
            else if (child instanceof JPanel || child instanceof JViewport || child instanceof JScrollPane)
                controls((Container)child,list);
        }
    }

    private Position position()
    {
        Position saved = new Position();
        saved.scrollY = scroll.getVerticalScrollBar().getValue();
        Component focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (focus != null && SwingUtilities.isDescendingFrom(focus,this))
        {
            saved.focus=key(focus);
            if (focus instanceof JComponent) saved.fallback=(String)((JComponent)focus).getClientProperty("farming.focusFallback");
            if (focus instanceof JTextField)
            {
                JTextField field=(JTextField)focus;
                saved.search=true; saved.dot=field.getCaret().getDot(); saved.mark=field.getCaret().getMark();
            }
        }
        if (saved.scrollY > 0)
        {
            List<JComponent> candidates = new ArrayList<>();
            controls(rows,candidates);
            int nearest=Integer.MAX_VALUE;
            for (JComponent c : candidates)
            {
                int offset=SwingUtilities.convertPoint(c,0,0,scroll.getViewport()).y;
                if (offset+c.getHeight()<=0 || offset>=scroll.getViewport().getHeight()) continue;
                if (c==focus || Math.abs(offset)<nearest)
                {
                    saved.anchor=key(c); saved.offset=offset; nearest=Math.abs(offset);
                    if (c==focus) break;
                }
            }
        }
        return saved;
    }

    private void restorePosition(Position saved)
    {
        long generation=++renderGeneration, session=guideSession;
        SwingUtilities.invokeLater(() -> {
            if (generation!=renderGeneration || session!=guideSession) return;
            if (saved==null) { scroll.getVerticalScrollBar().setValue(0); return; }
            int target=saved.scrollY;
            if (saved.focus!=null || saved.anchor!=null)
            {
                List<JComponent> candidates = new ArrayList<>();
                controls(this,candidates);
                JComponent focus=null, fallback=null;
                for (JComponent c : candidates)
                {
                    String k=key(c);
                    if (k.equals(saved.anchor) && SwingUtilities.isDescendingFrom(c,rows))
                        target=SwingUtilities.convertPoint(c,0,0,scroll.getViewport().getView()).y-saved.offset;
                    if (c.isEnabled() && k.equals(saved.focus)) focus=c;
                    if (c.isEnabled() && k.equals(saved.fallback)) fallback=c;
                }
                if (focus==null) focus=fallback;
                Component current=KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
                if (focus!=null && (current==null || !current.isShowing() || SwingUtilities.isDescendingFrom(current,this)))
                {
                    focus.requestFocusInWindow();
                    if (saved.search && focus instanceof JTextField)
                    {
                        JTextField field=(JTextField)focus;
                        int n=field.getDocument().getLength();
                        field.setCaretPosition(Math.min(saved.mark,n));field.moveCaretPosition(Math.min(saved.dot,n));
                    }
                }
            }
            scroll.getVerticalScrollBar().setValue(target);
        });
    }

    private void back(Runnable action)
    {
        returning=true;
        try { action.run(); } finally { returning=false; }
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

    private void heading(String value){heading(value,null);}

    private void infoRow(JComponent label,String help)
    {
        if(help==null){addRow(label);return;}
        JPanel row=transparent(new BorderLayout(2,0));
        String topic=label instanceof JLabel?((JLabel)label).getText():((JTextArea)label).getText();
        row.add(label,BorderLayout.CENTER);row.add(infoButton(topic,help),BorderLayout.EAST);addRow(row);
    }

    /** Same icon treatment as the rest of the sidebar. Click or hover for help. */
    static JButton infoButton(String topic, String help)
    {
        JButton button = FarmingIcons.button(INFO, "Help for " + topic, FarmingTheme.MUTED, null);
        String html = "<html><div style='width:180px'>" + escape(help).replace("\n", "<br>") + "</div></html>";
        button.setToolTipText(html);
        button.getAccessibleContext().setAccessibleDescription(help);
        button.addActionListener(e -> {
            JPopupMenu popup = new JPopupMenu();
            JLabel content = new JLabel(html);
            content.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));
            content.setFont(FarmingTheme.font(Font.PLAIN,11));
            content.setForeground(FarmingTheme.TEXT);
            popup.add(content);
            popup.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
                public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent event) { }
                public void popupMenuCanceled(javax.swing.event.PopupMenuEvent event) { }
                public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent event) { button.requestFocusInWindow(); }
            });
            popup.show(button, button.getWidth() - popup.getPreferredSize().width, button.getHeight());
        });
        return button;
    }

    private void helpRow(String value,Color color,String help)
    {
        JTextArea label=text(value,color);label.setFont(FarmingTheme.font(Font.PLAIN,11));
        infoRow(label,help);
    }

    private void heading(String value,String help)
    {
        JLabel label=new JLabel(value);label.setFont(FarmingTheme.font(Font.BOLD,12));label.setForeground(FarmingTheme.ACCENT);
        if(help==null)label.setBorder(BorderFactory.createEmptyBorder(5,3,1,3));
        infoRow(label,help);
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

    static JButton runButton(String text,Runnable action)
    {
        JButton button=new JButton(text,FarmingIcons.of(PLAY,FarmingTheme.RUN_PRIMARY_TEXT));
        button.putClientProperty("farming.primaryRun",Boolean.TRUE);
        button.setHorizontalAlignment(SwingConstants.CENTER);button.setIconTextGap(7);
        button.setToolTipText(text);button.getAccessibleContext().setAccessibleName(text);
        button.addActionListener(e->action.run());FarmingTheme.apply(button);
        return button;
    }

    private void finish() { rows.revalidate(); rows.repaint(); toolbar.revalidate();toolbar.repaint();footer.revalidate();footer.repaint(); }

    private void header(String title,Runnable back,Runnable gear,boolean end)
    {
        toolbar.removeAll();
        if(back!=null)toolbar.add(FarmingIcons.button(BACK,"Back",FarmingTheme.MUTED,()->back(back)),BorderLayout.WEST);
        JLabel label=new JLabel(title);label.setFont(FarmingTheme.font(Font.BOLD,14));label.setForeground(FarmingTheme.TEXT);label.setToolTipText(title);
        if(back==null&&navigation.page()==HOME){label.setIcon(FarmingIcons.of(LEAF,FarmingTheme.SUCCESS));label.setIconTextGap(5);}
        toolbar.add(label,BorderLayout.CENTER);
        JPanel actions=transparent(new FlowLayout(FlowLayout.RIGHT,1,0));
        if(gear!=null)actions.add(FarmingIcons.button(GEAR,navigation.page()==GUIDE?"Run settings":"Settings",FarmingTheme.MUTED,gear));
        if(end)actions.add(FarmingIcons.button(END,"End farming run",FarmingTheme.DANGER,plugin::stop));
        toolbar.add(actions,BorderLayout.EAST);
        FarmingTheme.apply(toolbar);
    }
    private void page(SidebarNavigation.Page screen,String title,Runnable back,Runnable gear,boolean end)
    {
        if (mountedPage!=null) positions.put(mountedPage,position());
        Position saved=screen==mountedPage || returning ? positions.get(screen) : null;
        if (screen!=MANUAL) manualPicker=null;
        if (screen!=PATCHES) patchResults=null;
        mountedPage=screen;
        navigation.show(screen);rows.removeAll();footer.removeAll();header(title,back,gear,end);
        restorePosition(saved);
    }
    private void start(Runnable action)
    {
        if(latestGuide!=null)return; // Only the explicit End action returns to run selection.
        navigation.starting();action.run();
    }
    private void returnToGuide(){if(latestGuide!=null){navigation.show(GUIDE);previousGuide=null;renderGuide(latestGuide);}}
    private void settings(boolean fromRun){settingsFromRun=fromRun;preferences();}
    private void searchField(String value,String placeholder,java.util.function.Consumer<String> change)
    {
        JTextField search=new JTextField(value);search.putClientProperty("JTextField.placeholderText",placeholder);search.setToolTipText(placeholder);search.setName(placeholder);search.getAccessibleContext().setAccessibleName(placeholder.replace("…",""));
        search.getDocument().addDocumentListener(new DocumentListener(){
            private boolean pending;
            private void update(){
                if(pending)return;
                pending=true;
                SwingUtilities.invokeLater(()->{pending=false;if(SwingUtilities.isDescendingFrom(search,rows))change.accept(search.getText());});
            }
            public void insertUpdate(DocumentEvent e){update();} public void removeUpdate(DocumentEvent e){update();} public void changedUpdate(DocumentEvent e){update();}
        });addRow(search);
    }
    private JButton disclosure(String label, boolean open, Runnable action)
    {
        JButton button = new JButton(label, FarmingIcons.of(open ? COLLAPSE : EXPAND, FarmingTheme.MUTED));
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setName(label);button.getAccessibleContext().setAccessibleDescription((open ? "Collapse " : "Expand ") + label);
        button.addActionListener(e -> action.run());
        return button;
    }
    private JButton collapse(String label,boolean open,Runnable action)
    {
        JButton button = disclosure(label,open,action);
        addRow(button);
        return button;
    }
    private void caption(String value){JTextArea t=text(value,FarmingTheme.MUTED);t.setFont(FarmingTheme.font(Font.PLAIN,11));addRow(t);}


    // Sidebar visibility is not the lifetime of an active run. The guide header
    // explicitly ends the session; game ticks continue updating it while hidden.
    @Override public void onDeactivate() { }

    void home()
    {
        positions.clear();mountedPage=null;latestGuide=null;guideSession=Long.MIN_VALUE;previousGuide=null;expanded.clear();lastActive=null;navigation.reset();requiredOpen=false;completedOpen=false;lastPreparing=false;teleportsOpen=false;guideRendered=false;
        supplyRows.clear();stepText.clear();showHome();
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
        JLabel version=new JLabel(versionLabel(),SwingConstants.CENTER);version.setFont(FarmingTheme.font(Font.PLAIN,10));version.setForeground(FarmingTheme.MUTED);
        footer.add(version);finish();
    }
    static String versionLabel()
    {
        Properties metadata=new Properties();
        try(java.io.InputStream stream=FarmingHelperPanel.class.getResourceAsStream("runelite-plugin.properties"))
        {
            if(stream!=null)metadata.load(stream);
        }
        catch(java.io.IOException ignored){}
        String version=metadata.getProperty("version","");
        return version.isEmpty()?"Farming Helper":"Farming Helper · v"+version;
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
        if(results.getComponentCount()==0)resultRow(results,text("No runs match your search.",FarmingTheme.MUTED));
        results.revalidate();results.repaint();
    }
    void emptySelection()
    {
        showHome();
        line("No enabled patches for this run. Check patch preferences.",true);
        rows.setComponentZOrder(rows.getComponent(rows.getComponentCount()-1),0);
        restorePosition(null);finish();
    }
    private void openManual()
    {
        manual.clear();manual.addAll(ManualSelection.decode(plugin.setting("manualPatchChoicesV1",null),plugin.patches()));
        manualGroups.clear();manualRun();
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
        page(MANUAL,"Manual Run",this::showHome,null,latestGuide!=null);
        manualPicker=new ManualPicker();
        finish();
    }

    /** The selection is character-owned. These controls live only as long as the picker. */
    private final class ManualPicker
    {
        final JPanel list = new JPanel();
        final Map<String,JCheckBox> children = new HashMap<>(), parents = new HashMap<>();
        Map<String,List<FarmRunPatch>> groups;
        final JLabel selected = new JLabel("",SwingConstants.CENTER);
        final JButton go = runButton("Start Manual Run",()->start(()->{
            Set<String> keys=new LinkedHashSet<>();
            for(FarmRunPatch patch:plugin.patches())if(selectable(patch)&&manual.contains(patch.key()))keys.add(patch.key());
            plugin.startManual(keys);
        }));

        ManualPicker()
        {
            JComboBox<String> template=new JComboBox<>();
            template.addItem("Choose preset…");
            Map<String,Set<String>> templates=new LinkedHashMap<>();
            for(RunPreset preset:RunPreset.values())templates.put(preset.title,preset.select(plugin.patches(),plugin::flag));
            for(PatchType type:PatchType.values())
            {
                Set<String> keys=new LinkedHashSet<>();
                for(FarmRunPatch p:plugin.patches())if(p.getPatchType()==type)keys.add(p.key());
                templates.put(RunPreset.individualTitle(type),keys);
            }
            templates.keySet().forEach(template::addItem);
            template.getAccessibleContext().setAccessibleName("Manual run preset");
            template.addActionListener(e->{
                Set<String> keys=templates.get(template.getSelectedItem());
                if(keys==null)return;
                manual.clear();
                for(List<FarmRunPatch> patches:groups.values())
                    for(FarmRunPatch patch:patches)if(keys.contains(patch.key()))manual.add(patch.key());
                save();template.setSelectedIndex(0);
            });
            addRow(template);
            JComboBox<String> sort=new JComboBox<>(new String[]{"Crop type · A–Z","Location · A–Z"});
            sort.getAccessibleContext().setAccessibleName("Group manual patches by");
            sort.setSelectedIndex(manualByLocation?1:0);
            sort.addActionListener(e->{manualByLocation=sort.getSelectedIndex()==1;manualGroups.clear();rebuild();});
            addRow(sort);
            JPanel bulk=transparent(new java.awt.GridLayout(1,2,4,0));
            JButton all=new JButton("Select all"), none=new JButton("Clear all");
            all.addActionListener(e->{for(List<FarmRunPatch> patches:groups.values())for(FarmRunPatch patch:patches)manual.add(patch.key());save();});
            none.addActionListener(e->{manual.clear();save();});
            bulk.add(all);bulk.add(none);addRow(bulk);
            list.setOpaque(false);list.setLayout(new BoxLayout(list,BoxLayout.Y_AXIS));addRow(list);
            JPanel bottom=transparent(new BorderLayout(2,4));
            bottom.setBorder(BorderFactory.createEmptyBorder(5,6,6,6));
            selected.setForeground(FarmingTheme.MUTED);
            bottom.add(selected,BorderLayout.NORTH);bottom.add(go,BorderLayout.CENTER);
            FarmingTheme.apply(bottom);footer.add(bottom);
            rebuild();
        }

        void rebuild()
        {
            Position saved=groups==null?null:position();
            groups=groupedPatches(manualByLocation,FarmingHelperPanel.this::selectable);
            children.clear();parents.clear();list.removeAll();
            groups.forEach((key,patches)->{
                JPanel group=new JPanel(new BorderLayout(2,0));group.setBackground(FarmingTheme.CARD);
                JCheckBox parent=new JCheckBox();parent.setName("manual-group/"+key);
                parent.addActionListener(e->{
                    boolean all=patches.stream().allMatch(p->manual.contains(p.key()));
                    for(FarmRunPatch p:patches)if(all)manual.remove(p.key());else manual.add(p.key());
                    save();
                });
                parents.put(key,parent);group.add(parent);
                boolean open=manualGroups.contains(key);
                JButton fold=FarmingIcons.button(open?COLLAPSE:EXPAND,(open?"Collapse patches · ":"Expand patches · ")+key,
                    FarmingTheme.MUTED,()->{if(!manualGroups.add(key))manualGroups.remove(key);rebuild();});
                fold.setName("manual-fold/"+key);group.add(fold,BorderLayout.EAST);resultRow(list,group);
                if(open)for(FarmRunPatch p:patches)
                {
                    JCheckBox child=check(patchLabel(p,manualByLocation),manual.contains(p.key()),on->{
                        if(on)manual.add(p.key());else manual.remove(p.key());
                        save();
                    });
                    child.setName("manual/"+p.key());
                    child.setMargin(new java.awt.Insets(3,12,3,0));
                    children.put(p.key(),child);resultRow(list,child);
                }
            });
            refresh();list.revalidate();list.repaint();
            if(saved!=null)restorePosition(saved);
        }
        void save(){saveManual();refresh();}
        void refresh()
        {
            int total=0;
            for(var entry:groups.entrySet())
            {
                List<FarmRunPatch> patches=entry.getValue();
                int count=(int)patches.stream().filter(p->manual.contains(p.key())).count();
                total+=count;
                JCheckBox parent=parents.get(entry.getKey());
                parent.setText(entry.getKey()+" ("+count+"/"+patches.size()+")");
                parent.setSelected(count==patches.size());
                parent.putClientProperty("JButton.selectedState",count>0&&count<patches.size()?"indeterminate":null);
            }
            children.forEach((key,child)->child.setSelected(manual.contains(key)));
            selected.setText(total+" patches selected");
            go.setEnabled(total>0);
        }
    }

    /* Layout adapted from Quest Helper QuestOverviewPanel, QuestRequirementsPanel
     * and QuestStepPanel (Zoinkwiz et al., BSD-2-Clause; see LICENSE-QUEST-HELPER).
     * Keep the same title/close, requirement groups and highlighted section hierarchy,
     * but use immutable farming view data instead of Quest Helper runtime dependencies. */
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
    private static Color itemColor(GuideView.ItemRow row){return row.satisfied()?FarmingTheme.SUCCESS:row.recommended?FarmingTheme.WARNING:FarmingTheme.DANGER;}
    private static String observedSource(GuideView.ItemRow row)
    {
        String bank = row.bank > 0 ? "Bank " + row.bank : "";
        String vault = row.vault > 0 ? "Seed Vault " + row.vault : "";
        return bank.isEmpty() ? vault : vault.isEmpty() ? bank : bank + " · " + vault;
    }
    private static String stockTip(GuideView.ItemRow row)
    {
        String source = observedSource(row);
        return "Carried " + row.owned + " of " + row.needed + (source.isEmpty() ? "" : " · Last seen: " + source);
    }
    static void updateQuantity(JLabel label,GuideView.ItemRow row)
    {
        label.setText(row.satisfied() ? "" : row.owned + "/" + row.needed);
        label.setIcon(row.satisfied() ? SATISFIED : null);
        label.setForeground(itemColor(row));
        label.setToolTipText(stockTip(row));
        label.getAccessibleContext().setAccessibleName(row.name + " · " + stockTip(row));
    }

    private final class SupplyRow extends JPanel
    {
        final JTextArea name = text("", FarmingTheme.TEXT);
        final JTextArea source = text("", FarmingTheme.MUTED);
        final JLabel badge = new JLabel();
        final JLabel sprite = new JLabel();
        SupplyRow(GuideView.ItemRow item)
        {
            super(new BorderLayout(4, 0));
            setOpaque(false);
            Icon icon = plugin.icon(item.id);
            if (icon != null)
            {
                sprite.setIcon(icon);
                sprite.setPreferredSize(new Dimension(32,25));
                add(sprite, BorderLayout.WEST);
            }
            badge.setFont(FarmingTheme.font(Font.PLAIN,12));
            source.setFont(FarmingTheme.font(Font.PLAIN,11));
            add(name, BorderLayout.CENTER);
            add(badge, BorderLayout.EAST);
            add(source, BorderLayout.SOUTH);
            update(item);
        }
        void update(GuideView.ItemRow item)
        {
            if (!name.getText().equals(item.name)) name.setText(item.name);
            String seen = item.satisfied() ? "" : observedSource(item);
            String hint = seen.isEmpty() ? "" : "Last seen · " + seen;
            if (!source.getText().equals(hint)) source.setText(hint);
            source.setVisible(!hint.isEmpty());
            String tip = stockTip(item);
            name.setToolTipText(tip);
            name.getAccessibleContext().setAccessibleDescription(tip);
            sprite.setToolTipText(tip);
            updateQuantity(badge,item);
        }
    }

    private boolean sameStructure(GuideView a,GuideView b)
    {
        if(a==null || !a.stats.equals(b.stats) || !a.message.equals(b.message)&&a.active==null || a.hideSeedless!=b.hideSeedless || a.clearSeedless!=b.clearSeedless || a.compostStep!=b.compostStep || !a.preparation.equals(b.preparation) || !a.supplyNote.equals(b.supplyNote) || !a.notes.equals(b.notes) || b.active!=a.active || !a.title.equals(b.title) || a.items.size()!=b.items.size() || a.stops.size()!=b.stops.size())return false;
        for(int i=0;i<a.items.size();i++)if(!rowKey(a.items.get(i)).equals(rowKey(b.items.get(i))))return false;
        for(int i=0;i<a.stops.size();i++)if(a.stops.get(i).patch!=b.stops.get(i).patch || a.stops.get(i).done!=b.stops.get(i).done || a.stops.get(i).active!=b.stops.get(i).active)return false;
        return true;
    }
    private void refreshRows(GuideView before, GuideView view)
    {
        if (requirementsHeader != null && !requirementsTitle(before).equals(requirementsTitle(view)))
            requirementsHeader.setText(requirementsTitle(view));
        for (int i = 0; i < view.items.size(); i++)
        {
            GuideView.ItemRow item = view.items.get(i);
            SupplyRow row = supplyRows.get(rowKey(item));
            if (row != null && !item.equals(before.items.get(i))) row.update(item);
        }
        for (int i = 0; i < view.stops.size(); i++)
        {
            GuideView.Stop stop = view.stops.get(i);
            JTextArea label = stepText.get(stop.patch.key());
            if (label != null && !stop.instruction.equals(before.stops.get(i).instruction))
                label.setText((stop.active ? "▶ " : "") + stop.instruction);
        }
    }

    private JButton supplySection(String title, String topic, String help, boolean optional, GuideView view)
    {
        if (view.items.stream().noneMatch(item -> item.recommended == optional)) return null;
        boolean open = optional ? teleportsOpen : requiredOpen;
        JButton fold = disclosure(title, open, () -> {
            if (optional) teleportsOpen = !teleportsOpen;
            else requiredOpen = !requiredOpen;
            redrawGuide();
        });
        fold.setName(topic);
        JPanel header = transparent(new BorderLayout(2,0));
        header.add(fold, BorderLayout.CENTER);
        header.add(infoButton(topic,help), BorderLayout.EAST);
        addRow(header);
        if (open)
        {
            JPanel body = new JPanel();
            body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
            body.setBackground(FarmingTheme.CARD);
            body.setBorder(BorderFactory.createEmptyBorder(2,5,4,5));
            for (GuideView.ItemRow item : view.items) if (item.recommended == optional)
            {
                SupplyRow row = new SupplyRow(item);
                supplyRows.put(rowKey(item),row);
                body.add(row);
            }
            addRow(body);
        }
        return fold;
    }

    private void redrawGuide()
    {
        previousGuide = null;
        renderGuide(latestGuide);
    }

    void guide(GuideView view,long session)
    {
        // A new backend session must open the guide even if it was started via
        // the plugin API rather than by a sidebar button. Same-session ticks
        // must never change the user's selected screen.
        if(guideSession!=session){positions.clear();mountedPage=null;guideSession=session;guideRendered=false;previousGuide=null;lastActive=null;expanded.clear();requiredOpen=!view.preparation.isEmpty();lastPreparing=!view.preparation.isEmpty();completedOpen=false;teleportsOpen=false;navigation.starting();}
        latestGuide=view;
        if(navigation.acceptGuide())renderGuide(view);
    }
    private String requirementsTitle(GuideView view)
    {long missing=view.items.stream().filter(r->!r.recommended&&!r.satisfied()).count();return "Recommended items"+(missing>0?" · "+missing+" missing":"");}
    private void renderGuide(GuideView view)
    {
        if(sameStructure(previousGuide,view)){refreshRows(previousGuide,view);previousGuide=view;return;}
        supplyRows.clear();stepText.clear();
        boolean sameActive=guideRendered && lastActive==view.active;
        guideRendered=true;
        previousGuide=view;
        if(lastActive!=view.active)
        {
            if(lastActive!=null)expanded.put(lastActive.key(),false);
            if(view.active!=null)expanded.put(view.active.key(),true);
            lastActive=view.active;
        }
        boolean isPreparing=!view.preparation.isEmpty();
        if(isPreparing!=lastPreparing){requiredOpen=isPreparing;teleportsOpen=false;lastPreparing=isPreparing;}
        page(GUIDE,view.title,null,this::runSettings,true);
        long done=view.stops.stream().filter(stop->stop.done).count();
        long pending=view.stops.size()-done;
        int remaining=GuidePresentation.remaining(view.stats,(int)pending);
        int complete=GuidePresentation.done(view.stats,(int)done);
        int total=complete+remaining;
        if(!plugin.diseaseInterruptActive()&&total>0)
        {
            caption(complete+" done · "+remaining+" remaining");
            JProgressBar progress=new JProgressBar(0,total);progress.setValue(complete);progress.setForeground(FarmingTheme.SUCCESS);
            progress.setToolTipText("Completed or preserved: "+complete+". Remaining: "+remaining+". Growing, skipped and unavailable patches are excluded.");
            progress.setPreferredSize(new Dimension(1,6));addRow(progress);
        }
        // The preparation action stays above the checklists; every patch step stays below both.
        if(isPreparing)
        {
            heading(view.notes.initial?"Preparation":"Resupply",view.notes.preparationHelp.isEmpty()?GuidePresentation.PREPARATION_HELP:view.notes.preparationHelp);
            line(view.preparation,true);
            if(!view.notes.storageWarning.isEmpty())helpRow(view.notes.storageWarning,FarmingTheme.WARNING,GuidePresentation.STORAGE_HELP);
            addRow(runButton(view.notes.initial?"Start Run":"Continue Run",plugin::continueRun));
        }
        requirementsHeader=supplySection(requirementsTitle(view),"Recommended items",GuidePresentation.ITEMS_HELP,false,view);
        supplySection("Recommended teleports","Recommended teleports",GuidePresentation.TRAVEL_HELP,true,view);
        if (!isPreparing)
        {
        if(view.active!=null)
        {
            heading(plugin.diseaseInterruptActive()?"Disease detected":"Current patch");
            for(GuideView.Stop stop:view.stops)if(stop.active&&!stop.done)renderStop(view,stop);
            if(plugin.diseaseInterruptActive()&&!view.supplyNote.isEmpty())caption(view.supplyNote);
        }
        else
        {
            heading("Run status");line(view.message,true);
            if(view.stats.processed>=0)
            {
                caption("Completed: "+view.stats.processed);
                if(view.stats.preserved>0)caption("Preserved: "+view.stats.preserved);
                if(view.stats.skipped>0)caption("Skipped: "+view.stats.skipped);
                if(view.stats.deferred>0)caption("Deferred: "+view.stats.deferred);
                if(view.stats.seedless>0)caption("Missing planting supplies: "+view.stats.seedless);
            }
            button("Return home",plugin::stop);
        }
        }
        if(!isPreparing&&!view.notes.storageWarning.isEmpty()&&!plugin.diseaseInterruptActive())
            helpRow(view.notes.storageWarning,FarmingTheme.WARNING,GuidePresentation.STORAGE_HELP);
        if(isPreparing||view.active!=null)
        {
            if(plugin.flag("activeRunBank",true))button("Show farming bank",plugin::showActiveRunBank);
            if(!isPreparing&&!plugin.diseaseInterruptActive())button("Resupply…",plugin::resupply);
        }
        if(!view.supplyNote.isEmpty()&&!plugin.diseaseInterruptActive())caption(view.supplyNote);
        if(!view.notes.details.isEmpty())helpRow("Run details",FarmingTheme.MUTED,view.notes.details);
        if(view.stops.stream().anyMatch(stop->!stop.done&&!stop.active))
        {
            heading("Upcoming patches");
            for(GuideView.Stop stop:view.stops)if(!stop.done&&!stop.active)renderStop(view,stop);
        }
        if(done>0)collapse("Completed / preserved · "+done,completedOpen,()->{completedOpen=!completedOpen;redrawGuide();});
        if(completedOpen)for(GuideView.Stop stop:view.stops)if(stop.done)caption(("Preserved".equals(stop.instruction)?"Preserved: ":"✓ ")+stop.patch.guideLabel());
        finish();
        if (!sameActive) restorePosition(null);
    }

    private void renderStop(GuideView view,GuideView.Stop stop)
    {
        JPanel section=new JPanel(new BorderLayout(0,1));section.setBorder(BorderFactory.createEmptyBorder(5,0,0,0));
        boolean open=expanded.getOrDefault(stop.patch.key(),stop.active);
        Color bg=stop.active?ACTIVE:FarmingTheme.CARD.darker();
        JButton fold=new JButton("<html><div style='width:140px'>"+escape(stop.patch.guideLabel())+"<br><small>"+escape(stop.patch.getPatchType().getDisplayName())+"</small></div></html>",FarmingIcons.of(open?COLLAPSE:EXPAND,stop.active?FarmingTheme.SUCCESS:FarmingTheme.MUTED));
        fold.setName("stop/"+stop.patch.key());fold.setToolTipText(stop.patch.getDisplayName());
        fold.setFont(FarmingTheme.font(Font.BOLD,12));fold.setHorizontalAlignment(SwingConstants.LEFT);
        fold.setBackground(bg);fold.putClientProperty("farming.preserveBackground",Boolean.TRUE);fold.setForeground(stop.active?ACTIVE_TEXT:Color.WHITE);
        fold.setMargin(new java.awt.Insets(5,7,5,7));
        fold.getAccessibleContext().setAccessibleName((open?"Collapse ":"Expand ")+stop.patch.guideLabel());
        fold.addActionListener(e->{expanded.put(stop.patch.key(),!open);redrawGuide();});section.add(fold,BorderLayout.NORTH);
        if(open)
        {
            JPanel body=new JPanel(new BorderLayout());body.setBackground(FarmingTheme.CARD);
            body.setBorder(BorderFactory.createEmptyBorder(5,5,5,5));
            JTextArea detail=text((stop.active?"▶ ":"")+stop.instruction,stop.done?new Color(100,205,100):stop.active?ACTIVE_TEXT:Color.WHITE);
            stepText.put(stop.patch.key(),detail);body.add(detail,BorderLayout.CENTER);
            if(stop.active)
            {
                JButton skip=new JButton(plugin.diseaseInterruptActive()?"Skip cure / resume run":"Skip patch");skip.addActionListener(e->plugin.skip(stop.patch));
                JPanel actions=transparent(new java.awt.GridLayout(0,1,0,2));
                if(view.compostStep){JButton applied=new JButton("Compost applied / skip");applied.addActionListener(e->plugin.acknowledgeCompost(stop.patch));actions.add(applied);}
                actions.add(skip);body.add(actions,BorderLayout.SOUTH);
            }
            section.add(body,BorderLayout.CENTER);
        }
        addRow(section);
    }

    private static String escape(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");}
    private void preferences()
    {
        page(SETTINGS,"Settings",settingsFromRun?this::runSettings:this::showHome,null,latestGuide!=null);
        if(!plugin.canSave())caption("Log in to edit character preferences.");
        else
        {
            addRow(navRow("Farming preferences","Default crop care",SLIDERS,-1,false,this::general));
            addRow(navRow("Patches","Locations and grouped runs",PIN,-1,false,this::patchBrowser));
            addRow(navRow("Crop priorities","Preferred seeds",LEAF,-1,false,this::priorities));
            addRow(navRow("Advanced","Diagnostics & maintenance",TOOLS,-1,false,this::advanced));
            caption("Preferences save automatically.");
        }
        finish();
    }
    private void general()
    {
        page(GENERAL,"Farming preferences",this::preferences,null,latestGuide!=null);
        heading("Run preparation");caption("Start at");
        choice(Preparation.StartAt.values(),plugin.startAt(),null,v->plugin.save("startAtV1",v.name()));
        heading("Default crop care");caption("Compost type");
        choice(CompostChoice.values(),plugin.compostChoice(),"Bottomless bucket uses ultracompost if the bucket is unavailable.",
            v->plugin.save("directorCompostChoice",v.name()));
        preferenceCheck("Pay for crop protection","directorProtectTrees",true);
        heading("Default tree removal");preferenceCheck("Pay gardener to remove trees","directorPayRemoval",true);
        heading("Missing planting supplies");
        seedlessChoice(plugin.seedlessDefault(),v->plugin.save("seedlessPolicyV1",v.name()));
        collapse("Keep planted",keepPlantsOpen,()->{keepPlantsOpen=!keepPlantsOpen;general();});
        if(keepPlantsOpen)
        {
            caption("Harvest renewable produce; leave selected flowers and trees standing. Patch and run overrides take precedence.");
            for(KeepCrop crop:KeepCrop.values())if(crop.common)
            {
                JCheckBox keep=check("<html><div style='width:125px'>"+escape(crop.label)+"</div></html>",plugin.keepSpecies(crop),
                    on->plugin.save(crop.key(),on?"PRESERVE":"REPLANT"));
                JPanel row=keepRow(crop);row.add(keep,BorderLayout.CENTER);addRow(row);
            }
            caption("Spirit trees and active Guild contracts are protected.");
            heading("Also keep");
            for(KeepCrop crop:KeepCrop.values())if(!crop.common&&plugin.keepSpecies(crop))
            {
                JPanel row=keepRow(crop);
                row.add(new JLabel("<html><div style='width:105px'>"+escape(crop.label)+"</div></html>"),BorderLayout.CENTER);
                row.add(FarmingIcons.button(END,"Stop keeping "+crop.label,FarmingTheme.DANGER,()->{plugin.save(crop.key(),"REPLANT");general();}),BorderLayout.EAST);
                addRow(row);
            }
            JComboBox<Object> alsoKeep=new JComboBox<>();alsoKeep.addItem("Add a plant…");
            for(KeepCrop crop:KeepCrop.values())if(!crop.common&&!plugin.keepSpecies(crop))alsoKeep.addItem(crop);
            alsoKeep.addActionListener(e->{
                if(alsoKeep.getSelectedItem() instanceof KeepCrop)
                {
                    KeepCrop crop=(KeepCrop)alsoKeep.getSelectedItem();plugin.save(crop.key(),"PRESERVE");general();
                }
            });addRow(alsoKeep);
            collapse("Crop type defaults",keepTypesOpen,()->{keepTypesOpen=!keepTypesOpen;general();});
            if(keepTypesOpen)for(PatchType type:PatchType.values())if(CropReplacement.preferenceType(type))
            {
                caption(type.getDisplayName());
                JComboBox<String> mode=replacementChoice("Default behavior",plugin.setting("replacement_"+type.name(),""));
                mode.addActionListener(e->{plugin.save("replacement_"+type.name(),replacementMode(mode));general();});addRow(mode);
            }
        }
        caption("These defaults apply to every run. Changes in Run settings are temporary.");finish();

    }

    private JPanel keepRow(KeepCrop crop)
    {
        JPanel row=transparent(new BorderLayout(4,0));
        Crop item=crop.crop();
        if(item!=null)
        {
            JLabel sprite=new JLabel();sprite.setPreferredSize(new Dimension(32,25));
            plugin.decorateSprite(sprite,item.getItemId());row.add(sprite,BorderLayout.WEST);
        }
        return row;
    }

    static JComboBox<String> replacementChoice(String inherited,String saved)
    {
        JComboBox<String> mode=new JComboBox<>(new String[]{inherited,"Harvest & replant","Keep planted"});
        mode.setSelectedIndex("REPLANT".equals(saved)?1:"PRESERVE".equals(saved)?2:0);
        FarmingTheme.apply(mode);return mode;
    }

    static String replacementMode(JComboBox<String> mode)
    {return mode.getSelectedIndex()==1?"REPLANT":mode.getSelectedIndex()==2?"PRESERVE":"";}

    private <T> void choice(T[] values,T selected,String tip,Consumer<T> change)
    {
        JComboBox<T> box=new JComboBox<>(values);box.setSelectedItem(selected);box.setToolTipText(tip);
        box.addActionListener(e->change.accept(box.getItemAt(box.getSelectedIndex())));addRow(box);
    }
    private void seedlessChoice(SeedlessPolicy selected,Consumer<SeedlessPolicy> change)
    {choice(SeedlessPolicy.values(),selected,"Guide anyway: show missing supplies. Harvest / clear: clear occupied patches. Skip: omit seedless patches.",change);}

    private void preferenceCheck(String title,String key,boolean fallback)
    {addRow(check(title,plugin.flag(key,fallback),on->plugin.save(key,Boolean.toString(on))));}
    private void groupedPreferences()
    {
        page(GROUPS,"Grouped runs",this::patchBrowser,null,latestGuide!=null);
        caption("Applies to grouped runs only.");
        for(RunPreset preset:RunPreset.values())
        {
            long count=preset.types.stream().filter(t->preset.included(t,plugin::flag)).count();
            boolean open=presetExpanded.contains(preset.name());
            collapse(preset.title+" · "+count+"/"+preset.types.size(),open,()->{if(!presetExpanded.add(preset.name()))presetExpanded.remove(preset.name());groupedPreferences();});
            if(open)for(PatchType type:preset.types)
            {
                JCheckBox check=new JCheckBox(type.getDisplayName(),preset.included(type,plugin::flag));
                check.setBorder(BorderFactory.createEmptyBorder(2,10,2,0));
                if(plugin.flag("skipType_"+type.name(),false))check.setToolTipText("Excluded by the crop type preference. Enable here to remove that exclusion.");
                check.addActionListener(e->{
                    if(check.isSelected())plugin.save("skipType_"+type.name(),"false");
                    plugin.save(preset.key(type),Boolean.toString(check.isSelected()));groupedPreferences();
                });addRow(check);
            }
        }
        finish();
    }
    private void advanced()
    {
        page(ADVANCED,"Advanced",this::preferences,null,latestGuide!=null);
        heading("Bank & Seed Vault");
        preferenceCheck("Farming bank view","activeRunBank",true);
        preferenceCheck("Custom Seed Vault views","seedVaultViews",true);
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
        caption("Changes apply only to this run.");
        heading("Missing planting supplies");
        seedlessChoice(SeedlessPolicy.of(plugin.hideSeedless(),plugin.clearSeedless()),plugin::setSeedlessPolicy);
        heading("Crop care");choice(RunPolicy.values(),plugin.runPolicy(),null,v->plugin.setRunPolicy(v,null));
        if(plugin.runHasTrees())
        {
            heading("Tree removal");
            String[] modes={"Pay to remove","Manual removal"};
            choice(modes,modes[plugin.runPayRemoval()?0:1],null,v->plugin.setRunPolicy(null,v.equals(modes[0])));
        }
        List<PatchType> runTypes=plugin.selectedRunTypes();
        if(runTypes.stream().anyMatch(CropReplacement::preferenceType))
        {
            collapse("Crop replacement",runReplacementOpen,()->{runReplacementOpen=!runReplacementOpen;runSettings();});
            if(runReplacementOpen)
            {
                caption("Overrides apply to unfinished patches for this run only.");
                for(PatchType type:runTypes)if(CropReplacement.preferenceType(type))
                {
                    caption(type.getDisplayName());
                    JComboBox<String> mode=replacementChoice("Use preferences",plugin.runReplacement(type));
                    mode.addActionListener(e->plugin.setRunReplacement(type,replacementMode(mode)));addRow(mode);
                }
            }
        }
        if(!runTypes.isEmpty()){
            heading("Skip remaining crop type");JComboBox<Object> skipType=new JComboBox<>();skipType.addItem("Choose crop type…");
            for(PatchType type:runTypes)skipType.addItem(type);
            skipType.addActionListener(e->{if(skipType.getSelectedItem() instanceof PatchType){
                PatchType type=(PatchType)skipType.getSelectedItem();
                if(JOptionPane.showConfirmDialog(this,"Skip remaining "+type.getDisplayName()+" patches in this run?","Skip crop type",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION)plugin.skipRemainingType(type);
                skipType.setSelectedIndex(0);
            }});addRow(skipType);
        }
        button("All settings",()->settings(true));finish();

    }
    void accessChanged()
    {
        if(navigation.page()==MANUAL && manualPicker!=null)manualPicker.rebuild();
        if(navigation.page()==PATCHES && patchResults!=null)fillPatches(patchResults);
    }
    private void patchBrowser()
    {
        page(PATCHES,"Patches",this::preferences,null,latestGuide!=null);
        addRow(navRow("Grouped runs","Customize preset crops",LAYERS,-1,false,this::groupedPreferences));
        heading("Locations");
        JPanel results=new JPanel();results.setOpaque(false);results.setLayout(new BoxLayout(results,BoxLayout.Y_AXIS));
        patchResults=results;
        searchField(patchSearch,"Search patches…",v->{patchSearch=v;fillPatches(results);});
        JComboBox<String> sort=new JComboBox<>(new String[]{"Location · A–Z","Crop type · A–Z"});sort.setSelectedIndex(patchByLocation?0:1);
        sort.addActionListener(e->{patchByLocation=sort.getSelectedIndex()==0;patchExpanded.clear();fillPatches(results);});addRow(sort);
        addRow(check("Show locked patches",showLocked,on->{showLocked=on;fillPatches(results);}));
        fillPatches(results);addRow(results);finish();
    }
    private void resultRow(JPanel result,JComponent c)
    {
        JPanel row=transparent(new BorderLayout());row.setBorder(BorderFactory.createEmptyBorder(2,0,2,0));row.add(c);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);row.setMaximumSize(new Dimension(Integer.MAX_VALUE,row.getPreferredSize().height));FarmingTheme.apply(row);result.add(row);
    }
    private void fillPatches(JPanel results)
    {
        Position savedPosition=SwingUtilities.isDescendingFrom(results,rows)?position():null;
        results.removeAll();String q=patchSearch.toLowerCase(Locale.ROOT).trim();
        for(var entry:groupedPatches(patchByLocation,p->(showLocked||plugin.accessNote(p).isEmpty())&&(p.getDisplayName()+" "+p.getPatchType().getDisplayName()).toLowerCase(Locale.ROOT).contains(q)).entrySet())
        {
            String key=entry.getKey();List<FarmRunPatch> list=entry.getValue();
            boolean open=patchExpanded.contains(key)||!q.isEmpty();
            String count=key+" · "+list.stream().filter(plugin::enabled).count()+"/"+list.size();
            if(q.isEmpty())
            {
                JButton fold=disclosure(count,open,()->{if(!patchExpanded.add(key))patchExpanded.remove(key);fillPatches(results);});
                fold.setName("patch-group/"+key);resultRow(results,fold);
            }
            else resultRow(results,text(count,FarmingTheme.MUTED));
            if(open)for(FarmRunPatch p:list)
            {
                JPanel row=transparent(new BorderLayout(2,0));row.setBorder(BorderFactory.createEmptyBorder(2,8,2,0));
                JCheckBox enabled=check("<html><div style='width:120px'>"+escape(patchLabel(p,patchByLocation))+"</div></html>",plugin.enabled(p),
                    on->{plugin.save("patchEnabled_"+p.key(),Boolean.toString(on));fillPatches(results);});
                enabled.setName("enabled/"+p.key());
                String issue=plugin.accessNote(p);enabled.setToolTipText(issue.isEmpty()?p.getDisplayName():issue);
                enabled.getAccessibleContext().setAccessibleDescription(enabled.getToolTipText());
                if(!issue.isEmpty()){
                    JTextArea reason=text("Locked · "+issue,FarmingTheme.WARNING);
                    reason.setFont(FarmingTheme.font(Font.PLAIN,11));row.add(reason,BorderLayout.SOUTH);
                }
                row.add(enabled);
                if(p.getPatchType()!=PatchType.BIRDHOUSE&&p.getPatchType()!=PatchType.COMPOST_BIN)
                {
                    String old=plugin.override(p);
                    String saved=plugin.setting("replacementPatch_"+p.key(),"");
                    JButton edit=FarmingIcons.button(MORE,"Crop preferences: "+(old.isEmpty()?"Automatic":old),old.isEmpty()&&!KeepCrop.explicit(saved)?FarmingTheme.MUTED:FarmingTheme.SUCCESS,()->{
                        JComboBox<String> choose=crops(p.getPatchType(),"Automatic");if(!old.isEmpty())choose.setSelectedItem(old);FarmingTheme.apply(choose);
                        JPanel options=new JPanel();options.setLayout(new BoxLayout(options,BoxLayout.Y_AXIS));options.add(choose);
                        JComboBox<String> behavior=replacementChoice("Use preferences",saved);
                        if(CropReplacement.preferenceType(p.getPatchType()))options.add(behavior);
                        FarmingTheme.apply(options);
                        if(JOptionPane.showConfirmDialog(this,options,p.getDisplayName()+" crop",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION){
                            plugin.save("directorCrop_"+p.key(),choose.getSelectedIndex()==0?"":String.valueOf(choose.getSelectedItem()));
                            if(CropReplacement.preferenceType(p.getPatchType()))plugin.save("replacementPatch_"+p.key(),replacementMode(behavior));
                            fillPatches(results);}
                    });row.add(edit,BorderLayout.EAST);
                }
                resultRow(results,row);
            }
        }
        if(results.getComponentCount()==0)resultRow(results,text("No patches match your filters.",FarmingTheme.MUTED));
        results.revalidate();results.repaint();
        if(savedPosition!=null)restorePosition(savedPosition);
    }
    private void priorities()
    {
        page(PRIORITIES,"Crop priorities",this::preferences,null,latestGuide!=null);
        List<PatchType> types=new ArrayList<>(Arrays.asList(PatchType.values()));types.sort(Comparator.comparing(PatchType::getDisplayName,String.CASE_INSENSITIVE_ORDER));
        for(PatchType type:types)if(type!=PatchType.BIRDHOUSE&&type!=PatchType.COMPOST_BIN)
        {
            addRow(navRow(type.getDisplayName(),plugin.priority(type).isBlank()?"Automatic":"Custom priorities",priorityOpen==type?COLLAPSE:EXPAND,patchSprite(type),false,()->{priorityOpen=priorityOpen==type?null:type;priorities();}));
            if(priorityOpen==type)ranking(type);
        }
        finish();
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
            JPanel row=transparent(new BorderLayout(2,0));JLabel name=new JLabel("<html>"+(i+1)+". "+escape(names.get(i))+"</html>");Crop crop=CropCatalog.findByName(type,names.get(i));if(crop!=null)plugin.decorateSprite(name,crop.getItemId());row.add(name,BorderLayout.CENTER);
            JPanel buttons=transparent(new FlowLayout(FlowLayout.RIGHT,0,0));
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
