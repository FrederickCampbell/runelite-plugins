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
    private final Set<FarmRunType> combined = EnumSet.noneOf(FarmRunType.class);

    FarmingHelperPanel(FarmingHelperPlugin plugin)
    {
        super(false);
        this.plugin = plugin;
        setLayout(new BorderLayout());
        rows.setLayout(new BoxLayout(rows,BoxLayout.Y_AXIS));
        rows.setBorder(BorderFactory.createEmptyBorder(6,6,6,6));
        JPanel top = new JPanel(new BorderLayout());
        top.add(rows, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(top);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll);
    }

    private void addRow(JComponent row)
    {
        JPanel wrapper = new JPanel(new BorderLayout());
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
        heading("Farming runs");
        for (FarmRunType type : FarmRunType.values())
            button(type.getDisplayName(), () -> plugin.start(EnumSet.of(type)));
        button("Combined run…", this::combinedRun);
        button("Preferences…", this::preferences);
        finish();
    }

    private void combinedRun()
    {
        rows.removeAll();
        heading("Combined run");
        for (FarmRunType type : FarmRunType.values())
        {
            JCheckBox select = new JCheckBox(type.getDisplayName(),combined.contains(type));
            select.addActionListener(e -> {
                if (select.isSelected()) combined.add(type); else combined.remove(type);
            });
            addRow(select);
        }
        button("Start", () -> { if (!combined.isEmpty()) plugin.start(EnumSet.copyOf(combined)); });
        button("Back",this::home);
        finish();
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
        label.setForeground(Color.WHITE);
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

    private void requirements(String name, List<GuideView.ItemRow> items, boolean recommended)
    {
        JPanel group=group(name,ColorScheme.DARKER_GRAY_COLOR);
        JPanel body=new JPanel();body.setLayout(new BoxLayout(body,BoxLayout.Y_AXIS));body.setOpaque(false);
        for(GuideView.ItemRow r:items)
        {
            if(r.recommended!=recommended)continue;
            JPanel row=new JPanel(new BorderLayout(4,0));row.setOpaque(false);
            Color c=r.owned>=r.needed?new Color(100,205,100):new Color(240,105,105);
            JTextArea label=text(r.name,c);
            JLabel quantity=new JLabel(r.owned+"/"+r.needed);quantity.setForeground(c);
            quantity.setFont(FontManager.getRunescapeFont());
            String tip="Observed bank: "+r.bank+" · Seed Vault: "+r.vault;
            label.setToolTipText(tip);quantity.setToolTipText(tip);
            row.add(label,BorderLayout.CENTER);row.add(quantity,BorderLayout.EAST);body.add(row);
        }
        if(body.getComponentCount()==0)return;
        group.add(body,BorderLayout.CENTER);addRow(group);
    }

    void guide(GuideView view)
    {
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
            Color bg=stop.active?ColorScheme.BRAND_ORANGE:ColorScheme.DARKER_GRAY_COLOR.darker();
            JButton fold=new JButton((open?"▾ ":"▸ ")+(stop.done?"✓ ":"")+stop.patch.guideLabel());
            fold.setUI(new BasicButtonUI());fold.setFont(FontManager.getRunescapeBoldFont());fold.setHorizontalAlignment(SwingConstants.LEFT);
            fold.setBackground(bg);fold.setForeground(stop.active?Color.BLACK:Color.WHITE);
            fold.setBorder(BorderFactory.createEmptyBorder(7,7,5,7));
            fold.addActionListener(e->{expanded.put(stop.patch.key(),!open);guide(view);});section.add(fold,BorderLayout.NORTH);
            if(open)
            {
                JPanel body=new JPanel(new BorderLayout());body.setBackground(ColorScheme.DARKER_GRAY_COLOR);
                body.setBorder(BorderFactory.createEmptyBorder(5,5,5,5));
                body.add(text(stop.instruction,stop.done?new Color(100,205,100):Color.WHITE),BorderLayout.CENTER);
                if(stop.active)
                {
                    JButton skip=new JButton("Skip step");skip.addActionListener(e->plugin.skip(stop.patch));body.add(skip,BorderLayout.SOUTH);
                }
                section.add(body,BorderLayout.CENTER);
            }
            addRow(section);
        }
        finish();
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
        line(names.isEmpty()?"Automatic · highest plantable crop in stock":"Preferred crops, in order",false);
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
            JButton edit=new JButton("⋯");edit.setToolTipText("Crop override: "+(plugin.override(p).isEmpty()?"Automatic":plugin.override(p)));
            edit.addActionListener(e->{JComboBox<String> choose=crops(type,"Automatic");String old=plugin.override(p);if(!old.isEmpty())choose.setSelectedItem(old);
                if(JOptionPane.showConfirmDialog(this,choose,p.getDisplayName()+" crop",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION){plugin.save("directorCrop_"+p.key(),choose.getSelectedIndex()==0?"":String.valueOf(choose.getSelectedItem()));patches(type);}});
            row.add(edit,BorderLayout.EAST);addRow(row);
        }
        button("Back",this::preferences);finish();
    }
}
