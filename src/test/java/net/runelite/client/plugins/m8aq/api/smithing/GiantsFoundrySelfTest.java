package net.runelite.client.plugins.m8aq.api.smithing;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;

/** Minimal runnable check for {@link GiantsFoundry}. */
public final class GiantsFoundrySelfTest
{
	private GiantsFoundrySelfTest()
	{
	}

	/** Runs the checks with Java assertions enabled. */
	public static void main(String[] args)
	{
		Map<Integer, Integer> varbits = new HashMap<>();
		Map<Integer, Widget> widgets = new HashMap<>();
		varbits.put(VarbitID.GIANTS_FOUNDRY_COMMISSION_WORD_1, 4);
		varbits.put(VarbitID.GIANTS_FOUNDRY_COMMISSION_WORD_2, 6);
		varbits.put(VarbitID.GIANTS_FOUNDRY_STEEL_COUNT, 14);
		varbits.put(VarbitID.GIANTS_FOUNDRY_MITHRIL_COUNT, 14);
		varbits.put(VarbitID.GIANTS_FOUNDRY_PREFORM_DIFICULTY, 60);
		varbits.put(VarbitID.GIANTS_FOUNDRY_PREFORM_QUALITY, 150);
		varbits.put(VarbitID.GIANTS_FOUNDRY_PREFORM_START_QUALITY, 155);
		varbits.put(VarbitID.GIANTS_FOUNDRY_PREFORM_TEMPERATURE, 800);
		widgets.put(InterfaceID.GiantsFoundryHud.UNIVERSE, widget(false, 0));
		widgets.put(InterfaceID.GiantsFoundryHud.SWEETSPOT_LAYER,
			widget(false, 0, widget(false, 0xfcd703)));
		widgets.put(InterfaceID.GiantsFoundryHud.COMPLETION_BAR_COVER, widget(true, 0));

		Client client = client(varbits, widgets, new WorldPoint(3357, 11488, 0));
		GiantsFoundry.State state = GiantsFoundry.getState(client);
		assert state.getCommissionTypes().contains(GiantsFoundry.CommissionType.BROAD);
		assert state.getCommissionTypes().contains(GiantsFoundry.CommissionType.SPIKED);
		assert state.getCrucibleCount(GiantsFoundry.Bar.STEEL) == 14;
		assert state.getCrucibleCount(GiantsFoundry.Bar.MITHRIL) == 14;
		assert state.getCrucibleBarCount() == 28;
		assert state.isCrucibleFull();
		assert state.isRefining();
		assert !state.isPreformStored();
		assert state.getTemperature() == 800;
		assert state.getCompletion() == 0;
		assert state.getQuality() == 150;
		assert state.getStartingQuality() == 155;
		assert state.getRequiredTool() == GiantsFoundry.Tool.TRIP_HAMMER;
		assert state.getTemperatureStatus() == GiantsFoundry.TemperatureStatus.READY;
		assert state.isBonusActive();
		assert !state.isReadyToHandIn();
		assert state.isAtGiantsFoundry();

		varbits.put(VarbitID.GIANTS_FOUNDRY_PREFORM_COMPLETION, 200);
		varbits.put(VarbitID.GIANTS_FOUNDRY_SEED_1, 1);
		varbits.put(VarbitID.GIANTS_FOUNDRY_PREFORM_TEMPERATURE, 700);
		state = GiantsFoundry.getState(client);
		assert state.getRequiredTool() == GiantsFoundry.Tool.GRINDSTONE;
		assert state.getTemperatureStatus() == GiantsFoundry.TemperatureStatus.TOO_HOT;

		varbits.put(VarbitID.GIANTS_FOUNDRY_PREFORM_TEMPERATURE, 371);
		state = GiantsFoundry.getState(client);
		assert state.getTemperatureStatus() == GiantsFoundry.TemperatureStatus.READY;

		varbits.put(VarbitID.GIANTS_FOUNDRY_PREFORM_COMPLETION, 1000);
		widgets.put(InterfaceID.GiantsFoundryHud.COMPLETION_BAR_COVER, widget(false, 0));
		state = GiantsFoundry.getState(client);
		assert state.getRequiredTool() == GiantsFoundry.Tool.NONE;
		assert state.getTemperatureStatus() == GiantsFoundry.TemperatureStatus.UNKNOWN;
		assert state.isReadyToHandIn();

		varbits.put(VarbitID.GIANTS_FOUNDRY_PREFORM_COMPLETION, 400);
		varbits.put(VarbitID.GIANTS_FOUNDRY_SEED_2, 2);
		varbits.put(VarbitID.GIANTS_FOUNDRY_PREFORM_TEMPERATURE, 38);
		varbits.put(VarbitID.GIANTS_FOUNDRY_PREFORM_STORED, 1);
		widgets.clear();
		state = GiantsFoundry.getState(client(varbits, widgets, null));
		assert !state.isRefining();
		assert state.isPreformStored();
		assert state.getRequiredTool() == GiantsFoundry.Tool.POLISHING_WHEEL;
		assert state.getTemperatureStatus() == GiantsFoundry.TemperatureStatus.READY;
		assert !state.isAtGiantsFoundry();

		boolean immutable = false;
		try
		{
			state.getCrucibleContents().put(GiantsFoundry.Bar.RUNE, 1);
		}
		catch (UnsupportedOperationException ex)
		{
			immutable = true;
		}
		assert immutable;
	}

	private static Client client(
		Map<Integer, Integer> varbits,
		Map<Integer, Widget> widgets,
		WorldPoint location)
	{
		Player player = location == null ? null : proxy(Player.class,
			(method, args) -> "getWorldLocation".equals(method) ? location : null);
		return proxy(Client.class, (method, args) ->
		{
			switch (method)
			{
				case "getVarbitValue":
					return varbits.getOrDefault((int) args[0], 0);
				case "getWidget":
					return widgets.get((int) args[0]);
				case "getLocalPlayer":
					return player;
				default:
					return null;
			}
		});
	}

	private static Widget widget(boolean hidden, int textColor, Widget... children)
	{
		return proxy(Widget.class, (method, args) ->
		{
			switch (method)
			{
				case "isHidden":
					return hidden;
				case "getTextColor":
					return textColor;
				case "getChildren":
					return children;
				default:
					return null;
			}
		});
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
