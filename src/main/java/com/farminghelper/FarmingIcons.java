package com.farminghelper;

import java.awt.*;
import javax.swing.*;

/** Small antialiased line icons, drawn at logical Swing coordinates for HiDPI. */
final class FarmingIcons
{
    enum Kind{PLAY,BACK,GEAR,END,EXPAND,COLLAPSE,UP,DOWN,MORE,CHECK,LEAF,LAYERS,PIN,LIST,STORAGE,TOOLS,SLIDERS,INFO}
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
                    case PLAY:p.fillPolygon(new int[]{5,13,5},new int[]{3,8,13},3);break;
                    case BACK:p.drawLine(12,8,3,8);p.drawLine(3,8,7,4);p.drawLine(3,8,7,12);break;
                    case END:p.drawLine(4,4,12,12);p.drawLine(4,12,12,4);break;
                    case EXPAND:p.drawLine(6,4,10,8);p.drawLine(10,8,6,12);break;
                    case COLLAPSE:case DOWN:p.drawLine(4,6,8,10);p.drawLine(8,10,12,6);break;
                    case UP:p.drawLine(4,10,8,6);p.drawLine(8,6,12,10);break;
                    case CHECK:p.drawLine(3,8,6,11);p.drawLine(6,11,13,4);break;
                    case MORE:for(int i=3;i<=13;i+=5)p.fillOval(i-1,7,3,3);break;
                    case LEAF:p.drawArc(3,2,10,10,0,235);p.drawLine(3,13,11,5);p.drawLine(6,9,6,5);break;
                    case LAYERS:p.drawPolygon(new int[]{2,8,14,8},new int[]{5,2,5,8},4);p.drawPolyline(new int[]{2,8,14},new int[]{8,11,8},3);p.drawPolyline(new int[]{2,8,14},new int[]{11,14,11},3);break;
                    case PIN:p.drawOval(4,2,8,8);p.drawOval(7,5,2,2);p.drawLine(4,8,8,14);p.drawLine(12,8,8,14);break;
                    case LIST:for(int i=4;i<=12;i+=4){p.drawLine(2,i,3,i);p.drawLine(6,i,13,i);}break;
                    case STORAGE:p.drawRoundRect(2,4,12,10,2,2);p.drawLine(2,8,14,8);p.drawLine(7,8,9,8);break;
                    case TOOLS:p.drawLine(3,13,10,6);p.drawLine(4,14,11,7);p.drawArc(8,1,6,6,40,235);break;
                    case SLIDERS:for(int i=4;i<=12;i+=4)p.drawLine(2,i,14,i);p.fillRect(5,2,2,4);p.fillRect(10,6,2,4);p.fillRect(7,10,2,4);break;
                    case INFO:p.drawOval(2,2,12,12);p.fillOval(7,4,2,2);p.drawLine(8,8,8,11);break;
                    case GEAR:
                        java.awt.geom.Path2D path=new java.awt.geom.Path2D.Double();
                        for(int i=0;i<32;i++){double a=(i/4*4+(i%4))*Math.PI/16;double radius=i%4==0||i%4==3?5.4:7.1;double xx=8+radius*Math.cos(a), yy=8+radius*Math.sin(a);if(i==0)path.moveTo(xx,yy);else path.lineTo(xx,yy);}
                        path.closePath();p.draw(path);p.drawOval(5,5,6,6);break;
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
        if(action!=null)b.addActionListener(e->action.run());return b;
    }
}
