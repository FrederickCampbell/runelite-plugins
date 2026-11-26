package com.farminghelper;

import javax.swing.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static com.farminghelper.SidebarInteractionTest.button;
import static com.farminghelper.SidebarInteractionTest.named;

/** Exercise sidebar component actions and selection without acquiring desktop focus. */
public class SidebarKeyboardTest
{
    @Test public void rankingActionsAndSearchSelectionSurviveRefresh() throws Exception
    {
        FarmingHelperPanel[] panel=new FarmingHelperPanel[1];
        JTextField[] search=new JTextField[1];
        SwingUtilities.invokeAndWait(()->{
            SidebarInteractionTest.Character character=new SidebarInteractionTest.Character();
            character.prefs.put("cropPriority_HERB","Ranarr, Snapdragon");
            character.prefs.put("cropCustom_HERB","true");
            panel[0]=new FarmingHelperPanel(character);
            panel[0].home();button(panel[0],"Settings").doClick(0);
            button(panel[0],"Crop priorities").doClick(0);button(panel[0],"Herb").doClick(0);
            JButton down=button(panel[0],"Move Ranarr down");
            assertTrue(down.isEnabled());assertTrue(down.isFocusable());
            down.doClick(0);
            assertEquals("Snapdragon, Ranarr",character.prefs.get("cropPriority_HERB"));
            assertTrue(button(panel[0],"Move Ranarr up").isEnabled());
            button(panel[0],"Remove Ranarr").doClick(0);
            assertEquals("Snapdragon",character.prefs.get("cropPriority_HERB"));
            assertTrue(button(panel[0],"Remove Snapdragon").isFocusable());
            button(panel[0],"Back").doClick(0);button(panel[0],"Patches").doClick(0);
            search[0]=named(panel[0],"Search patches…",JTextField.class);
            search[0].setText("Catherby");search[0].select(2,5);
        });
        SwingUtilities.invokeAndWait(()->panel[0].accessChanged());
        SwingUtilities.invokeAndWait(()->{
            JTextField field=named(panel[0],"Search patches…",JTextField.class);
            assertSame(search[0],field);
            assertEquals("Catherby",field.getText());
            assertEquals(2,field.getSelectionStart());assertEquals(5,field.getSelectionEnd());
        });
    }
}
