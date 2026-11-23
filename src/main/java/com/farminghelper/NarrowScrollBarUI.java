package com.farminghelper;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Rectangle;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.plaf.basic.BasicScrollBarUI;

final class NarrowScrollBarUI extends BasicScrollBarUI
{
	static final int WIDTH = 8;
	private static final Color TRACK_COLOR = FarmingHelperTheme.BASE;
	private static final Color THUMB_COLOR = new Color(105, 118, 106);

	@Override
	protected void paintTrack(Graphics graphics, JComponent component, Rectangle bounds)
	{
		graphics.setColor(TRACK_COLOR);
		graphics.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
	}

	@Override
	protected void paintThumb(Graphics graphics, JComponent component, Rectangle bounds)
	{
		if (!component.isEnabled() || bounds.isEmpty())
		{
			return;
		}
		graphics.setColor(THUMB_COLOR);
		graphics.fillRect(bounds.x + 1, bounds.y, Math.max(1, bounds.width - 2), bounds.height);
	}

	@Override
	protected Dimension getMinimumThumbSize()
	{
		return new Dimension(WIDTH - 2, 24);
	}

	@Override
	protected JButton createDecreaseButton(int orientation)
	{
		return createHiddenButton();
	}

	@Override
	protected JButton createIncreaseButton(int orientation)
	{
		return createHiddenButton();
	}

	private static JButton createHiddenButton()
	{
		JButton button = new JButton();
		Dimension size = new Dimension(0, 0);
		button.setPreferredSize(size);
		button.setMinimumSize(size);
		button.setMaximumSize(size);
		return button;
	}
}
