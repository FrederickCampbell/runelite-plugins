package com.farminghelper;

import java.awt.*;
import java.awt.image.BufferedImage;
import javax.swing.*;

/** Reusable, width-aware sidebar primitives. No global Swing settings. */
final class FarmingHelperComponents
{
    private FarmingHelperComponents() {}

    static void addFull(JPanel parent, Component child)
    {
        if (child instanceof javax.swing.JComponent)
        {
            javax.swing.JComponent component = (javax.swing.JComponent) child;
            component.setAlignmentX(Component.LEFT_ALIGNMENT);
            FarmingHelperTheme.style(child);
            if (component instanceof JButton)
            {
                JButton button = (JButton) component;
                button.setMargin(new java.awt.Insets(4, 6, 4, 6));
                if (button.getText() != null && button.getText().length() > 24
                    && !button.getText().startsWith("<html>"))
                    button.setText(wrapped(button.getText(), 164));
            }
            else if (component instanceof JCheckBox)
            {
                JCheckBox check = (JCheckBox) component;
                if (check.getText() != null && check.getText().length() > 23
                    && !check.getText().startsWith("<html>"))
                    check.setText(wrapped(check.getText(), 164));
            }
            component.setMaximumSize(new Dimension(Integer.MAX_VALUE,
                child instanceof JButton ? Math.max(28, component.getPreferredSize().height)
                    : child instanceof JTextField || child instanceof JComboBox ? 28
                    : child instanceof javax.swing.JSeparator ? 2 : Integer.MAX_VALUE));
        }
        parent.add(child);
        parent.add(Box.createVerticalStrut(3));
    }

    static JLabel label(String text, boolean strong)
    {
        JLabel l = new JLabel(wrapped(text, 184));
        l.setFont(strong ? FarmingHelperTheme.HEADING : FarmingHelperTheme.BODY);
        l.setForeground(strong ? FarmingHelperTheme.TEXT : FarmingHelperTheme.MUTED);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private static String wrapped(String value, int width)
    {
        return "<html><body style='font-family: sans-serif; width:" + Math.min(width,170) + "px'>"
            + value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;") + "</body></html>";
    }


    static JPanel column(int inset)
    {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(FarmingHelperTheme.BASE);
        panel.setBorder(BorderFactory.createEmptyBorder(inset,inset,inset,inset));
        return panel;
    }

    static JButton row(String text)
    {
        JButton button = new JButton(text);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setIconTextGap(6);
        FarmingHelperTheme.role(button, "quiet");
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        return button;
    }

    static JLabel caption(String text)
    {
        JLabel label = new JLabel(text);
        label.setForeground(FarmingHelperTheme.MUTED);
        label.setFont(FarmingHelperTheme.BODY);
        return label;
    }

    static JPanel header(String text, JButton leading, JButton trailing)
    {
        JPanel bar = new JPanel(new BorderLayout(5,0));
        bar.setBackground(FarmingHelperTheme.BASE);
        JLabel title = new JLabel(text);
        title.setFont(FarmingHelperTheme.TITLE);
        title.setForeground(FarmingHelperTheme.TEXT);
        if (leading != null) bar.add(leading, BorderLayout.WEST);
        bar.add(title, BorderLayout.CENTER);
        if (trailing != null) bar.add(trailing, BorderLayout.EAST);
        return bar;
    }

    static Icon sprite(BufferedImage image)
    {
        if (image == null) return null;
        int x1=image.getWidth(), y1=image.getHeight(), x2=-1,y2=-1;
        for(int y=0;y<image.getHeight();y++) for(int x=0;x<image.getWidth();x++)
            if ((image.getRGB(x,y) >>> 24) != 0) { x1=Math.min(x1,x); y1=Math.min(y1,y); x2=Math.max(x2,x); y2=Math.max(y2,y); }
        if(x2<x1 || y2<y1) return new ImageIcon(new BufferedImage(22,22,BufferedImage.TYPE_INT_ARGB));
        int w=x2-x1+1,h=y2-y1+1;
        double scale=Math.min(20.0/w,20.0/h);
        int sw=Math.max(1,(int)Math.round(w*scale)),sh=Math.max(1,(int)Math.round(h*scale));
        BufferedImage out=new BufferedImage(22,22,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            int x=(22-sw)/2,y=(22-sh)/2;
            g.drawImage(image,x,y,x+sw,y+sh,x1,y1,x2+1,y2+1,null);
        } finally { g.dispose(); }
        return new ImageIcon(out);
    }

    static final class SearchField extends JTextField
    {
        SearchField(){ setToolTipText("Find a patch or location"); }
        @Override protected void paintComponent(Graphics graphics)
        {
            super.paintComponent(graphics);
            if(getText().isEmpty() && !hasFocus())
            {
                Graphics2D g=(Graphics2D)graphics.create();
                try { g.setFont(FarmingHelperTheme.BODY); g.setColor(FarmingHelperTheme.MUTED);
                    g.drawString("Find a patch…",getInsets().left+2,(getHeight()+g.getFontMetrics().getAscent()-g.getFontMetrics().getDescent())/2);
                } finally {g.dispose();}
            }
        }
    }
}
