package net.runelite.client.plugins.m8aq.api.smithing;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;

/** Minimal runnable check for {@link BlastFurnace}. */
public final class BlastFurnaceSelfTest
{
	private BlastFurnaceSelfTest()
	{
	}

	/** Runs the checks with Java assertions enabled. */
	public static void main(String[] args)
	{
		Map<Integer, Integer> values = new HashMap<>();
		values.put(VarbitID.BLAST_FURNACE_BARS_HOT, 2);
		values.put(VarbitID.BLAST_FURNACE_COFFER, 72_000);
		values.put(VarbitID.BLAST_FURNACE_COAL, 42);
		values.put(VarbitID.BLAST_FURNACE_STEEL_BARS, 28);

		BlastFurnace.State state = BlastFurnace.getState(client(values, true, new WorldPoint(1940, 4963, 0)));
		assert state.getDispenserState() == BlastFurnace.DispenserState.HOT;
		assert state.getCofferCoins() == 72_000;
		assert state.getMaterialCount(BlastFurnace.Material.COAL) == 42;
		assert state.getBarCount(BlastFurnace.Bar.STEEL) == 28;
		assert state.isReadyToCollect();
		assert state.isAtBlastFurnace();
		assert !state.needsCooling();

		state = BlastFurnace.getState(client(values, false, null));
		assert !state.isReadyToCollect();
		assert !state.isAtBlastFurnace();
		assert state.needsCooling();

		values.put(VarbitID.BLAST_FURNACE_BARS_HOT, 3);
		state = BlastFurnace.getState(client(values, false, null));
		assert state.isReadyToCollect();
		assert !state.needsCooling();

		values.put(VarbitID.BLAST_FURNACE_BARS_HOT, 4);
		state = BlastFurnace.getState(client(values, false, null));
		assert state.getDispenserState() == BlastFurnace.DispenserState.UNKNOWN;
	}

	private static Client client(Map<Integer, Integer> values, boolean iceGloves, WorldPoint location)
	{
		ItemContainer equipment = (ItemContainer) Proxy.newProxyInstance(
			ItemContainer.class.getClassLoader(),
			new Class<?>[]{ItemContainer.class},
			(proxy, method, args) -> "contains".equals(method.getName())
				&& iceGloves
				&& (int) args[0] == ItemID.ICE_GLOVES);
		Player player = location == null ? null : (Player) Proxy.newProxyInstance(
			Player.class.getClassLoader(),
			new Class<?>[]{Player.class},
			(proxy, method, args) -> "getWorldLocation".equals(method.getName())
				? location
				: defaultValue(method.getReturnType()));

		return (Client) Proxy.newProxyInstance(
			Client.class.getClassLoader(),
			new Class<?>[]{Client.class},
			(proxy, method, args) ->
			{
				if ("getVarbitValue".equals(method.getName()))
				{
					return values.getOrDefault((int) args[0], 0);
				}
				if ("getItemContainer".equals(method.getName()))
				{
					return equipment;
				}
				if ("getLocalPlayer".equals(method.getName()))
				{
					return player;
				}
				return defaultValue(method.getReturnType());
			});
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
}
