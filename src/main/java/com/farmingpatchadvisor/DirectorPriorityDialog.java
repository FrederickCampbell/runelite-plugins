package com.farmingpatchadvisor;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.*;
import java.util.function.Predicate;

/** Native Swing controls with a separate ranked allowlist and stock reserves. */
final class DirectorPriorityDialog
{
    static final class Result
    {
        final String priorities;
        final String reserves;
        Result(String priorities, String reserves)
        {
            this.priorities = priorities;
            this.reserves = reserves;
        }
    }

    static Result show(Component parent, Predicate<PatchType> types, String csv,
                       String reserves, boolean editReserves)
    {
        List<Crop> catalog = DirectorPriorityEditorModel.catalog(types);
        List<Crop> ranked = DirectorPriorityEditorModel.selected(catalog, csv);
        Map<String, Integer> reserved = DirectorPlanner.parseReserves(reserves);
        DefaultListModel<Crop> model = new DefaultListModel<>();
        JList<Crop> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setVisibleRowCount(7);
        list.setCellRenderer(new DefaultListCellRenderer()
        {
            @Override public Component getListCellRendererComponent(JList<?> l, Object value,
                int index, boolean selected, boolean focused)
            {
                Crop crop = (Crop) value;
                int held = reserved.getOrDefault(DirectorPlanner.normalize(crop.getName()), 0);
                return super.getListCellRendererComponent(l,
                    (index + 1) + ". " + crop.getName() + " · lvl " + crop.getLevel()
                        + (held > 0 ? " · keep " + held : ""), index, selected, focused);
            }
        });
        JComboBox<Crop> available = new JComboBox<>(catalog.toArray(new Crop[0]));
        available.setRenderer(new DefaultListCellRenderer()
        {
            @Override public Component getListCellRendererComponent(JList<?> l, Object value,
                int index, boolean selected, boolean focused)
            {
                Crop crop = (Crop) value;
                return super.getListCellRendererComponent(l, crop == null ? "" :
                    crop.getName() + " · " + crop.getPatchType().getDisplayName() +
                    " · lvl " + crop.getLevel(), index, selected, focused);
            }
        });
        Runnable redraw = () ->
        {
            int index = list.getSelectedIndex();
            model.clear();
            ranked.forEach(model::addElement);
            if (!ranked.isEmpty()) list.setSelectedIndex(Math.max(0, Math.min(index, ranked.size() - 1)));
        };
        redraw.run();
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        JPanel top = new JPanel(new BorderLayout(4, 4));
        top.add(new JLabel("First eligible crop with sufficient stock wins."), BorderLayout.NORTH);
        top.add(available, BorderLayout.CENTER);
        JButton add = new JButton("Add");
        add.addActionListener(e ->
        {
            Crop choice = (Crop) available.getSelectedItem();
            if (choice != null && !ranked.contains(choice))
            {
                ranked.add(choice); redraw.run(); list.setSelectedIndex(ranked.size() - 1);
            }
        });
        top.add(add, BorderLayout.EAST);
        panel.add(top, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(360, 180));
        panel.add(scroll, BorderLayout.CENTER);
        JPanel controls = new JPanel(new GridLayout(0, 2, 4, 4));
        for (int direction : new int[]{-1, 1})
        {
            JButton button = new JButton(direction < 0 ? "Move up" : "Move down");
            button.addActionListener(e ->
            {
                int index = list.getSelectedIndex();
                if (DirectorPriorityEditorModel.move(ranked, index, direction))
                {
                    redraw.run(); list.setSelectedIndex(index + direction);
                }
            });
            controls.add(button);
        }
        JButton remove = new JButton("Remove selected");
        remove.addActionListener(e -> { int i = list.getSelectedIndex(); if (i >= 0) { ranked.remove(i); redraw.run(); } });
        controls.add(remove);
        JButton all = new JButton("Add remaining crops");
        all.addActionListener(e -> { for (Crop crop : catalog) if (!ranked.contains(crop)) ranked.add(crop); redraw.run(); });
        controls.add(all);
        JButton clear = new JButton("Clear list");
        clear.addActionListener(e -> { ranked.clear(); redraw.run(); });
        controls.add(clear);
        if (editReserves)
        {
            JButton reserve = new JButton("Reserve stock…");
            reserve.addActionListener(e ->
            {
                Crop crop = (Crop) available.getSelectedItem();
                if (crop == null) return;
                String key = DirectorPlanner.normalize(crop.getName());
                JSpinner count = new JSpinner(new SpinnerNumberModel(
                    reserved.getOrDefault(key, 0).intValue(), 0, Integer.MAX_VALUE, 1));
                if (JOptionPane.showConfirmDialog(panel, count, "Keep " + crop.getName() + " in stock",
                    JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION)
                {
                    reserved.put(key, (Integer) count.getValue());
                    list.repaint();
                }
            });
            controls.add(reserve);
        }
        panel.add(controls, BorderLayout.SOUTH);
        if (JOptionPane.showConfirmDialog(parent, panel, "Crop priority order",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return null;
        return new Result(DirectorPriorityEditorModel.encode(ranked),
            DirectorPriorityEditorModel.encodeReserves(reserved));
    }
}
