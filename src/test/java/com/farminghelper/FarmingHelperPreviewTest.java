package com.farminghelper;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class FarmingHelperPreviewTest
{
    @Test public void previewAtNativeWidth() throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                JPanel root=FarmingHelperComponents.column(6);
                root.add(FarmingHelperComponents.header("Patch Settings",new JButton("‹"),new JButton("≡")));
                root.add(FarmingHelperComponents.caption("81 / 99 enabled"));
                root.add(new FarmingHelperComponents.SearchField());
                root.add(FarmingHelperComponents.row("Herbs · 10/10"));
                root.add(new JComboBox<>(new String[]{"Automatic","Custom priorities"}));
                root.add(new JCheckBox("Catherby",true));
                root.add(new JCheckBox("Harmony Island",false));
                root.add(FarmingHelperComponents.row("Advanced"));
                root.add(FarmingHelperComponents.row("Fruit trees"));
                FarmingHelperTheme.style(root);
                root.setSize(225,420);
                layout(root);
                BufferedImage image=new BufferedImage(225,420,BufferedImage.TYPE_INT_ARGB);
                Graphics2D g=image.createGraphics();
                root.printAll(g); g.dispose();
                File out=new File("build/reports/farming-helper-preview.png");
                out.getParentFile().mkdirs(); ImageIO.write(image,"png",out);
                assertTrue(out.isFile());
                assertEquals(Font.DIALOG, root.getComponent(2).getFont().getFamily());
                assertEquals(225,root.getWidth());
            }
            catch (Exception ex) { throw new AssertionError(ex); }
        });
    }
    private static void layout(Container parent)
    {
        parent.doLayout();
        for (Component child: parent.getComponents()) if(child instanceof Container) layout((Container)child);
    }
}
