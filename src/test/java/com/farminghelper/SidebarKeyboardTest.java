package com.farminghelper;

import java.awt.GraphicsEnvironment;
import java.awt.KeyboardFocusManager;
import java.beans.PropertyChangeListener;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.swing.*;
import org.junit.Assume;
import org.junit.Test;
import static org.junit.Assert.*;
import static com.farminghelper.SidebarInteractionTest.button;
import static com.farminghelper.SidebarInteractionTest.named;

public class SidebarKeyboardTest
{
    private JFrame frame;
    private FarmingHelperPanel panel;

    private void onEdt(Runnable action) throws Exception
    {
        SwingUtilities.invokeAndWait(action);
    }

    private void awaitFocus(Supplier<JComponent> lookup,boolean request) throws Exception
    {
        KeyboardFocusManager manager=KeyboardFocusManager.getCurrentKeyboardFocusManager();
        CompletableFuture<Void> focused=new CompletableFuture<>();
        PropertyChangeListener[] listener=new PropertyChangeListener[1];
        onEdt(()->{
            JComponent expected=lookup.get();
            listener[0]=event->{if(event.getNewValue()==expected)focused.complete(null);};
            manager.addPropertyChangeListener("focusOwner",listener[0]);
            if(manager.getFocusOwner()==expected)focused.complete(null);
            else if(request)expected.requestFocusInWindow();
        });
        try { focused.get(8,TimeUnit.SECONDS); }
        finally { onEdt(()->manager.removePropertyChangeListener("focusOwner",listener[0])); }
    }

    @Test public void rankingFocusFollowsTheCropAndSearchRetainsItsSelection() throws Exception
    {
        // This test needs exclusive desktop focus, not merely a non-headless JVM.
        // CI supplies an isolated Xvfb display and explicitly enables this test.
        // Ordinary local builds (including the Dev Manager installer) must not
        // depend on Windows foreground-window arbitration or steal user focus.
        Assume.assumeTrue("Requires an explicitly enabled isolated/focus-capable display",
            "true".equalsIgnoreCase(System.getenv("FARMING_HELPER_DISPLAY_TEST")));
        assertFalse("Display test was enabled but the JVM is headless",GraphicsEnvironment.isHeadless());
        try
        {
            onEdt(()->{
                net.runelite.client.ui.laf.RuneLiteLAF.setup();
                SidebarInteractionTest.Character character=new SidebarInteractionTest.Character();
                character.prefs.put("cropPriority_HERB","Ranarr, Snapdragon");
                character.prefs.put("cropCustom_HERB","true");
                panel=new FarmingHelperPanel(character);
                panel.home();button(panel,"Settings").doClick(0);button(panel,"Crop priorities").doClick(0);
                button(panel,"Herb").doClick(0);
                frame=new JFrame("Farming Helper sidebar test");
                frame.setContentPane(panel);frame.setSize(225,400);frame.setVisible(true);
            });
            awaitFocus(()->button(panel,"Move Ranarr down"),true);
            onEdt(()->button(panel,"Move Ranarr down").doClick(0));
            awaitFocus(()->button(panel,"Move Ranarr up"),false);
            awaitFocus(()->button(panel,"Remove Ranarr"),true);
            onEdt(()->button(panel,"Remove Ranarr").doClick(0));
            awaitFocus(()->button(panel,"Remove Snapdragon"),false);
            onEdt(()->{
                button(panel,"Back").doClick(0);button(panel,"Patches").doClick(0);
                named(panel,"Search patches…",JTextField.class).setText("Catherby");
            });
            awaitFocus(()->named(panel,"Search patches…",JTextField.class),true);
            onEdt(()->{
                JTextField field=named(panel,"Search patches…",JTextField.class);
                field.select(2,5);panel.accessChanged();
            });
            onEdt(()->{
                JTextField field=named(panel,"Search patches…",JTextField.class);
                assertSame(field,KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner());
                assertEquals(2,field.getSelectionStart());assertEquals(5,field.getSelectionEnd());
            });
        }
        finally { onEdt(()->{if(frame!=null)frame.dispose();}); }
    }
}
