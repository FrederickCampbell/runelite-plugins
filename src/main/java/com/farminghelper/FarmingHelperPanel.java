package com.farminghelper;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.util.HashMap;
import java.util.Map;
import javax.swing.plaf.basic.BasicButtonUI;
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
    private GuideView previousGuide;
    private final Map<String,JTextArea> itemNames=new HashMap<>(),stepText=new HashMap<>();
    private final Map<String,JLabel> itemQuantities=new HashMap<>();
    private static final Color ACTIVE=new Color(58,74,54), ACTIVE_TEXT=new Color(178,222,159);
    private final Set<String> manual = new java.util.LinkedHashSet<>();
    private final Set<PatchType> manualExpanded = EnumSet.noneOf(PatchType.class);

    FarmingHelperPanel(FarmingHelperPlugin plugin)
    {
        super(false);
        this.plugin = plugin;
        setLayout(new BorderLayout());
        rows.setLayout(new BoxLayout(rows,BoxLayout.Y_AXIS));
        rows.setBorder(BorderFactory.createEmptyBorder(6,6,6,6));
        JPanel top = new JPanel(new BorderLayout());
        top.add(rows, BorderLayout.NORTH);
        scroll = new JScrollPane(top);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll);
    }

    private void addRow(JComponent row)
    {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(row);
        wrapper.setBorder(BorderFactory.createEmptyBorder(2,0,2,0));
        rows.add(wrapper);
    }

    private void heading(String text)
    {
        JLabel label = new JLabel(text);
        label.setFont(FontManager.getRunescapeBoldFont());
        label.setForeground(Color.WHITE);
        addRow(label);
    }

    private void line(String text, boolean active)
    {
        String escaped = text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\n","<br>");
        JLabel label = new JLabel("<html><body style='width:132pt'>" + escaped + "</body></html>");
        label.setOpaque(active);
        label.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        label.setForeground(active ? ColorScheme.BRAND_ORANGE : Color.LIGHT_GRAY);
        label.setFont(FontManager.getRunescapeFont());
        label.setBorder(BorderFactory.createEmptyBorder(3,4,3,4));
        addRow(label);
    }

    private void button(String text, Runnable action)
    {
        JButton button = new JButton(text);
        button.setFont(FontManager.getRunescapeBoldFont());
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(ColorScheme.DARK_GRAY_COLOR.brighter()),BorderFactory.createEmptyBorder(8,8,8,8)));
        button.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        button.addActionListener(e -> action.run());
        addRow(button);
    }

    private void finish() { rows.revalidate(); rows.repaint(); }

    @Override public void onDeactivate() { plugin.stop(); }

    void home()
    {
        rows.removeAll();
        expanded.clear();
        lastActive = null;
        previousGuide=null;
        heading("Farming runs");
        for (FarmRunType type : FarmRunType.values())
            button(type.getDisplayName(), () -> plugin.start(EnumSet.of(type)));
        button("Manual Run…", this::openManual);
        button("Preferences…", this::preferences);
        finish();
    }

    private void openManual()
    {
        manual.clear();manual.addAll(ManualSelection.decode(plugin.setting("manualPatchChoicesV1",null),plugin.patches()));
        manualExpanded.clear();manualRun();scroll.getVerticalScrollBar().setValue(0);
    }
    private boolean selectable(FarmRunPatch p){return plugin.enabled(p)&&plugin.accessNote(p).isEmpty();}
    private void saveManual(){plugin.save("manualPatchChoicesV1",ManualSelection.encode(manual));}
    private void manualRun()
    {
        int y=scroll.getVerticalScrollBar().getValue();
        rows.removeAll();heading("Manual Run");
        line("Choose crops for this run. Expand a category to exclude individual patches. Permanent preferences still apply.",false);
        JPanel controls=new JPanel(new java.awt.GridLayout(1,2,4,0));
        JButton all=new JButton("Select all"),none=new JButton("Clear all");
        all.addActionListener(e->{ManualSelection.select(manual,plugin.patches(),this::selectable,true);saveManual();manualRun();});
        none.addActionListener(e->{manual.clear();saveManual();manualRun();});
        controls.add(all);controls.add(none);addRow(controls);
        long count=plugin.patches().stream().filter(this::selectable).filter(p->manual.contains(p.key())).count();
        line(count+" patches selected · growing patches skipped",false);
        JButton start=new JButton("Start Manual Run");start.setFont(FontManager.getRunescapeBoldFont());start.setEnabled(count>0);
        start.addActionListener(e->{Set<String> selected=new java.util.LinkedHashSet<>();for(FarmRunPatch p:plugin.patches())if(selectable(p)&&manual.contains(p.key()))selected.add(p.key());plugin.startManual(selected);});addRow(start);
        for(PatchType type:PatchType.values())
        {
            List<FarmRunPatch> list=new ArrayList<>();for(FarmRunPatch p:plugin.patches())if(p.getPatchType()==type)list.add(p);
            if(list.isEmpty())continue;
            long available=list.stream().filter(this::selectable).count();
            long selected=list.stream().filter(this::selectable).filter(p->manual.contains(p.key())).count();
            JPanel header=new JPanel(new BorderLayout(2,0));header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            JCheckBox check=new JCheckBox(type.getDisplayName()+"  ("+selected+"/"+available+")",available>0&&selected==available);
            check.setFont(FontManager.getRunescapeFont());check.setOpaque(false);check.setEnabled(available>0);
            check.setToolTipText(selected>0&&selected<available?"Partially selected; click to select all available patches":"Toggle this crop category");
            check.addActionListener(e->{ManualSelection.select(manual,list,this::selectable,check.isSelected());saveManual();manualRun();});
            JButton fold=new JButton(manualExpanded.contains(type)?"▾":"▸");fold.setUI(new BasicButtonUI());fold.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            fold.setToolTipText("Expand individual patches");fold.setMargin(new java.awt.Insets(2,5,2,5));
            fold.addActionListener(e->{if(!manualExpanded.add(type))manualExpanded.remove(type);manualRun();});
            header.add(check,BorderLayout.CENTER);header.add(fold,BorderLayout.EAST);addRow(header);
            if(manualExpanded.contains(type))for(FarmRunPatch p:list)
            {
                JCheckBox child=new JCheckBox(p.getDisplayName(),selectable(p)&&manual.contains(p.key()));
                child.setFont(FontManager.getRunescapeFont());child.setBorder(BorderFactory.createEmptyBorder(2,12,2,0));
                child.setEnabled(selectable(p));child.setToolTipText(!plugin.enabled(p)?"Disabled in permanent preferences":plugin.accessNote(p));
                child.addActionListener(e->{if(child.isSelected())manual.add(p.key());else manual.remove(p.key());saveManual();manualRun();});addRow(child);
            }
        }
        button("Back",this::home);finish();SwingUtilities.invokeLater(()->scroll.getVerticalScrollBar().setValue(y));
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
    private static String stockTip(GuideView.ItemRow row){return "Last observed bank: "+row.bank+" · Seed Vault: "+row.vault;}
    private boolean sameStructure(GuideView a,GuideView b)
    {
        if(a==null || a.active==null || b.active!=a.active || !a.title.equals(b.title) || a.items.size()!=b.items.size() || a.stops.size()!=b.stops.size())return false;
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
            quantity.setText(r.owned+"/"+r.needed);quantity.setForeground(itemColor(r));quantity.setToolTipText(stockTip(r));
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
            JLabel quantity=new JLabel(r.owned+"/"+r.needed);quantity.setForeground(c);
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
        JButton close=new JButton("×");close.setUI(new BasicButtonUI());close.setBorderPainted(false);close.setContentAreaFilled(false);
        close.setToolTipText("Close helper");close.addActionListener(e->plugin.stop());header.add(close,BorderLayout.EAST);addRow(header);
        requirements("Item requirements:",view.items,false);
        requirements("Recommended items:",view.items,true);
        if(view.active==null)line(view.message,true);
        for(GuideView.Stop stop:view.stops)
        {
            JPanel section=new JPanel(new BorderLayout(0,1));section.setBorder(BorderFactory.createEmptyBorder(5,0,0,0));
            boolean open=expanded.getOrDefault(stop.patch.key(),stop.active);
            Color bg=stop.active?ACTIVE:ColorScheme.DARKER_GRAY_COLOR.darker();
            JButton fold=new JButton((open?"▾ ":"▸ ")+(stop.done?"✓ ":"")+stop.patch.guideLabel()+(view.title.equals("Manual Run")?" · "+stop.patch.getPatchType().getDisplayName():""));
            fold.setUI(new BasicButtonUI());fold.setFont(FontManager.getRunescapeBoldFont());fold.setHorizontalAlignment(SwingConstants.LEFT);
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
                    JButton skip=new JButton("Skip step");skip.addActionListener(e->plugin.skip(stop.patch));body.add(skip,BorderLayout.SOUTH);
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
            for (PatchType type : PatchType.values()) button(type.getDisplayName(), () -> patches(type));
        }
        button("Back",this::home);
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
        patches(type);
    }

    private void patches(PatchType type)
    {
        rows.removeAll();heading(type.getDisplayName());
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
