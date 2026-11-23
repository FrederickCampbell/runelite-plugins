package com.farmingpatchadvisor;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Rectangle;
import javax.swing.JPanel;
import javax.swing.Scrollable;

/** RuneLite's narrow viewport must drive width, not a long button or label.
 * Keep the content at its natural height even in tall windows. */
final class DirectorSidebarViewport extends JPanel implements Scrollable
{
    DirectorSidebarViewport(JPanel content)
    {
        super(new BorderLayout());
        setBackground(content.getBackground());
        add(content, BorderLayout.NORTH);
    }

    @Override public Dimension getPreferredScrollableViewportSize()
    {
        return getPreferredSize();
    }

    @Override public int getScrollableUnitIncrement(Rectangle r, int orientation, int direction)
    {
        return 16;
    }

    @Override public int getScrollableBlockIncrement(Rectangle r, int orientation, int direction)
    {
        return Math.max(16, r.height - 16);
    }

    @Override public boolean getScrollableTracksViewportWidth() { return true; }
    @Override public boolean getScrollableTracksViewportHeight() { return false; }
}
