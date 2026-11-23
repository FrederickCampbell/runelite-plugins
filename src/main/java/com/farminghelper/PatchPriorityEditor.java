package com.farminghelper;

import java.awt.BorderLayout;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.swing.*;

/** Crop preferences UI; game state and navigation are supplied by its controller. */
final class PatchPriorityEditor
{
    private PatchPriorityEditor() {}
    static JPanel build(PatchType type, PatchPriorityStore priorityStore, boolean canEdit,
        boolean loggedIn, int farmingLevel, Set<PatchType> expandedAdvanced,
        Runnable changed, Runnable openRoute)
    {
        JPanel editor = FarmingHelperComponents.column(4);
        editor.setBackground(FarmingHelperTheme.SURFACE);
        editor.setBorder(BorderFactory.createEmptyBorder(4, 5, 4, 5));
        List<Crop> catalog = PriorityEditorModel.catalog(t -> t == type);
        if (catalog.isEmpty()) return editor;
        PatchPriority pref = priorityStore.get(type);
        List<Crop> ranked = PriorityEditorModel.selected(catalog, pref.csv);
        FarmingHelperComponents.addFull(editor, FarmingHelperComponents.label("Crop preference", true));
        if (loggedIn && PatchPriority.plantingLevel(type) > farmingLevel)
        {
            JLabel locked = FarmingHelperComponents.label("Planting unlocks at " + PatchPriority.plantingLevel(type) + " Farming.", false);
            locked.setForeground(FarmingHelperTheme.GOLD);
            FarmingHelperComponents.addFull(editor, locked);
        }
        JComboBox<String> mode = new JComboBox<>(new String[]{"Automatic", "Custom priorities"});
        mode.setSelectedIndex(pref.custom ? 1 : 0);
        mode.setEnabled(canEdit);
        mode.addActionListener(e ->
        {
            priorityStore.save(type, mode.getSelectedIndex() == 1, ranked, pref.reserves);
            SwingUtilities.invokeLater(changed);
        });
        FarmingHelperComponents.addFull(editor, mode);
        if (pref.custom)
        {
            for (int i = 0; i <= ranked.size() && i < catalog.size(); i++)
            {
                final int index = i;
                Crop current = i < ranked.size() ? ranked.get(i) : null;
                List<Crop> options = new ArrayList<>();
                options.add(null);
                for (Crop crop : catalog) if (crop.equals(current) || !ranked.contains(crop)) options.add(crop);
                JComboBox<Crop> choice = new JComboBox<>(options.toArray(new Crop[0]));
                choice.setRenderer(new javax.swing.DefaultListCellRenderer()
                {
                    @Override public Component getListCellRendererComponent(javax.swing.JList<?> list,
                        Object value, int row, boolean selected, boolean focus)
                    {
                        Crop crop = (Crop)value;
                        return super.getListCellRendererComponent(list,
                            crop == null ? "Choose crop…" : crop.getName() + " (" + crop.getLevel() + ")", row, selected, focus);
                    }
                });
                choice.setSelectedItem(current);
                choice.setEnabled(canEdit);
                choice.addActionListener(e ->
                {
                    Crop selected = (Crop)choice.getSelectedItem();
                    if (index < ranked.size())
                    {
                        if (selected == null) ranked.remove(index);
                        else ranked.set(index, selected);
                    }
                    else if (selected != null) ranked.add(selected);
                    priorityStore.save(type, true, ranked, pref.reserves);
                    SwingUtilities.invokeLater(changed);
                });
                JPanel row = new JPanel(new BorderLayout(3, 0));
                row.setBackground(FarmingHelperTheme.SURFACE);
                row.add(new JLabel("#" + (i + 1)), BorderLayout.WEST);
                row.add(choice, BorderLayout.CENTER);
                if (current != null && i > 0)
                {
                    JButton up = new JButton("↑");
                    up.setToolTipText("Move preference up");
                    up.setEnabled(canEdit);
                    up.addActionListener(e ->
                    {
                        PriorityEditorModel.move(ranked, index, -1);
                        priorityStore.save(type, true, ranked, pref.reserves);
                        changed.run();
                    });
                    row.add(up, BorderLayout.EAST);
                }
                FarmingHelperComponents.addFull(editor, row);
            }
            FarmingHelperComponents.addFull(editor, FarmingHelperComponents.label("First eligible available crop wins.", false));
        }
        else FarmingHelperComponents.addFull(editor, FarmingHelperComponents.label("Highest eligible crop in stock.", false));
        JPanel advanced = FarmingHelperComponents.column(4);
        advanced.setBackground(FarmingHelperTheme.SURFACE);
        JButton reservesButton = new JButton("Keep seeds in reserve…");
        reservesButton.setToolTipText("Keep a minimum stock for contracts or future runs");
        reservesButton.setEnabled(canEdit);
        reservesButton.addActionListener(e ->
        {
            PriorityDialog.Result result = PriorityDialog.show(editor, t -> t == type,
                pref.csv, pref.reserves, true);
            if (result != null)
            {
                priorityStore.save(type, pref.custom,
                    PriorityEditorModel.selected(catalog, result.priorities), result.reserves);
                changed.run();
            }
        });
        FarmingHelperComponents.addFull(advanced, reservesButton);
        JButton route = new JButton("Route & teleports…");
        route.addActionListener(e -> openRoute.run());
        FarmingHelperComponents.addFull(advanced, route);
        JButton more = FarmingHelperComponents.row((expandedAdvanced.contains(type) ? "▾ " : "▸ ") + "Advanced");
        advanced.setVisible(expandedAdvanced.contains(type));
        more.addActionListener(e -> { if (!expandedAdvanced.add(type)) expandedAdvanced.remove(type); advanced.setVisible(expandedAdvanced.contains(type)); more.setText((advanced.isVisible() ? "▾ " : "▸ ") + "Advanced"); editor.revalidate(); });
        FarmingHelperComponents.addFull(editor, more);
        FarmingHelperComponents.addFull(editor, advanced);
        return editor;
    }

}
