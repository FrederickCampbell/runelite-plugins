package com.farminghelper;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class FarmingHelperPreviewTest
{
    @Test public void rendersGuideAtSidebarWidth() throws Exception
    {
        BufferedImage image = new BufferedImage(225,500,BufferedImage.TYPE_INT_RGB);
        SwingUtilities.invokeAndWait(() -> {
            com.formdev.flatlaf.FlatDarkLaf.setup();
            FarmingHelperPanel panel = new FarmingHelperPanel(new FarmingHelperPlugin());
            FarmRunPatch patch = new FarmRunPatch("Falador","",PatchType.HERB);
            panel.guide(new GuideView("Herb Run","Harvest the herbs, then plant Ranarr.",
                List.of(new GuideView.ItemRow(1,"Ranarr seed",0,6,12,2,false),
                    new GuideView.ItemRow(2,"Ultracompost",4,6,10,0,true)),
                List.of(new GuideView.Stop(patch,"Harvest the herbs, then plant Ranarr.",true,false),
                    new GuideView.Stop(new FarmRunPatch("Ardougne","",PatchType.HERB),"Plant ranarr.",false,false)),patch),1L);
            panel.setSize(image.getWidth(),image.getHeight());
            layout(panel);
            Graphics2D graphics = image.createGraphics();
            panel.printAll(graphics);
            graphics.dispose();
        });
        Path output = Paths.get("build/reports/farming-helper-preview.png");
        Files.createDirectories(output.getParent());
        assertTrue(ImageIO.write(image,"png",output.toFile()));
    }

    private void layout(Container c)
    {
        c.doLayout();
        for (Component child : c.getComponents())
            if (child instanceof Container) layout((Container)child);
    }
}
