package com.farminghelper;

import java.awt.*;
import java.awt.image.BufferedImage;
import javax.swing.*;

/** Reusable, width-aware sidebar primitives. No global Swing settings. */
final class FarmingHelperComponents
{
    private FarmingHelperComponents() {}

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
