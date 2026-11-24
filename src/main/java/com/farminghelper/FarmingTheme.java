package com.farminghelper;

import java.awt.*;
import java.util.Arrays;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.FontManager;

/** Local design tokens. Never modifies UIManager defaults or installs a look and feel. */
final class FarmingTheme
{
    static final Color BACKGROUND=new Color(0x202020), CARD=new Color(0x292929), INPUT=new Color(0x353535),
        HOVER=new Color(0x414141), BORDER=new Color(0x484848), TEXT=new Color(0xE8E8E8),
        MUTED=new Color(0xA6A6A6), ACCENT=new Color(0xF0A04B), SUCCESS=new Color(0x9AC784),
        ACTIVE=new Color(0x303E2C), WARNING=new Color(0xE9C46A), DANGER=new Color(0xE57373);
    private static final boolean VERDANA=Arrays.stream(GraphicsEnvironment.getLocalGraphicsEnvironment()
        .getAvailableFontFamilyNames()).anyMatch("Verdana"::equalsIgnoreCase);
    private FarmingTheme(){}
    static Font font(int style,int size)
    {
        Font base=VERDANA?new Font("Verdana",Font.PLAIN,size):FontManager.getDefaultFont();
        return base.deriveFont(style,(float)size);
    }
    // FlatLaf's public per-component style property; native defaults still work
    // if the host changes its look and feel. No global style class registration.
    static void style(JComponent c,String css){c.putClientProperty("FlatLaf.style",css);}
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
                        style(b,"icon.selectedBackground: #9AC784; icon.selectedBorderColor: #9AC784; icon.checkmarkColor: #202020");
                    }
                    else
                    {
                        if(b.getClientProperty("farming.icon")==null)
                        {if(b.getClientProperty("farming.preserveBackground")==null)b.setBackground(INPUT);style(b,"arc: 8; borderWidth: 1; focusWidth: 1; hoverBackground: #414141; pressedBackground: #303E2C");}
                        b.setMargin(new Insets(5,7,5,7));
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
            }
        }
        // Do not style look-and-feel-owned children of composite controls (combo arrows, etc.).
        if(c instanceof JPanel)for(Component child:((Container)c).getComponents())apply(child);
    }
}
