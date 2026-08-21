package net.runelite.client.plugins.m8aq.preventlogout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.awt.Canvas;
import java.awt.EventQueue;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.Point;
import org.junit.Test;

public class PreventLogoutPluginTest
{
	@Test
	public void resetsOnlyWhenBothInputsReachThreshold()
	{
		assertFalse(PreventLogoutPlugin.shouldResetIdle(74_999, 75_000));
		assertFalse(PreventLogoutPlugin.shouldResetIdle(75_000, 74_999));
		assertTrue(PreventLogoutPlugin.shouldResetIdle(75_000, 75_000));
	}

	@Test
	public void dispatchesSamePositionMouseMovement() throws Exception
	{
		Canvas canvas = new Canvas();
		AtomicReference<MouseEvent> received = new AtomicReference<>();
		canvas.addMouseMotionListener(new MouseMotionAdapter()
		{
			@Override
			public void mouseMoved(MouseEvent event)
			{
				received.set(event);
			}
		});

		PreventLogoutPlugin.dispatchMouseMoved(canvas, new Point(-1, -1));
		EventQueue.invokeAndWait(() -> { });

		assertNotNull(received.get());
		assertEquals(-1, received.get().getX());
		assertEquals(-1, received.get().getY());
	}
}
