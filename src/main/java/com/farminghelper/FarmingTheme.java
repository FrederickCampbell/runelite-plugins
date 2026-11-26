package com.farminghelper;

import java.awt.*;
import java.util.Arrays;
import javax.swing.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.View;
import net.runelite.client.ui.FontManager;

/** Local design tokens. Never modifies UIManager defaults or installs a look and feel. */
final class FarmingTheme
{
    static final Color BACKGROUND=new Color(0x202020), CARD=new Color(0x292929), INPUT=new Color(0x353535),
        HOVER=new Color(0x414141), BORDER=new Color(0x484848), TEXT=new Color(0xE8E8E8),
        MUTED=new Color(0xA6A6A6), ACCENT=new Color(0xF0A04B), SUCCESS=new Color(0x9AC784),
        RUN_PRIMARY=new Color(0x82D66A), RUN_PRIMARY_TEXT=new Color(0x182416), RUN_CARD=new Color(0x293328), NAV=new Color(0x252525), ACTIVE=new Color(0x303E2C), WARNING=new Color(0xE9C46A), DANGER=new Color(0xE57373);
    private static final boolean VERDANA=Arrays.stream(GraphicsEnvironment.getLocalGraphicsEnvironment()
        .getAvailableFontFamilyNames()).anyMatch("Verdana"::equalsIgnoreCase);
    private FarmingTheme(){}
    static String escape(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");}
    static Font font(int style,int size)
    {
        Font base=VERDANA?new Font("Verdana",Font.PLAIN,size):FontManager.getDefaultFont();
        return base.deriveFont(style,(float)size);
    }
    // FlatLaf's public per-component style property; native defaults still work
    // if the host changes its look and feel. No global style class registration.
    static void style(JComponent c,String css){c.putClientProperty("FlatLaf.style",css);}
    // BasicHTML measures Swing text in logical pixels. Fit to the component that the host
    // actually laid out, including its focus border, checkbox indicator, sprite and trailing margin.
    private static void fitText(JComponent c)
    {
        if(c.getWidth()<=0)return;
        AbstractButton button=c instanceof AbstractButton?(AbstractButton)c:null;
        String text=button!=null?button.getText():((JLabel)c).getText();
        if(text==null||text.isEmpty())return;
        Insets in=c.getInsets();
        int available=c.getWidth()-in.left-in.right;
        Icon icon=button!=null?button.getIcon():((JLabel)c).getIcon();
        if(icon==null && c instanceof JCheckBox)icon=UIManager.getIcon("CheckBox.icon");
        if(icon!=null)available-=icon.getIconWidth()+(button!=null?button.getIconTextGap():((JLabel)c).getIconTextGap());
        if(available<=0)return;
        View view=(View)c.getClientProperty(BasicHTML.propertyKey);
        float previousHeight=view==null?0:view.getPreferredSpan(View.Y_AXIS);
        if(!BasicHTML.isHTMLString(text))
        {
            if(c.getFontMetrics(c.getFont()).stringWidth(text)<=available)
            {
                if(view!=null){c.putClientProperty(BasicHTML.propertyKey,null);textHeightChanged(c);}
                return;
            }
            if(view==null)
            {
                view=BasicHTML.createHTMLView(c,"<html>"+escape(text)+"</html>");
                c.putClientProperty(BasicHTML.propertyKey,view);
            }
        }
        if(view!=null && (int)view.getPreferredSpan(View.X_AXIS)!=available)
        {
            view.setSize(available,0);
            if(previousHeight!=view.getPreferredSpan(View.Y_AXIS))textHeightChanged(c);
            else c.repaint();
        }
    }

    private static void textHeightChanged(JComponent control)
    {
        // BoxLayout caches each child's size. A wrapped label can grow while its row is
        // already invalid, so revalidate() on the label alone can leave the old row height.
        for(Container parent=control.getParent();parent instanceof JPanel;parent=parent.getParent())
            parent.invalidate();
        control.revalidate();control.repaint();
    }

    private static final ComponentAdapter RESIZE_TEXT = new ComponentAdapter()
    {
        @Override public void componentResized(ComponentEvent event){fitText((JComponent)event.getComponent());}
    };
    private static final java.beans.PropertyChangeListener UPDATE_TEXT = event -> {
        String property=event.getPropertyName();
        if ("text".equals(property)||"icon".equals(property)||"font".equals(property)) fitText((JComponent)event.getSource());
    };
    static void apply(Component c)
    {
        if(c instanceof JComponent)
        {
            JComponent j=(JComponent)c;
            if(j.getClientProperty("farming.styled")==null)
            {
                j.putClientProperty("farming.styled",Boolean.TRUE);
                if(j instanceof AbstractButton)
                {
                    AbstractButton b=(AbstractButton)j;
                    Font old=b.getFont();b.setFont(font(old!=null&&old.isBold()?Font.BOLD:Font.PLAIN,12));
                    if(b instanceof JCheckBox)
                    {
                        b.setOpaque(false);
                        style(b,"icon.background: #303030; icon.borderColor: #777777; icon.selectedBackground: #9AC784; icon.selectedBorderColor: #9AC784; icon.checkmarkColor: #202020");
                    }
                    else
                    {
                        if(b.getClientProperty("farming.primaryRun")!=null)
                        {
                            b.setBackground(RUN_PRIMARY);b.setForeground(RUN_PRIMARY_TEXT);
                            b.setFont(font(Font.BOLD,13));b.setMargin(new Insets(10,10,10,10));
                            style(b,"arc: 8; borderWidth: 0; focusWidth: 1; hoverBackground: #9BE482; pressedBackground: #65BA51; disabledBackground: #3B4937; disabledText: #A6A6A6");
                        }
                        else if(b.getClientProperty("farming.navigation")!=null)
                        {style(b,"arc: 8; borderWidth: 0; focusWidth: 1; hoverBackground: #3B4637; pressedBackground: #303E2C");b.setMargin(new Insets(5,6,5,b.getClientProperty("farming.marker")!=null?26:6));}
                        else if(b.getClientProperty("farming.icon")==null)
                        {if(b.getClientProperty("farming.preserveBackground")==null)b.setBackground(INPUT);style(b,"arc: 8; borderWidth: 1; focusWidth: 1; hoverBackground: #414141; pressedBackground: #303E2C");b.setMargin(new Insets(5,7,5,b.getClientProperty("farming.inlineHelp")!=null?35:7));}
                    }
                    if(b.getForeground()==null||b.getForeground() instanceof javax.swing.plaf.UIResource)b.setForeground(TEXT);
                }
                else if(j instanceof JComboBox)
                {
                    j.setFont(font(Font.PLAIN,12));j.setBackground(INPUT);j.setForeground(TEXT);
                    style(j,"arc: 8; focusWidth: 1; borderWidth: 1");
                }
                else if(j instanceof JTextField)
                {
                    j.setFont(font(Font.PLAIN,12));j.setBackground(INPUT);j.setForeground(TEXT);
                    style(j,"arc: 8; focusWidth: 1");
                }
                else if(j instanceof JLabel||j instanceof JTextArea)
                {
                    Font old=j.getFont();
                    if(old==null||old instanceof javax.swing.plaf.UIResource)
                        j.setFont(font(old!=null&&old.isBold()?Font.BOLD:Font.PLAIN,12));
                }
                if(j instanceof AbstractButton && j.getClientProperty("farming.icon")==null ||
                    j instanceof JLabel && ((JLabel)j).getText()!=null && !((JLabel)j).getText().isEmpty())
                {
                    j.addComponentListener(RESIZE_TEXT);
                    j.addPropertyChangeListener(UPDATE_TEXT);
                }
            }
        }
        // Do not style look-and-feel-owned children of composite controls (combo arrows, etc.).
        if(c instanceof JPanel)for(Component child:((Container)c).getComponents())apply(child);
    }
}
