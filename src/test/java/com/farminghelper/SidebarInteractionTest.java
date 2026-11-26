package com.farminghelper;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class SidebarInteractionTest
{
    private static final FarmRunPatch CATHERBY=new FarmRunPatch("Catherby","",PatchType.HERB);
    private static final FarmRunPatch WEISS=new FarmRunPatch("Weiss","",PatchType.HERB);

    static class Character extends FarmingHelperPlugin
    {
        final Map<String,String> prefs=new HashMap<>();
        String locked="";
        @Override boolean canSave(){return true;}
        @Override boolean enabled(FarmRunPatch patch){return flag("patchEnabled_"+patch.key(),true);}
        @Override List<FarmRunPatch> patches(){return List.of(CATHERBY,WEISS);}
        @Override String setting(String key,String fallback){return prefs.getOrDefault(key,fallback);}
        @Override void save(String key,String value){prefs.put(key,value);}
        @Override String accessNote(FarmRunPatch patch){return patch==WEISS?locked:"";}
    }
    private static List<JComponent> descendants(Container root)
    {
        List<JComponent> result=new ArrayList<>();
        for(Component child:root.getComponents())if(child instanceof JComponent)
        {
            result.add((JComponent)child);
            result.addAll(descendants((JComponent)child));
        }
        return result;
    }
    static JButton button(Container root,String name)
    {
        return descendants(root).stream().filter(c->c instanceof JButton && name.equals(c.getAccessibleContext().getAccessibleName()))
            .map(c->(JButton)c).findFirst().orElseThrow(()->new AssertionError("No button "+name));
    }
    static <T extends JComponent> T named(Container root,String name,Class<T> type)
    {
        return descendants(root).stream().filter(c->type.isInstance(c)&&name.equals(c.getName()))
            .map(type::cast).findFirst().orElseThrow(()->new AssertionError("No control "+name));
    }
    private static JTextArea line(Container root,String text)
    {
        return descendants(root).stream().filter(c->c instanceof JTextArea && text.equals(((JTextArea)c).getText()))
            .map(c->(JTextArea)c).findFirst().orElse(null);
    }
    private static GuideView guide(boolean preparing,int carried,int vault)
    {
        return new GuideView("Herb Run","Harvest",List.of(new GuideView.ItemRow(1,"Ranarr seed",carried,3,0,vault,false),
            new GuideView.ItemRow(2,"Camelot teleport",0,1,0,0,true)),
            List.of(new GuideView.Stop(CATHERBY,"Harvest",!preparing,false)),preparing?null:CATHERBY,
            preparing?"Collect supplies.":"","",false,false,false,GuideView.Stats.UNKNOWN,new GuideView.Notes(preparing,"","",""));
    }
    @Test public void supplyHeadersPrecedeStepsAndFoldOnceWhenLeavingPreparation() throws Exception
    {
        SwingUtilities.invokeAndWait(()->{
            FarmingHelperPanel panel=new FarmingHelperPanel(new Character());
            panel.guide(guide(true,1,12),2);
            JButton items=named(panel,"Recommended items",JButton.class);
            JButton travel=named(panel,"Recommended teleports",JButton.class);
            assertSame(items.getParent(),button(panel,"Help for Recommended items").getParent());
            assertSame(travel.getParent(),button(panel,"Help for Recommended teleports").getParent());
            List<JComponent> preparation=descendants(panel);
            assertTrue(preparation.indexOf(travel)<preparation.indexOf(line(panel,"Collect supplies.")));
            assertNull(SwingUtilities.getAncestorOfClass(JScrollPane.class,button(panel,"Start Run")));
            JTextArea seed=line(panel,"Ranarr seed");assertNotNull(seed);
            assertNotNull(line(panel,"Last seen · Seed Vault 12"));
            panel.guide(guide(true,2,0),2);
            assertSame(seed,line(panel,"Ranarr seed"));assertNull(line(panel,"Last seen · Seed Vault 12"));
            travel.doClick(0);assertNotNull(line(panel,"Camelot teleport"));
            panel.guide(guide(false,2,0),2);
            List<JComponent> components=descendants(panel);
            assertTrue(components.indexOf(named(panel,"Recommended items",JButton.class))<components.indexOf(named(panel,"Recommended teleports",JButton.class)));
            assertTrue(components.indexOf(named(panel,"Recommended teleports",JButton.class))<components.indexOf(button(panel,"Skip patch")));
            assertNull(line(panel,"Ranarr seed"));assertNull(line(panel,"Camelot teleport"));
            named(panel,"Recommended teleports",JButton.class).doClick(0);
            panel.guide(guide(false,3,0),2);
            assertNotNull(line(panel,"Camelot teleport"));
            assertFalse(named(panel,"Recommended items",JButton.class).getText().contains("missing"));
        });
    }
    @Test public void anEmptyChecklistKeepsBothDisclosuresAboveTheActivePatch() throws Exception
    {
        SwingUtilities.invokeAndWait(()->{
            FarmingHelperPanel panel=new FarmingHelperPanel(new Character());
            GuideView view=new GuideView("Herb Run","Harvest",List.of(),guide(false,0,0).stops,
                CATHERBY,"","",false,false,false,GuideView.Stats.UNKNOWN,GuideView.Notes.EMPTY);
            panel.guide(view,4);
            JButton items=named(panel,"Recommended items",JButton.class);
            JButton travel=named(panel,"Recommended teleports",JButton.class);
            assertFalse(items.getText().contains("missing"));
            items.doClick(0);travel.doClick(0);
            assertNotNull(line(panel,"No items needed for this run."));
            assertNotNull(line(panel,"No teleports recommended for the remaining patches."));
            panel.setSize(200,400);
            layout(panel);
            for(JButton fold:List.of(items,travel))
            {
                JPanel header=(JPanel)fold.getParent();
                JButton help=button(header,"Help for "+fold.getName());
                assertTrue(help.isFocusable());
                assertTrue(fold.getBounds().contains(help.getBounds()));
                assertSame(help,SwingUtilities.getDeepestComponentAt(header,help.getX()+help.getWidth()/2,help.getY()+help.getHeight()/2));
                assertSame(fold,SwingUtilities.getDeepestComponentAt(header,5,fold.getHeight()/2));
            }
        });
    }
    private static void layout(Container component)
    {
        component.doLayout();
        for(Component child:component.getComponents())if(child instanceof Container)layout((Container)child);
    }
    @Test public void individualSearchReusesTheSameRunButtonsAndSprites() throws Exception
    {
        Character character=new Character();
        FarmingHelperPanel[] panel=new FarmingHelperPanel[1];JTextField[] field=new JTextField[1];
        JButton[] tree=new JButton[1];
        SwingUtilities.invokeAndWait(()->{
            panel[0]=new FarmingHelperPanel(character);panel[0].home();
            button(panel[0],"Individual runs").doClick(0);
            field[0]=named(panel[0],"Find a run…",JTextField.class);
            tree[0]=button(panel[0],"Fruit Tree & Calquat Run");field[0].setText("herb");
        });
        SwingUtilities.invokeAndWait(()->{
            assertFalse(tree[0].getParent().isVisible());
            assertTrue(button(panel[0],"Herb Run").getParent().isVisible());
            field[0].setText("nothing matches");
        });
        SwingUtilities.invokeAndWait(()->{
            assertTrue(line(panel[0],"No runs match your search.").getParent().isVisible());
            field[0].setText(" TREE ");
        });
        SwingUtilities.invokeAndWait(()->{
            assertSame(tree[0],button(panel[0],"Fruit Tree & Calquat Run"));
            assertTrue(tree[0].getParent().isVisible());
            assertFalse(line(panel[0],"No runs match your search.").getParent().isVisible());
        });
    }
    @Test public void changingManualSelectionKeepsThePickerAndCorrectMixedState() throws Exception
    {
        SwingUtilities.invokeAndWait(()->{
            Character character=new Character();
            FarmingHelperPanel panel=new FarmingHelperPanel(character);panel.home();button(panel,"Manual Run").doClick(0);
            JButton all=button(panel,"Select all"),go=button(panel,"Start Manual Run");
            all.doClick(0);assertSame(all,button(panel,"Select all"));assertTrue(go.isEnabled());
            button(panel,"Expand patches · Herb").doClick(0);
            JCheckBox child=named(panel,"manual/"+CATHERBY.key(),JCheckBox.class);
            child.doClick(0);assertSame(child,named(panel,"manual/"+CATHERBY.key(),JCheckBox.class));
            JCheckBox parent=named(panel,"manual-group/Herb",JCheckBox.class);
            assertEquals("indeterminate",parent.getClientProperty("JButton.selectedState"));
            assertNotNull(button(panel,"Collapse patches · Herb"));
            character.locked="Quest required";panel.accessChanged();
            assertFalse(go.isEnabled());assertSame(all,button(panel,"Select all"));
            assertFalse(ManualSelection.decode(character.prefs.get("manualPatchChoicesV1"),character.patches()).contains(CATHERBY.key()));
        });
    }
    @Test public void patchFilteringAndAccessRefreshRetainTheSearchField() throws Exception
    {
        Character character=new Character();
        FarmingHelperPanel[] panel=new FarmingHelperPanel[1];JTextField[] field=new JTextField[1];
        SwingUtilities.invokeAndWait(()->{
            panel[0]=new FarmingHelperPanel(character);panel[0].home();button(panel[0],"Settings").doClick(0);
            button(panel[0],"Patches").doClick(0);
            field[0]=named(panel[0],"Search patches…",JTextField.class);
            field[0].setText("Catherby");field[0].setCaretPosition(3);
        });
        SwingUtilities.invokeAndWait(()->{
            assertNotNull(line(panel[0],"Catherby · 1/1"));
            assertFalse(descendants(panel[0]).stream().anyMatch(c->"patch-group/Catherby".equals(c.getName())));
            panel[0].accessChanged();
            assertSame(field[0],named(panel[0],"Search patches…",JTextField.class));assertEquals(3,field[0].getCaretPosition());
            field[0].setText("no such patch");
        });
        SwingUtilities.invokeAndWait(()->assertNotNull(line(panel[0],"No patches match your filters.")));
    }
}
