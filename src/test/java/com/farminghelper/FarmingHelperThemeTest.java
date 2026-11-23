package com.farminghelper;
import javax.swing.*;
import java.awt.Color;
import org.junit.Test;
import static org.junit.Assert.*;
public class FarmingHelperThemeTest
{
    @Test public void themeIsLocalAndRepeatable() throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            JButton button = new JButton("Start");
            JComboBox<String> choices = new JComboBox<>(new String[]{"Ultracompost","None"});
            JCheckBox check = new JCheckBox("Auto-next", true);
            JSpinner spinner = new JSpinner(new SpinnerNumberModel(28,1,28,1));
            JPanel panel = new JPanel();
            panel.add(button); panel.add(choices); panel.add(check); panel.add(spinner);
            Color outside = new JButton("Outside").getBackground();
            FarmingHelperTheme.style(panel);
            FarmingHelperTheme.style(panel);
            assertEquals(FarmingHelperTheme.SURFACE, button.getBackground());
            assertEquals(FarmingHelperTheme.TEXT, choices.getForeground());
            assertEquals("Ultracompost", choices.getSelectedItem());
            assertTrue(check.isSelected());
            assertEquals(28, spinner.getValue());
            assertEquals(outside, new JButton("Outside").getBackground());
            check.doClick();
            assertFalse(check.isSelected());
        });
    }
}
