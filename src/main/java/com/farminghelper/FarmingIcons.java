package com.farminghelper;

import java.awt.*;
import javax.swing.*;

/** Small antialiased line icons, drawn at logical Swing coordinates for HiDPI. */
final class FarmingIcons
{
    enum Kind{BACK,GEAR,END,EXPAND,COLLAPSE,UP,DOWN,MORE,CHECK}
    static Icon of(Kind kind,Color color)
    {
        return new Icon()
        {
            public int getIconWidth(){return 16;}
            public int getIconHeight(){return 16;}
            public void paintIcon(Component c,Graphics g,int x,int y)
            {
                Graphics2D p=(Graphics2D)g.create();p.translate(x,y);
                p.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                p.setColor(c.isEnabled()?color:FarmingTheme.MUTED.darker());
                p.setStroke(new BasicStroke(1.6f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
                switch(kind)
                {
                    case BACK:p.drawLine(12,8,3,8);p.drawLine(3,8,7,4);p.drawLine(3,8,7,12);break;
                    case END:p.drawLine(4,4,12,12);p.drawLine(4,12,12,4);break;
                    case EXPAND:p.drawLine(6,4,10,8);p.drawLine(10,8,6,12);break;
                    case COLLAPSE:case DOWN:p.drawLine(4,6,8,10);p.drawLine(8,10,12,6);break;
                    case UP:p.drawLine(4,10,8,6);p.drawLine(8,6,12,10);break;
                    case CHECK:p.drawLine(3,8,6,11);p.drawLine(6,11,13,4);break;
                    case MORE:for(int i=3;i<=13;i+=5)p.fillOval(i-1,7,3,3);break;
                    case GEAR:
                        p.drawOval(4,4,8,8);p.drawOval(6,6,4,4);
                        for(int i=0;i<8;i++){double a=i*Math.PI/4; p.drawLine((int)Math.round(8+5*Math.cos(a)),(int)Math.round(8+5*Math.sin(a)),(int)Math.round(8+7*Math.cos(a)),(int)Math.round(8+7*Math.sin(a)));}break;
                }
                p.dispose();
            }
        };
    }
    static JButton button(Kind kind,String tooltip,Color color,Runnable action)
    {
        JButton b=new JButton(of(kind,color));b.putClientProperty("farming.icon",Boolean.TRUE);
        b.putClientProperty("FlatLaf.styleClass","iconButton legacyIconButton");
        b.setPreferredSize(new Dimension(28,28));b.setMinimumSize(new Dimension(28,28));
        b.setMargin(new Insets(3,3,3,3));b.setToolTipText(tooltip);b.getAccessibleContext().setAccessibleName(tooltip);
        b.addActionListener(e->action.run());return b;
    }
}
