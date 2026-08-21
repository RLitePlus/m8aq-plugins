package net.runelite.client.plugins.m8aq.preventlogout;

import java.awt.Canvas;
import java.awt.EventQueue;
import java.awt.event.MouseEvent;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

/** Prevents the client-side inactivity logout while enabled. */
@PluginDescriptor(
	name = "Prevent Logout",
	description = "Prevents logout caused by client inactivity"
)
public final class PreventLogoutPlugin extends Plugin
{
	private static final int MAX_IDLE_TICKS = 90_000;
	private static final int RESET_IDLE_TICKS = 75_000;

	@Inject
	private Client client;

	private int previousIdleTimeout;

	@Override
	protected void startUp()
	{
		previousIdleTimeout = client.getIdleTimeout();
		client.setIdleTimeout(MAX_IDLE_TICKS);
	}

	@Override
	protected void shutDown()
	{
		client.setIdleTimeout(previousIdleTimeout);
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (client.getIdleTimeout() != MAX_IDLE_TICKS)
		{
			client.setIdleTimeout(MAX_IDLE_TICKS);
		}

		if (shouldResetIdle(client.getMouseIdleTicks(), client.getKeyboardIdleTicks()))
		{
			dispatchMouseMoved(client.getCanvas(), client.getMouseCanvasPosition());
		}
	}

	static boolean shouldResetIdle(int mouseIdleTicks, int keyboardIdleTicks)
	{
		return Math.min(mouseIdleTicks, keyboardIdleTicks) >= RESET_IDLE_TICKS;
	}

	static void dispatchMouseMoved(Canvas canvas, Point point)
	{
		EventQueue.invokeLater(() -> canvas.dispatchEvent(new MouseEvent(
			canvas,
			MouseEvent.MOUSE_MOVED,
			System.currentTimeMillis(),
			0,
			point.getX(),
			point.getY(),
			0,
			false)));
	}
}
