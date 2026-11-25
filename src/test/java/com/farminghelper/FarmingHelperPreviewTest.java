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
                    new GuideView.Stop(new FarmRunPatch("Ardougne","",PatchType.HERB),"Plant ranarr.",false,false)),patch,"","",false,false,false,GuideView.Stats.UNKNOWN,GuideView.Notes.EMPTY),1L);
            assertNotNull(findButton(panel,"End farming run"));
            assertNull("Active guides must not have a Home/Back exit",findButton(panel,"Back"));
            JButton settings=findButton(panel,"Run settings");assertNotNull(settings);settings.doClick(0);
            JButton back=findButton(panel,"Back");assertNotNull(back);back.doClick(0);
            assertNotNull(findButton(panel,"End farming run"));assertNull(findButton(panel,"Back"));
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

    @Test public void rendersHomeAtSidebarWidth() throws Exception
    {
        BufferedImage image=new BufferedImage(225,500,BufferedImage.TYPE_INT_RGB);
        SwingUtilities.invokeAndWait(()->{
            com.formdev.flatlaf.FlatDarkLaf.setup();
            FarmingHelperPanel panel=new FarmingHelperPanel(new FarmingHelperPlugin());panel.home();
            assertNotNull(findButton(panel,"Settings"));assertNull(findButton(panel,"End farming run"));
            panel.setSize(image.getWidth(),image.getHeight());layout(panel);
            Graphics2D g=image.createGraphics();panel.printAll(g);g.dispose();
        });
        Path output=Paths.get("build/reports/farming-helper-home-preview.png");Files.createDirectories(output.getParent());assertTrue(ImageIO.write(image,"png",output.toFile()));
    }
    @Test public void primaryRunControlsHavePlayIconAndStayProminent()throws Exception
    {
        SwingUtilities.invokeAndWait(()->{
            com.formdev.flatlaf.FlatDarkLaf.setup();
            int[] clicks={0};
            JButton start=FarmingHelperPanel.runButton("Start Run",()->clicks[0]++);
            JButton resume=FarmingHelperPanel.runButton("Continue Run",()->clicks[0]++);
            assertEquals(FarmingTheme.RUN_PRIMARY,start.getBackground());
            assertEquals(FarmingTheme.RUN_PRIMARY_TEXT,resume.getForeground());
            assertNotNull(start.getIcon());assertNotNull(resume.getIcon());
            assertTrue(start.getFont().isBold());
            start.doClick(0);resume.doClick(0);assertEquals(2,clicks[0]);
            start.setEnabled(false);start.doClick(0);assertEquals(2,clicks[0]);
        });
    }
    @Test public void packagedSidebarIconHasExpectedDimensions()
    {
        BufferedImage icon=net.runelite.client.util.ImageUtil.loadImageResource(FarmingHelperPlugin.class,"sidebar.png");
        assertEquals(16,icon.getWidth());assertEquals(16,icon.getHeight());
        assertTrue(icon.getColorModel().hasAlpha());
    }
    private static JButton findButton(Container root,String tooltip)
    {
        for(Component child:root.getComponents())
        {
            if(child instanceof JButton&&tooltip.equals(((JButton)child).getToolTipText()))return (JButton)child;
            if(child instanceof Container){JButton result=findButton((Container)child,tooltip);if(result!=null)return result;}
        }
        return null;
    }
    private void layout(Container c)
    {
        c.doLayout();
        for (Component child : c.getComponents())
            if (child instanceof Container) layout((Container)child);
    }
}
