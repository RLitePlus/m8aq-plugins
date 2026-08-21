package net.runelite.client.plugins.m8aq.api.player;

import java.lang.reflect.Proxy;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

/** Minimal runnable checks for {@link Location}. */
public final class LocationSelfTest
{
	private LocationSelfTest()
	{
	}

	/** Runs the checks with Java assertions enabled. */
	public static void main(String[] args)
	{
		WorldPoint worldPoint = new WorldPoint(3200, 3200, 2);
		LocalPoint localPoint = new LocalPoint(1234, 5678, 7);
		WorldView worldView = proxy(WorldView.class, (method, arguments) ->
		{
			switch (method)
			{
				case "getId":
					return 7;
				case "getPlane":
					return 2;
				case "isInstance":
					return true;
				default:
					return null;
			}
		});
		Player player = proxy(Player.class, (method, arguments) ->
		{
			switch (method)
			{
				case "getWorldLocation":
					return worldPoint;
				case "getLocalLocation":
					return localPoint;
				case "getWorldView":
					return worldView;
				default:
					return null;
			}
		});

		Location.State state = Location.getState(client(player));
		assert state.isAvailable();
		assert state.getWorldPoint().equals(worldPoint);
		assert state.getLocalPoint().equals(localPoint);
		assert state.getPlane() == 2;
		assert state.getRegionId() == worldPoint.getRegionID();
		assert state.isInstanced();
		assert state.getWorldViewId() == 7;

		Location.State unavailable = Location.getState(client(null));
		assert !unavailable.isAvailable();
		assert unavailable.getWorldPoint() == null;
		assert unavailable.getLocalPoint() == null;
		assert unavailable.getPlane() == -1;
		assert unavailable.getRegionId() == -1;
		assert !unavailable.isInstanced();
		assert unavailable.getWorldViewId() == -1;
	}

	private static Client client(Player player)
	{
		return proxy(Client.class, (method, args) ->
			"getLocalPlayer".equals(method) ? player : null);
	}

	private static <T> T proxy(Class<T> type, Handler handler)
	{
		Object proxy = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
			(instance, method, args) ->
			{
				Object value = handler.invoke(method.getName(), args);
				return value == null ? defaultValue(method.getReturnType()) : value;
			});
		return type.cast(proxy);
	}

	private static Object defaultValue(Class<?> type)
	{
		if (!type.isPrimitive())
		{
			return null;
		}
		if (type == boolean.class)
		{
			return false;
		}
		if (type == char.class)
		{
			return '\0';
		}
		return 0;
	}

	@FunctionalInterface
	private interface Handler
	{
		Object invoke(String method, Object[] args);
	}
}
