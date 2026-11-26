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
    private boolean editingPatch, pendingPatchRefresh;
    private ManualPicker manualPicker;
    private GuideView latestGuide;
    private long guideSession=Long.MIN_VALUE;
    private boolean requiredOpen=false,completedOpen=false,lastPreparing=false,teleportsOpen=false;
    private JButton requirementsHeader,bankShortcut;
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
                if (focus!=null && current!=focus && (current==null || !current.isShowing() || SwingUtilities.isDescendingFrom(current,this)))
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
        resultRow(rows,row);
    }

    private void resultRow(JPanel owner,JComponent row)
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
        FarmingTheme.apply(row);owner.add(wrapper);
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
        JButton button = FarmingIcons.button(INFO, "Help for " + topic, FarmingTheme.MUTED);
        String html = "<html><div style='width:180px'>" + escape(help).replace("\n", "<br>") + "</div></html>";
        button.setToolTipText(html);
        button.getAccessibleContext().setAccessibleDescription(help);
        button.addActionListener(e -> showHelp(button,help));
        return button;
    }

    private static void showHelp(JButton button,String help)
    {
        if(!button.isShowing())return;
        java.awt.GraphicsConfiguration gc=button.getGraphicsConfiguration();
        java.awt.Rectangle bounds=new java.awt.Rectangle(gc.getBounds());
        java.awt.Insets insets=java.awt.Toolkit.getDefaultToolkit().getScreenInsets(gc);
        bounds.x+=insets.left;bounds.y+=insets.top;
        bounds.width-=insets.left+insets.right;bounds.height-=insets.top+insets.bottom;
        int width=Math.max(1,Math.min(220,bounds.width-24));
        JTextArea content=new JTextArea(help);
        content.setFont(FarmingTheme.font(Font.PLAIN,11));content.setForeground(FarmingTheme.TEXT);
        content.setEditable(false);content.setFocusable(false);content.setOpaque(false);
        content.setLineWrap(true);content.setWrapStyleWord(true);
        content.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));
        content.setSize(width,Short.MAX_VALUE);
        content.setPreferredSize(new Dimension(width,content.getPreferredSize().height));
        JPopupMenu popup=new JPopupMenu();popup.add(content);
        popup.addPopupMenuListener(new javax.swing.event.PopupMenuListener(){
            public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent event) { }
            public void popupMenuCanceled(javax.swing.event.PopupMenuEvent event) { }
            public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent event) { button.requestFocusInWindow(); }
        });
        java.awt.Point origin=button.getLocationOnScreen();
        Dimension size=popup.getPreferredSize();
        int x=Math.max(bounds.x,Math.min(origin.x+button.getWidth()-size.width,bounds.x+bounds.width-size.width));
        int y=origin.y+button.getHeight();
        if(y+size.height>bounds.y+bounds.height)y=Math.max(bounds.y,origin.y-size.height);
        popup.show(button,x-origin.x,y-origin.y);
    }

    private void helpRow(String value,Color color,String help)
    {
        JTextArea label=text(value,color);label.setFont(FarmingTheme.font(Font.PLAIN,11));
        infoRow(label,help);
    }

    private void heading(String value,String help)
    {
        JLabel label=new JLabel(value);label.setFont(FarmingTheme.font(Font.BOLD,12));label.setForeground(FarmingTheme.ACCENT);
        if(help==null)label.setBorder(BorderFactory.createEmptyBorder(5,0,1,0));
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
        mountedPage=screen;bankShortcut=null;
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
    private JButton disclosure(String label, boolean open)
    {
        JButton button = new JButton(label, FarmingIcons.of(open ? COLLAPSE : EXPAND, FarmingTheme.MUTED));
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setName(label);button.getAccessibleContext().setAccessibleDescription((open ? "Collapse " : "Expand ") + label);
        return button;
    }
    private JButton collapse(String label,boolean open,Runnable action)
    {
        JButton button = disclosure(label,open);
        button.addActionListener(e->action.run());
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
            addRow(navRow(preset.title,hint,LEAF,sprite,PLAY,()->start(()->plugin.startPreset(preset))));
        }
        addRow(new JSeparator());
        sectionToggle("Individual runs",individualOpen,()->{individualOpen=!individualOpen;showHome();});
        if(individualOpen)individualRuns();
        addRow(navRow("Manual Run","Choose your own patches",LIST,-1,FORWARD,this::openManual));
        JLabel version=new JLabel(versionLabel(),SwingConstants.CENTER);version.setFont(FarmingTheme.font(Font.PLAIN,10));version.setForeground(FarmingTheme.MUTED);
        footer.add(version);finish();
    }
    static String versionLabel()
    {
        return VERSION;
    }
    private static final String VERSION=loadVersion();
    private static String loadVersion()
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
    private JButton navRow(String title,String subtitle,FarmingIcons.Kind graphic,int sprite,FarmingIcons.Kind actionKind,Runnable action)
    {
        boolean launch=actionKind==PLAY;
        Icon marker=FarmingIcons.of(actionKind,launch?FarmingTheme.SUCCESS:FarmingTheme.MUTED);
        JButton button=new JButton("",FarmingIcons.of(graphic,FarmingTheme.SUCCESS)){
            @Override protected void paintComponent(java.awt.Graphics g)
            {
                super.paintComponent(g);
                marker.paintIcon(this,g,getWidth()-marker.getIconWidth()-7,(getHeight()-marker.getIconHeight())/2);
            }
        };
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.putClientProperty("farming.navigation",Boolean.TRUE);button.putClientProperty("farming.marker",Boolean.TRUE);
        button.setBackground(launch?FarmingTheme.RUN_CARD:FarmingTheme.NAV);
        button.setForeground(FarmingTheme.TEXT);button.setIconTextGap(6);
        navText(button,title,subtitle,actionKind);
        button.addActionListener(e->action.run());
        if(sprite>0)plugin.decorateSprite(button,sprite);
        return button;
    }
    private void navText(JButton button,String title,String subtitle,FarmingIcons.Kind actionKind)
    {
        String label="<html><b>"+escape(title)+"</b>"+(subtitle.isEmpty()?"":"<br><span style='color:#A6A6A6;font-size:9pt'>"+escape(subtitle)+"</span>")+"</html>";
        button.setText(label);
        String verb=actionKind==PLAY?"Start ":actionKind==FORWARD?"Open ":actionKind==COLLAPSE?"Collapse ":"Expand ";
        button.setToolTipText(verb+title+(subtitle.isEmpty()?"":" — "+subtitle));
        button.getAccessibleContext().setAccessibleName(title);
        button.getAccessibleContext().setAccessibleDescription(button.getToolTipText());
    }
    private void sectionToggle(String title,boolean open,Runnable action)
    {
        JButton b=new JButton(title,FarmingIcons.of(open?COLLAPSE:EXPAND,FarmingTheme.SUCCESS));b.setForeground(FarmingTheme.SUCCESS);b.setBackground(FarmingTheme.BACKGROUND);
        b.putClientProperty("farming.navigation",Boolean.TRUE);b.setHorizontalAlignment(SwingConstants.LEFT);b.setFont(FarmingTheme.font(Font.BOLD,12));
        b.getAccessibleContext().setAccessibleDescription((open?"Collapse ":"Expand ")+title);
        b.addActionListener(e->action.run());addRow(b);
    }
    private void individualRuns()
    {
        Map<String,JButton> choices=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for(PatchType type:PatchType.values())
        {
            String name=RunPreset.individualTitle(type);
            choices.put(name,navRow(name,"",LEAF,patchSprite(type),PLAY,()->start(()->plugin.startIndividual(type))));
        }
        choices.put("Allotment & Flower Run",navRow("Allotment & Flower Run","",LEAF,ItemID.MARIGOLD_SEED,PLAY,
            ()->start(()->plugin.startCombinedShortcut(FarmRunType.ALLOTMENT_FLOWER))));
        choices.put("Fruit Tree & Calquat Run",navRow("Fruit Tree & Calquat Run","",LEAF,ItemID.PLANTPOT_CALQUAT_SAPLING,PLAY,
            ()->start(()->plugin.startCombinedShortcut(FarmRunType.FRUIT_TREE_CALQUAT))));
        JPanel results=transparent(null);
        results.setLayout(new BoxLayout(results,BoxLayout.Y_AXIS));
        choices.values().forEach(button->resultRow(results,button));
        JTextArea empty=text("No runs match your search.",FarmingTheme.MUTED);
        resultRow(results,empty);
        Consumer<String> filter=query->{
            individualSearch=query;
            String term=query.trim().toLowerCase(Locale.ROOT);
            boolean any=false;
            for(var entry:choices.entrySet())
            {
                boolean match=entry.getKey().toLowerCase(Locale.ROOT).contains(term);
                entry.getValue().getParent().setVisible(match);
                any|=match;
            }
            empty.getParent().setVisible(!any);
            results.revalidate();results.repaint();
        };
        searchField(individualSearch,"Find a run…",filter);
        filter.accept(individualSearch);addRow(results);
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
                parent.putClientProperty("farming.focusFallback","Select all");
                parent.addActionListener(e->{
                    boolean all=patches.stream().allMatch(p->manual.contains(p.key()));
                    for(FarmRunPatch p:patches)if(all)manual.remove(p.key());else manual.add(p.key());
                    save();
                });
                parents.put(key,parent);group.add(parent);
                boolean open=manualGroups.contains(key);
                JButton fold=FarmingIcons.button(open?COLLAPSE:EXPAND,(open?"Collapse patches · ":"Expand patches · ")+key,
                    FarmingTheme.MUTED,()->{if(!manualGroups.add(key))manualGroups.remove(key);rebuild();});
                fold.setName("manual-fold/"+key);fold.putClientProperty("farming.focusFallback","Select all");group.add(fold,BorderLayout.EAST);resultRow(list,group);
                if(open)for(FarmRunPatch p:patches)
                {
                    JCheckBox child=check(patchLabel(p,manualByLocation),manual.contains(p.key()),on->{
                        if(on)manual.add(p.key());else manual.remove(p.key());
                        save();
                    });
                    child.setName("manual/"+p.key());
                    child.putClientProperty("farming.focusFallback","Select all");
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
        for(int i=0;i<a.items.size();i++)
        {
            GuideView.ItemRow old=a.items.get(i), next=b.items.get(i);
            if(old.id!=next.id || old.recommended!=next.recommended)return false;
        }
        for(int i=0;i<a.stops.size();i++)if(a.stops.get(i).patch!=b.stops.get(i).patch || a.stops.get(i).done!=b.stops.get(i).done || a.stops.get(i).active!=b.stops.get(i).active)return false;
        return true;
    }
    private void refreshRows(GuideView before, GuideView view)
    {
        if (requirementsHeader != null)
        {
            String title=requirementsTitle(view);
            if(!title.equals(requirementsHeader.getText()))requirementsHeader.setText(title);
        }
        for (int i = 0; i < view.items.size(); i++)
        {
            GuideView.ItemRow item = view.items.get(i);
            if(item.equals(before.items.get(i)))continue;
            SupplyRow row = supplyRows.get(rowKey(item));
            if (row != null) row.update(item);
        }
        for (int i = 0; i < view.stops.size(); i++)
        {
            GuideView.Stop stop = view.stops.get(i);
            if(stop.instruction.equals(before.stops.get(i).instruction))continue;
            JTextArea label = stepText.get(stop.patch.key());
            if (label != null)
                label.setText((stop.active ? "▶ " : "") + stop.instruction);
        }
    }

    private JButton supplySection(String title, String topic, String help, boolean optional, GuideView view)
    {
        boolean open = optional ? teleportsOpen : requiredOpen;
        JButton fold=disclosure(title,open);
        JPanel section=transparent(new BorderLayout(0,2));
        fold.setName(topic);
        fold.getAccessibleContext().setAccessibleDescription((open?"Collapse ":"Expand ")+topic);
        // Two keyboard targets share one native button surface. Help owns its own click area.
        fold.putClientProperty("farming.inlineHelp",Boolean.TRUE);
        JPanel header=transparent(new java.awt.GridBagLayout());
        java.awt.GridBagConstraints cell=new java.awt.GridBagConstraints();
        cell.gridx=0;cell.gridy=0;cell.weightx=1;cell.weighty=1;
        cell.anchor=java.awt.GridBagConstraints.EAST;
        cell.insets=new java.awt.Insets(0,0,0,3);
        header.add(infoButton(topic,help),cell);
        cell.fill=java.awt.GridBagConstraints.BOTH;cell.insets=new java.awt.Insets(0,0,0,0);
        header.add(fold,cell);
        section.add(header,BorderLayout.NORTH);
        if(open)section.add(supplies(view,optional),BorderLayout.CENTER);
        fold.addActionListener(e->{
            boolean show=optional?!teleportsOpen:!requiredOpen;
            if(optional)teleportsOpen=show;else requiredOpen=show;
            fold.setIcon(FarmingIcons.of(show?COLLAPSE:EXPAND,FarmingTheme.MUTED));
            fold.getAccessibleContext().setAccessibleDescription((show?"Collapse ":"Expand ")+topic);
            if(show)section.add(supplies(latestGuide,optional),BorderLayout.CENTER);
            else
            {
                section.remove(((BorderLayout)section.getLayout()).getLayoutComponent(BorderLayout.CENTER));
                for(GuideView.ItemRow item:latestGuide.items)if(item.recommended==optional)supplyRows.remove(rowKey(item));
            }
            section.revalidate();section.repaint();
        });
        addRow(section);
        return fold;
    }

    private JPanel supplies(GuideView view,boolean optional)
    {
        JPanel body=new JPanel();
        body.setLayout(new BoxLayout(body,BoxLayout.Y_AXIS));
        body.setBackground(FarmingTheme.CARD);
        body.setBorder(BorderFactory.createEmptyBorder(2,5,4,5));
        for(GuideView.ItemRow item:view.items)if(item.recommended==optional)
        {
            SupplyRow row=new SupplyRow(item);
            supplyRows.put(rowKey(item),row);body.add(row);
        }
        if(body.getComponentCount()==0)body.add(text(optional?"No teleports recommended for the remaining patches.":"No items needed for this run.",FarmingTheme.MUTED));
        FarmingTheme.apply(body);
        return body;
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
            progress.getAccessibleContext().setAccessibleName("Run progress");
            progress.getAccessibleContext().setAccessibleDescription(progress.getToolTipText());
            progress.setPreferredSize(new Dimension(1,6));addRow(progress);
        }
        requirementsHeader=null;
        if(isPreparing || view.active!=null || !view.items.isEmpty())
        {
            requirementsHeader=supplySection(requirementsTitle(view),"Recommended items",GuidePresentation.ITEMS_HELP,false,view);
            supplySection("Recommended teleports","Recommended teleports",GuidePresentation.TRAVEL_HELP,true,view);
        }
        if(isPreparing)
        {
            heading(view.notes.initial?"Preparation":"Resupply",view.notes.preparationHelp.isEmpty()?GuidePresentation.PREPARATION_HELP:view.notes.preparationHelp);
            line(view.preparation,true);
            if(!view.notes.storageWarning.isEmpty())helpRow(view.notes.storageWarning,FarmingTheme.WARNING,GuidePresentation.STORAGE_HELP);
            JPanel action=transparent(new BorderLayout());
            action.setBorder(BorderFactory.createEmptyBorder(3,8,6,8));
            action.add(runButton(view.notes.initial?"Start Run":"Continue Run",plugin::continueRun));
            FarmingTheme.apply(action);footer.add(action);
        }
        if(!isPreparing && view.active!=null)
        {
            heading(plugin.diseaseInterruptActive()?"Disease detected":"Current patch");
            for(GuideView.Stop stop:view.stops)if(stop.active&&!stop.done)renderStop(view,stop);
            if(plugin.diseaseInterruptActive()&&!view.supplyNote.isEmpty())caption(view.supplyNote);
        }
        else if(!isPreparing)
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
        if(!isPreparing&&!view.notes.storageWarning.isEmpty()&&!plugin.diseaseInterruptActive())
            helpRow(view.notes.storageWarning,FarmingTheme.WARNING,GuidePresentation.STORAGE_HELP);
        if(isPreparing||view.active!=null)
        {
            if(plugin.flag("activeRunBank",true))
            {
                JButton bank=new JButton("Show farming bank");
                bank.setHorizontalAlignment(SwingConstants.LEFT);
                bank.setToolTipText("Show the farming layout in an open bank with Bank Tags enabled.");
                bank.getAccessibleContext().setAccessibleDescription(bank.getToolTipText());
                bank.addActionListener(e->plugin.showActiveRunBank(message->{
                    if(mountedPage==GUIDE && bankShortcut!=null)showHelp(bankShortcut,message);
                }));
                bankShortcut=bank;addRow(bank);
            }
            if(!isPreparing&&!plugin.diseaseInterruptActive())button("Resupply…",plugin::resupply);
        }
        if(!view.supplyNote.isEmpty()&&!plugin.diseaseInterruptActive())caption(view.supplyNote);
        if(!view.notes.details.isEmpty())helpRow("Run details",FarmingTheme.MUTED,view.notes.details);
        if(view.stops.stream().anyMatch(stop->!stop.done&&!stop.active))
        {
            heading("Upcoming patches");
            for(GuideView.Stop stop:view.stops)if(!stop.done&&!stop.active)renderStop(view,stop);
        }
        if(done>0)
        {
            String title="Completed / preserved · "+done;
            JButton fold=disclosure(title,completedOpen);
            JPanel history=transparent(new BorderLayout());
            history.add(fold,BorderLayout.NORTH);
            Runnable update=()->{
                if(completedOpen){
                    JPanel entries=new JPanel();entries.setOpaque(false);entries.setLayout(new BoxLayout(entries,BoxLayout.Y_AXIS));
                    for(GuideView.Stop stop:latestGuide.stops)if(stop.done)
                        resultRow(entries,text(("Preserved".equals(stop.instruction)?"Preserved: ":"✓ ")+stop.patch.guideLabel(),FarmingTheme.MUTED));
                    history.add(entries,BorderLayout.CENTER);
                }else if(history.getComponentCount()>1)history.remove(1);
            };
            update.run();
            fold.addActionListener(e->{
                completedOpen=!completedOpen;
                fold.setIcon(FarmingIcons.of(completedOpen?COLLAPSE:EXPAND,FarmingTheme.MUTED));
                fold.getAccessibleContext().setAccessibleDescription((completedOpen?"Collapse ":"Expand ")+title);
                update.run();history.revalidate();history.repaint();
            });
            addRow(history);
        }
        finish();
        if (!sameActive) restorePosition(null);
    }

    private void renderStop(GuideView view,GuideView.Stop stop)
    {
        JPanel section=new JPanel(new BorderLayout(0,1));section.setBorder(BorderFactory.createEmptyBorder(5,0,0,0));
        boolean open=expanded.getOrDefault(stop.patch.key(),stop.active);
        Color bg=stop.active?ACTIVE:FarmingTheme.CARD.darker();
        JButton fold=new JButton("<html>"+escape(stop.patch.guideLabel())+"<br><small>"+escape(stop.patch.getPatchType().getDisplayName())+"</small></html>",FarmingIcons.of(open?COLLAPSE:EXPAND,stop.active?FarmingTheme.SUCCESS:FarmingTheme.MUTED));
        fold.setName("stop/"+stop.patch.key());fold.setToolTipText(stop.patch.getDisplayName());
        fold.setFont(FarmingTheme.font(Font.BOLD,12));fold.setHorizontalAlignment(SwingConstants.LEFT);
        fold.setBackground(bg);fold.putClientProperty("farming.preserveBackground",Boolean.TRUE);fold.setForeground(stop.active?ACTIVE_TEXT:Color.WHITE);
        fold.setMargin(new java.awt.Insets(5,7,5,7));
        fold.getAccessibleContext().setAccessibleName((open?"Collapse ":"Expand ")+stop.patch.guideLabel());
        section.add(fold,BorderLayout.NORTH);
        if(open)section.add(stopBody(view,stop),BorderLayout.CENTER);
        fold.addActionListener(e->{
            boolean show=!expanded.getOrDefault(stop.patch.key(),stop.active);
            expanded.put(stop.patch.key(),show);
            fold.setIcon(FarmingIcons.of(show?COLLAPSE:EXPAND,stop.active?FarmingTheme.SUCCESS:FarmingTheme.MUTED));
            fold.getAccessibleContext().setAccessibleName((show?"Collapse ":"Expand ")+stop.patch.guideLabel());
            if(show)
            {
                for(GuideView.Stop current:latestGuide.stops)if(current.patch==stop.patch){
                    section.add(stopBody(latestGuide,current),BorderLayout.CENTER);break;
                }
            }
            else
            {
                section.remove(1);stepText.remove(stop.patch.key());
            }
            section.revalidate();section.repaint();
        });
        addRow(section);
    }

    private JPanel stopBody(GuideView view,GuideView.Stop stop)
    {
        JPanel body=new JPanel(new BorderLayout());body.setBackground(FarmingTheme.CARD);
        body.setBorder(BorderFactory.createEmptyBorder(5,5,5,5));
        JTextArea detail=text((stop.active?"▶ ":"")+stop.instruction,stop.done?FarmingTheme.SUCCESS:stop.active?ACTIVE_TEXT:Color.WHITE);
        stepText.put(stop.patch.key(),detail);body.add(detail,BorderLayout.CENTER);
        if(stop.active)
        {
            JButton skip=new JButton(plugin.diseaseInterruptActive()?"Skip cure / resume run":"Skip patch");
            skip.addActionListener(e->plugin.skip(stop.patch));
            JPanel actions=transparent(new java.awt.GridLayout(0,1,0,2));
            if(view.compostStep){
                JButton applied=new JButton("Compost applied / skip");
                applied.addActionListener(e->plugin.acknowledgeCompost(stop.patch));actions.add(applied);
            }
            actions.add(skip);body.add(actions,BorderLayout.SOUTH);
        }
        FarmingTheme.apply(body);return body;
    }

    private static String escape(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");}
    private void preferences()
    {
        page(SETTINGS,"Settings",settingsFromRun?this::runSettings:this::showHome,null,latestGuide!=null);
        if(!plugin.canSave())caption("Log in to edit character preferences.");
        else
        {
            caption("Character preferences · Saved automatically");
            addRow(navRow("Farming preferences","Default crop care",SLIDERS,-1,FORWARD,this::general));
            addRow(navRow("Patches","Locations and grouped runs",PIN,-1,FORWARD,this::patchBrowser));
            addRow(navRow("Crop priorities","Preferred seeds",LEAF,-1,FORWARD,this::priorities));
            addRow(navRow("Advanced","Diagnostics & maintenance",TOOLS,-1,FORWARD,this::advanced));
        }
        finish();
    }
    private void general()
    {
        page(GENERAL,"Farming preferences",this::preferences,null,latestGuide!=null);
        heading("Run preparation");
        choice("Start at",Preparation.StartAt.values(),plugin.startAt(),null,v->plugin.save("startAtV1",v.name()));
        heading("Default crop care");
        choice("Compost type",CompostChoice.values(),plugin.compostChoice(),"Bottomless bucket uses ultracompost if the bucket is unavailable.",
            v->plugin.save("directorCompostChoice",v.name()));
        preferenceCheck("Pay for crop protection","directorProtectTrees",true);
        heading("Default tree removal");preferenceCheck("Pay gardener to remove trees","directorPayRemoval",true);
        seedlessChoice(plugin.seedlessDefault(),v->plugin.save("seedlessPolicyV1",v.name()));
        collapse("Keep planted",keepPlantsOpen,()->{keepPlantsOpen=!keepPlantsOpen;general();});
        if(keepPlantsOpen)
        {
            caption("Harvest renewable produce; leave selected flowers and trees standing. Patch and run overrides take precedence.");
            for(KeepCrop crop:KeepCrop.values())if(crop.common)
            {
                JCheckBox keep=check(crop.label,plugin.keepSpecies(crop),
                    on->plugin.save(crop.key(),on?"PRESERVE":"REPLANT"));
                JPanel row=keepRow(crop);row.add(keep,BorderLayout.CENTER);addRow(row);
            }
            caption("Spirit trees and active Guild contracts are protected.");
            heading("Also keep");
            for(KeepCrop crop:KeepCrop.values())if(!crop.common&&plugin.keepSpecies(crop))
            {
                JPanel row=keepRow(crop);
                row.add(new JLabel(crop.label),BorderLayout.CENTER);
                JButton remove=FarmingIcons.button(END,"Stop keeping "+crop.label,FarmingTheme.DANGER,()->{plugin.save(crop.key(),"REPLANT");general();});
                remove.putClientProperty("farming.focusFallback","Add a plant");row.add(remove,BorderLayout.EAST);
                addRow(row);
            }
            JComboBox<Object> alsoKeep=new JComboBox<>();alsoKeep.getAccessibleContext().setAccessibleName("Add a plant");alsoKeep.addItem("Add a plant…");
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
                JComboBox<String> mode=replacementChoice("Default behavior",plugin.setting("replacement_"+type.name(),""));
                mode.addActionListener(e->{plugin.save("replacement_"+type.name(),replacementMode(mode));general();});addChoice(type.getDisplayName(),mode);
            }
        }
        caption("Defaults apply to every run.");finish();

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

    private static JLabel fieldLabel(String name,JComponent control)
    {
        JLabel label=new JLabel(name);
        label.setFont(FarmingTheme.font(Font.PLAIN,11));label.setForeground(FarmingTheme.MUTED);
        label.setLabelFor(control);control.getAccessibleContext().setAccessibleName(name);
        return label;
    }
    private void addChoice(String name,JComboBox<?> box)
    {
        addRow(fieldLabel(name,box));addRow(box);
    }
    private <T> void choice(String name,T[] values,T selected,String tip,Consumer<T> change)
    {
        JComboBox<T> box=new JComboBox<>(values);box.setSelectedItem(selected);box.setToolTipText(tip);
        box.addActionListener(e->change.accept(box.getItemAt(box.getSelectedIndex())));addChoice(name,box);
    }
    private void seedlessChoice(SeedlessPolicy selected,Consumer<SeedlessPolicy> change)
    {choice("Missing planting supplies",SeedlessPolicy.values(),selected,"Guide anyway: show missing supplies. Harvest / clear: clear occupied patches. Skip: omit seedless patches.",change);}

    private void preferenceCheck(String title,String key,boolean fallback)
    {addRow(check(title,plugin.flag(key,fallback),on->plugin.save(key,Boolean.toString(on))));}
    private void groupedPreferences()
    {
        page(GROUPS,"Grouped runs",this::patchBrowser,null,latestGuide!=null);
        caption("Applies to grouped runs only.");
        List<Runnable> refresh=new ArrayList<>();
        for(RunPreset preset:RunPreset.values())
        {
            boolean open=presetExpanded.contains(preset.name());
            JButton fold=collapse(preset.title,open,()->{if(!presetExpanded.add(preset.name()))presetExpanded.remove(preset.name());groupedPreferences();});
            fold.setName("preset/"+preset.name());
            refresh.add(()->{
                long count=preset.types.stream().filter(t->preset.included(t,plugin::flag)).count();
                fold.setText(preset.title+" · "+count+"/"+preset.types.size());
            });
            if(open)for(PatchType type:preset.types)
            {
                JCheckBox check=new JCheckBox(type.getDisplayName());check.setName(preset.key(type));
                check.setMargin(new java.awt.Insets(2,10,2,0));
                refresh.add(()->{
                    check.setSelected(preset.included(type,plugin::flag));
                    check.setToolTipText(plugin.flag("skipType_"+type.name(),false)?"Excluded by the crop type preference. Enable here to remove that exclusion.":null);
                });
                check.addActionListener(e->{
                    if(check.isSelected())plugin.save("skipType_"+type.name(),"false");
                    plugin.save(preset.key(type),Boolean.toString(check.isSelected()));
                    refresh.forEach(Runnable::run);
                });addRow(check);
            }
        }
        refresh.forEach(Runnable::run);
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
        reset.addActionListener(e->{if(confirm(reset,"Clear the last observed bank and Seed Vault quantities?","Reset observed stock","Reset stock",true))plugin.resetObservedStock();});addRow(reset);
        if(latestGuide==null)caption("Reset is available during an active run.");
        heading("Integrations");
        for(String name:List.of("Shortest Path","Bank Tags","Time Tracking"))
        {
            JPanel row=transparent(new BorderLayout(4,0));
            JLabel state=new JLabel(plugin.integrationActive(name)?"Enabled":"Not enabled");
            state.setForeground(FarmingTheme.MUTED);
            row.add(new JLabel(name),BorderLayout.CENTER);row.add(state,BorderLayout.EAST);addRow(row);
        }
        finish();
    }
    private void runSettings()
    {
        if(latestGuide==null){showHome();return;}
        page(RUN_SETTINGS,"Run settings",this::returnToGuide,null,true);
        caption("This run only · Changes expire when it ends.");
        seedlessChoice(SeedlessPolicy.of(plugin.hideSeedless(),plugin.clearSeedless()),plugin::setSeedlessPolicy);
        choice("Crop care",RunPolicy.values(),plugin.runPolicy(),null,v->plugin.setRunPolicy(v,null));
        if(plugin.runHasTrees())
        {
            String[] modes={"Pay to remove","Manual removal"};
            choice("Tree removal",modes,modes[plugin.runPayRemoval()?0:1],null,v->plugin.setRunPolicy(null,v.equals(modes[0])));
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
                        JComboBox<String> mode=replacementChoice("Use preferences",plugin.runReplacement(type));
                    mode.addActionListener(e->plugin.setRunReplacement(type,replacementMode(mode)));addChoice(type.getDisplayName(),mode);
                }
            }
        }
        if(!runTypes.isEmpty()){
            JComboBox<Object> skipType=new JComboBox<>();skipType.addItem("Choose crop type…");
            for(PatchType type:runTypes)skipType.addItem(type);
            skipType.addActionListener(e->{if(skipType.getSelectedItem() instanceof PatchType){
                PatchType type=(PatchType)skipType.getSelectedItem();
                if(confirm(skipType,"Skip remaining "+type.getDisplayName()+" patches in this run?","Skip crop type","Skip "+type.getDisplayName(),true))plugin.skipRemainingType(type);
                skipType.setSelectedIndex(0);
            }});addChoice("Skip remaining crop type",skipType);
        }
        button("All settings",()->settings(true));finish();

    }
    void accessChanged()
    {
        if(navigation.page()==MANUAL && manualPicker!=null)manualPicker.rebuild();
        if(navigation.page()==PATCHES && patchResults!=null){
            if(editingPatch)pendingPatchRefresh=true;
            else fillPatches(patchResults);
        }
    }
    private void patchBrowser()
    {
        page(PATCHES,"Patches",this::preferences,null,latestGuide!=null);
        addRow(navRow("Grouped runs","Customize preset crops",LAYERS,-1,FORWARD,this::groupedPreferences));
        heading("Locations");
        JPanel results=new JPanel();results.setOpaque(false);results.setLayout(new BoxLayout(results,BoxLayout.Y_AXIS));
        patchResults=results;
        searchField(patchSearch,"Search patches…",v->{patchSearch=v;fillPatches(results);});
        JComboBox<String> sort=new JComboBox<>(new String[]{"Location · A–Z","Crop type · A–Z"});sort.setSelectedIndex(patchByLocation?0:1);
        sort.getAccessibleContext().setAccessibleName("Group patches by");
        sort.addActionListener(e->{patchByLocation=sort.getSelectedIndex()==0;patchExpanded.clear();fillPatches(results);});addRow(sort);
        addRow(check("Show locked patches",showLocked,on->{showLocked=on;fillPatches(results);}));
        fillPatches(results);addRow(results);finish();
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
                JButton fold=disclosure(count,open);
                fold.addActionListener(e->{if(!patchExpanded.add(key))patchExpanded.remove(key);fillPatches(results);});
                fold.setName("patch-group/"+key);fold.putClientProperty("farming.focusFallback","Search patches…");resultRow(results,fold);
            }
            else resultRow(results,text(count,FarmingTheme.MUTED));
            if(open)for(FarmRunPatch p:list)
            {
                JPanel row=transparent(new BorderLayout(2,0));row.setBorder(BorderFactory.createEmptyBorder(2,8,2,0));
                JCheckBox enabled=check(patchLabel(p,patchByLocation),plugin.enabled(p),
                    on->{plugin.save("patchEnabled_"+p.key(),Boolean.toString(on));fillPatches(results);});
                enabled.setName("enabled/"+p.key());enabled.putClientProperty("farming.focusFallback","Search patches…");
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
                    String behavior="PRESERVE".equals(saved)?"Keep planted":"REPLANT".equals(saved)?"Harvest & replant":"Use preferences";
                    String description="Edit "+p.getDisplayName()+" · "+(old.isEmpty()?"Automatic":old)+" · "+behavior;
                    JButton edit=FarmingIcons.button(MORE,description,old.isEmpty()&&!KeepCrop.explicit(saved)?FarmingTheme.MUTED:FarmingTheme.SUCCESS);
                    edit.setName("edit/"+p.key());edit.putClientProperty("farming.focusFallback","Search patches…");
                    edit.addActionListener(e->editPatch(p,edit,results,old,saved));
                    row.add(edit,BorderLayout.EAST);
                }
                resultRow(results,row);
            }
        }
        if(results.getComponentCount()==0)resultRow(results,text("No patches match your filters.",FarmingTheme.MUTED));
        results.revalidate();results.repaint();
        if(savedPosition!=null)restorePosition(savedPosition);
    }
    private boolean confirm(JComponent source,Object message,String title,String action,boolean destructive)
    {
        String[] options={action,"Cancel"};
        int result=JOptionPane.showOptionDialog(source,message,title,JOptionPane.DEFAULT_OPTION,JOptionPane.QUESTION_MESSAGE,
            null,options,destructive?"Cancel":action);
        if(!SwingUtilities.isDescendingFrom(source,this))return false;
        source.requestFocusInWindow();
        return result==0;
    }
    private void editPatch(FarmRunPatch patch,JButton edit,JPanel results,String old,String saved)
    {
        JComboBox<String> choose=crops(patch.getPatchType(),"Automatic");
        if(!old.isEmpty())choose.setSelectedItem(old);
        JPanel options=new JPanel();options.setLayout(new BoxLayout(options,BoxLayout.Y_AXIS));
        options.add(fieldLabel("Crop",choose));options.add(choose);
        JComboBox<String> behavior=replacementChoice("Use preferences",saved);
        boolean hasPolicy=CropReplacement.preferenceType(patch.getPatchType());
        if(hasPolicy){options.add(fieldLabel("Crop replacement",behavior));options.add(behavior);}
        FarmingTheme.apply(options);
        editingPatch=true;
        try
        {
            if(confirm(edit,options,patch.getDisplayName()+" crop","Save",false))
            {
                plugin.save("directorCrop_"+patch.key(),choose.getSelectedIndex()==0?"":String.valueOf(choose.getSelectedItem()));
                if(hasPolicy)plugin.save("replacementPatch_"+patch.key(),replacementMode(behavior));
                pendingPatchRefresh=true;
            }
        }
        finally
        {
            editingPatch=false;
            if(pendingPatchRefresh && patchResults==results)fillPatches(results);
            pendingPatchRefresh=false;
        }
    }
    private void priorities()
    {
        page(PRIORITIES,"Crop priorities",this::preferences,null,latestGuide!=null);
        List<PatchType> types=new ArrayList<>(Arrays.asList(PatchType.values()));types.sort(Comparator.comparing(PatchType::getDisplayName,String.CASE_INSENSITIVE_ORDER));
        for(PatchType type:types)if(type!=PatchType.BIRDHOUSE&&type!=PatchType.COMPOST_BIN)
        {
            JButton header=navRow(type.getDisplayName(),plugin.priority(type).isBlank()?"Automatic":"Custom priorities",LEAF,patchSprite(type),priorityOpen==type?COLLAPSE:EXPAND,()->{priorityOpen=priorityOpen==type?null:type;priorities();});
            addRow(header);
            if(priorityOpen==type){JPanel editor=new JPanel();editor.setOpaque(false);editor.setLayout(new BoxLayout(editor,BoxLayout.Y_AXIS));addRow(editor);ranking(type,editor,header);}
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

    private void ranking(PatchType type,JPanel editor,JButton header)
    {
        String csv=plugin.priority(type);
        List<String> names=new ArrayList<>();
        if(!csv.isBlank())names.addAll(Arrays.asList(csv.split(",\\s*")));
        String addKey="add-priority/"+type;
        Runnable save=()->{
            Position saved=position();
            plugin.save("cropPriority_"+type.name(),String.join(", ",names));
            plugin.save("cropCustom_"+type.name(),String.valueOf(!names.isEmpty()));
            navText(header,type.getDisplayName(),names.isEmpty()?"Automatic":"Custom priorities",COLLAPSE);
            editor.removeAll();ranking(type,editor,header);
            editor.revalidate();editor.repaint();restorePosition(saved);
        };
        resultRow(editor,text(names.isEmpty()?"Automatic · highest estimated XP per seed in stock":"Preferred crops first, then highest XP per seed",FarmingTheme.MUTED));
        for(int i=0;i<names.size();i++)
        {
            int n=i;
            String cropName=names.get(i);
            JLabel label=new JLabel((i+1)+". "+cropName);
            Crop crop=CropCatalog.findByName(type,cropName);
            if(crop!=null)plugin.decorateSprite(label,crop.getItemId());
            JButton up=FarmingIcons.button(UP,"Move "+cropName+" up",FarmingTheme.MUTED,()->{Collections.swap(names,n,n-1);save.run();});
            JButton down=FarmingIcons.button(DOWN,"Move "+cropName+" down",FarmingTheme.MUTED,()->{Collections.swap(names,n,n+1);save.run();});
            JButton remove=FarmingIcons.button(END,"Remove "+cropName,FarmingTheme.DANGER,()->{names.remove(n);save.run();});
            up.setEnabled(i>0);down.setEnabled(i+1<names.size());
            up.putClientProperty("farming.focusFallback",key(down));down.putClientProperty("farming.focusFallback",key(up));
            String neighbor=i+1<names.size()?names.get(i+1):i>0?names.get(i-1):null;
            remove.putClientProperty("farming.focusFallback",neighbor==null?addKey:"Remove "+neighbor);
            JPanel buttons=transparent(new FlowLayout(FlowLayout.RIGHT,0,0));
            buttons.add(up);buttons.add(down);buttons.add(remove);
            resultRow(editor,rankingRow(label,buttons));
        }
        JComboBox<String> add=crops(type,"Add a preferred crop…");
        add.setName(addKey);add.getAccessibleContext().setAccessibleName("Add a preferred "+type.getDisplayName()+" crop");
        add.addActionListener(e->{
            if(add.getSelectedIndex()>0){
                String crop=(String)add.getSelectedItem();
                if(!names.contains(crop)){names.add(crop);save.run();}
                else add.setSelectedIndex(0);
            }
        });
        resultRow(editor,add);
        if(!names.isEmpty())
        {
            JButton reset=new JButton("Reset to automatic");reset.putClientProperty("farming.focusFallback",addKey);
            reset.addActionListener(e->{names.clear();save.run();});resultRow(editor,reset);
        }
    }

    private JPanel rankingRow(JLabel name,JPanel buttons)
    {
        BorderLayout layout=new BorderLayout(2,2);
        JPanel row=transparent(layout);
        row.add(name,BorderLayout.CENTER);row.add(buttons,BorderLayout.EAST);
        Runnable fit=()->{
            if(row.getWidth()<=0)return;
            int width=name.getFontMetrics(name.getFont()).stringWidth(name.getText());
            if(name.getIcon()!=null)width+=name.getIcon().getIconWidth()+name.getIconTextGap();
            String side=width+buttons.getPreferredSize().width+layout.getHgap()>row.getWidth()?BorderLayout.SOUTH:BorderLayout.EAST;
            if(!side.equals(layout.getConstraints(buttons))){
                row.remove(buttons);row.add(buttons,side);row.revalidate();row.repaint();
            }
        };
        row.addComponentListener(new java.awt.event.ComponentAdapter(){
            @Override public void componentResized(java.awt.event.ComponentEvent event){fit.run();}
        });
        name.addPropertyChangeListener("icon",e->fit.run());
        return row;
    }
}
