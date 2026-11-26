package com.farminghelper;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.*;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import static org.junit.Assert.*;

public class FarmingHelperPreviewTest
{
    @Test public void rendersGuideAtSidebarWidth() throws Exception
    {
        preview("farming-helper-preview.png",panel->{
            panel.guide(exampleGuide(false),1L);
            assertNotNull(findButton(panel,"End farming run"));
            assertNull(findButton(panel,"Back"));
            JButton settings=findButton(panel,"Run settings");assertNotNull(settings);settings.doClick(0);
            JButton back=findButton(panel,"Back");assertNotNull(back);back.doClick(0);
            assertNotNull(findButton(panel,"End farming run"));assertNull(findButton(panel,"Back"));
        });
    }

    @Test public void rendersPreparationWithItsPinnedStartButton() throws Exception
    {
        preview("farming-helper-preparation.png",panel->{
            panel.guide(exampleGuide(true),1L);
            JButton start=findButton(panel,"Start Run");assertNotNull(start);
            assertNull(SwingUtilities.getAncestorOfClass(JScrollPane.class,start));
        });
    }

    private GuideView exampleGuide(boolean preparing)
    {
        FarmRunPatch patch=new FarmRunPatch("Falador","",PatchType.HERB);
        return new GuideView("Herb Run","Harvest the herbs, then plant Ranarr.",
            List.of(new GuideView.ItemRow(ItemID.RANARR_SEED,"Ranarr seed",0,2,0,12,false),
                new GuideView.ItemRow(ItemID.SPADE,"Spade",1,1,0,0,false),
                new GuideView.ItemRow(ItemID.POH_TABLET_CAMELOTTELEPORT,"Camelot teleport",0,1,4,0,true)),
            List.of(new GuideView.Stop(patch,"Harvest the herbs, then plant Ranarr.",!preparing,false),
                new GuideView.Stop(new FarmRunPatch("Ardougne","",PatchType.HERB),"Plant Ranarr.",false,false)),
            preparing?null:patch,preparing?"Collect your remaining supplies.":"","",false,false,false,
            GuideView.Stats.UNKNOWN,new GuideView.Notes(preparing,"","",""));
    }

    @Test public void rendersHomeAtSidebarWidth() throws Exception
    {
        preview("farming-helper-home-preview.png",panel->{
            panel.home();assertNotNull(findButton(panel,"Settings"));assertNull(findButton(panel,"End farming run"));
        });
    }

    private void preview(String filename,java.util.function.Consumer<FarmingHelperPanel> open) throws Exception
    {
        JPanel root=new JPanel(new java.awt.BorderLayout());
        BufferedImage image=new BufferedImage(225,500,BufferedImage.TYPE_INT_RGB);
        SwingUtilities.invokeAndWait(()->{
            net.runelite.client.ui.laf.RuneLiteLAF.setup();
            FarmingHelperPanel panel=new FarmingHelperPanel(new FarmingHelperPlugin());
            root.add(panel);root.addNotify();root.setSize(image.getWidth(),image.getHeight());open.accept(panel);
        });
        try
        {
            for(int pass=0;pass<4;pass++)SwingUtilities.invokeAndWait(()->{root.validate();layout(root);});
            SwingUtilities.invokeAndWait(()->{
                Graphics2D g=image.createGraphics();
                try { root.printAll(g); } finally { g.dispose(); }
            });
        }
        finally { SwingUtilities.invokeAndWait(root::removeNotify); }
        Path output=Paths.get("build/reports",filename);Files.createDirectories(output.getParent());
        assertTrue(ImageIO.write(image,"png",output.toFile()));
    }

    @Test public void primaryRunControlsHavePlayIconAndStayProminent()throws Exception
    {
        SwingUtilities.invokeAndWait(()->{
            net.runelite.client.ui.laf.RuneLiteLAF.setup();
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
