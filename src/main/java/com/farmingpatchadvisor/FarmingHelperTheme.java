package com.farmingpatchadvisor;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicArrowButton;
import javax.swing.plaf.basic.BasicSpinnerUI;

/** Local component theme. Never changes RuneLite's process-wide LookAndFeel. */
final class FarmingHelperTheme
{
    static final Color BASE = new Color(34,40,36), SURFACE = new Color(48,58,48),
        SELECTED = new Color(53,75,53), BORDER = new Color(66,82,65),
        PRIMARY = new Color(88,125,66), TEXT = new Color(226,235,220),
        MUTED = new Color(170,185,163), SUCCESS = new Color(165,209,154),
        GOLD = new Color(212,179,107);
    private static final String STYLED = "fh.styled";
    private FarmingHelperTheme() {}

    static void style(Component component)
    {
        if (component instanceof JComponent)
        {
            JComponent c = (JComponent) component;
            if (c.getClientProperty(STYLED) == null)
            {
                c.putClientProperty(STYLED, true);
                if (c instanceof JCheckBox)
                {
                    JCheckBox check = (JCheckBox)c;
                    check.setUI(new javax.swing.plaf.basic.BasicCheckBoxUI());
                    check.setIcon(new CheckIcon());
                    check.setSelectedIcon(check.getIcon());
                    check.setDisabledIcon(check.getIcon());
                    check.setDisabledSelectedIcon(check.getIcon());
                    check.setBackground(BASE);
                    check.setForeground(TEXT);
                    check.setOpaque(false);
                    check.setFocusPainted(false);
                    check.setIconTextGap(7);
                }
                else if (c instanceof JButton && !(c instanceof BasicArrowButton))
                {
                    JButton button = (JButton)c;
                    button.setUI(new ButtonUI());
                    button.setOpaque(false);
                    button.setContentAreaFilled(false);
                    button.setBorder(new EmptyBorder(6, 8, 6, 8));
                    button.setBackground(SURFACE);
                    button.setForeground(TEXT);
                    button.setFocusPainted(false);
                    button.setRolloverEnabled(true);
                    button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                }
                else if (c instanceof JComboBox)
                {
                    JComboBox<?> combo = (JComboBox<?>)c;
                    combo.setBackground(SURFACE); combo.setForeground(TEXT);
                    combo.setUI(new ComboUI());
                    combo.setBorder(BorderFactory.createLineBorder(BORDER));
                    themeRenderer(combo);
                }
                else if (c instanceof JSpinner)
                {
                    JSpinner spinner = (JSpinner)c;
                    spinner.setUI(new SpinnerUI());
                    spinner.setBackground(SURFACE); spinner.setForeground(TEXT);
                    spinner.setBorder(BorderFactory.createLineBorder(BORDER));
                }
                else if (c instanceof JTextField)
                {
                    JTextField field = (JTextField)c;
                    field.setBackground(SURFACE); field.setForeground(TEXT); field.setCaretColor(TEXT);
                    field.setSelectionColor(SELECTED); field.setSelectedTextColor(TEXT);
                    field.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER), new EmptyBorder(3,5,3,5)));
                }
                else if (c instanceof JLabel)
                {
                    Color fg = c.getForeground();
                    if (fg == null || fg instanceof UIResource || fg.getRed()+fg.getGreen()+fg.getBlue()<220)
                        c.setForeground(TEXT);
                }
                else if (c instanceof JPanel || c instanceof JViewport || c instanceof JScrollPane)
                {
                    Color bg = c.getBackground();
                    if (bg == null || bg instanceof UIResource) c.setBackground(BASE);
                    c.setForeground(TEXT);
                }
                else if (c instanceof JList)
                {
                    JList<?> list = (JList<?>)c;
                    list.setBackground(BASE); list.setForeground(TEXT);
                    list.setSelectionBackground(SELECTED); list.setSelectionForeground(TEXT);
                }
                else if (c instanceof JProgressBar)
                {
                    c.setBackground(SURFACE); c.setForeground(PRIMARY);
                }
                else if (c instanceof JScrollBar)
                {
                    c.setBackground(BASE); c.setForeground(BORDER);
                }
                else if (c instanceof JSeparator) c.setForeground(BORDER);
            }
        }
        if (component instanceof Container)
            for (Component child : ((Container)component).getComponents()) style(child);
    }

    @SuppressWarnings({"rawtypes","unchecked"})
    private static void themeRenderer(JComboBox combo)
    {
        ListCellRenderer original = combo.getRenderer();
        if (original == null) original = new DefaultListCellRenderer();
        final ListCellRenderer delegate = original;
        combo.setRenderer((list, value, index, selected, focus) ->
        {
            Component cell = delegate.getListCellRendererComponent(list,value,index,selected,focus);
            cell.setBackground(selected ? SELECTED : SURFACE);
            cell.setForeground(TEXT);
            if (cell instanceof JComponent) ((JComponent)cell).setOpaque(true);
            return cell;
        });
    }

    private static final class ButtonUI extends BasicButtonUI
    {
        @Override public void paint(Graphics graphics, JComponent component)
        {
            JButton b = (JButton)component;
            Graphics2D g = (Graphics2D)graphics.create();
            try
            {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                boolean primary = Boolean.TRUE.equals(b.getClientProperty("fh.primary"));
                Color fill = !b.isEnabled() ? BASE : b.getModel().isPressed() ? SELECTED
                    : b.getModel().isRollover() ? (primary ? PRIMARY.brighter() : SELECTED)
                    : primary ? PRIMARY : SURFACE;
                g.setColor(fill); g.fillRoundRect(0,0,b.getWidth()-1,b.getHeight()-1,7,7);
                g.setColor(b.hasFocus() ? SUCCESS : BORDER);
                g.drawRoundRect(0,0,b.getWidth()-1,b.getHeight()-1,7,7);
            }
            finally { g.dispose(); }
            super.paint(graphics, component);
        }
    }

    private static final class CheckIcon implements Icon
    {
        public int getIconWidth(){ return 17; }
        public int getIconHeight(){ return 17; }
        public void paintIcon(Component c, Graphics graphics, int x, int y)
        {
            AbstractButton button = (AbstractButton)c;
            Graphics2D g=(Graphics2D)graphics.create();
            try
            {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(button.isSelected() ? PRIMARY : SURFACE);
                g.fillRoundRect(x,y,16,16,5,5);
                g.setColor(button.isEnabled() ? SUCCESS : MUTED);
                g.drawRoundRect(x,y,16,16,5,5);
                if(button.isSelected())
                {
                    g.setStroke(new BasicStroke(2f)); g.drawLine(x+4,y+8,x+7,y+11); g.drawLine(x+7,y+11,x+13,y+5);
                }
            }
            finally {g.dispose();}
        }
    }
    private static final class ComboUI extends BasicComboBoxUI
    {
        @Override protected JButton createArrowButton()
        {
            return new BasicArrowButton(SwingConstants.SOUTH, SURFACE, BORDER, TEXT, SURFACE);
        }
        @Override public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean focus)
        { g.setColor(SURFACE); g.fillRect(bounds.x,bounds.y,bounds.width,bounds.height); }
    }
    private static final class SpinnerUI extends BasicSpinnerUI
    {
        @Override protected Component createNextButton()
        {
            JButton b = new BasicArrowButton(SwingConstants.NORTH,SURFACE,BORDER,TEXT,SURFACE);
            b.setName("Spinner.nextButton"); installNextButtonListeners(b); return b;
        }
        @Override protected Component createPreviousButton()
        {
            JButton b = new BasicArrowButton(SwingConstants.SOUTH,SURFACE,BORDER,TEXT,SURFACE);
            b.setName("Spinner.previousButton"); installPreviousButtonListeners(b); return b;
        }
    }
}
