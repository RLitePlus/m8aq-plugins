package net.runelite.client.plugins.m8aq.api.runecrafting;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;

/** Minimal runnable check for {@link GuardiansOfTheRift}. */
public final class GuardiansOfTheRiftSelfTest
{
	private GuardiansOfTheRiftSelfTest()
	{
	}

	/** Runs the checks with Java assertions enabled. */
	public static void main(String[] args)
	{
		Map<Integer, Integer> varbits = new HashMap<>();
		Map<Integer, Widget> widgets = new HashMap<>();
		varbits.put(VarbitID.GOTR_ELEMENTAL_EARNED_THIS_GAME, 175);
		varbits.put(VarbitID.GOTR_CATALYTIC_EARNED_THIS_GAME, 130);
		widgets.put(InterfaceID.GotrHud.UNIVERSE, widget(false, "", 0));
		widgets.put(InterfaceID.GotrHud.ENERGY_TITLE,
			widget(false, "Guardian's Power: 63%", 0));
		widgets.put(InterfaceID.GotrHud.PORTAL_TIME, widget(false, "1:23", 0));
		widgets.put(InterfaceID.GotrHud.ELEMENTAL_PORTAL, widget(false, "", 4356));
		widgets.put(InterfaceID.GotrHud.CATALYTIC_PORTAL, widget(false, "", 4362));
		widgets.put(InterfaceID.GotrHud.GUARDIAN_LIMIT, widget(false, "7/10", 0));
		widgets.put(InterfaceID.GotrHud.PORTAL_SHORTCUT_LAYER, widget(false, "", 0));
		widgets.put(InterfaceID.GotrHud.PORTAL_POSITION, widget(false, "SE - 0:23", 0));

		GuardiansOfTheRift.State state = GuardiansOfTheRift.getState(
			client(varbits, widgets, new WorldPoint(3616, 9492, 0)));
		assert state.isHudVisible();
		assert state.isRoundActive();
		assert state.isInArena();
		assert !state.isInLobby();
		assert !state.hasFinishedGame();
		assert state.getGuardianPower() == 63;
		assert state.getAltarRotationSecondsRemaining() == 83;
		assert state.getElementalEnergy() == 175;
		assert state.getCatalyticEnergy() == 130;
		assert state.getTotalEnergy() == 305;
		assert state.isRewardEligible();
		assert state.getElementalAltar() == GuardiansOfTheRift.Altar.EARTH;
		assert state.getCatalyticAltar() == GuardiansOfTheRift.Altar.LAW;
		assert state.getActiveGuardians() == 7;
		assert state.getGuardianLimit() == 10;
		assert state.isGuardianEssencePortalOpen();
		assert "SE".equals(state.getGuardianEssencePortalPosition());
		assert state.getGuardianEssencePortalSecondsRemaining() == 23;
		assert state.isInMainTemple();
		assert state.getMiningArea() == GuardiansOfTheRift.MiningArea.NONE;
		assert state.getCurrentAltar() == GuardiansOfTheRift.Altar.UNKNOWN;

		widgets.put(InterfaceID.GotrHud.GUARDIAN_LIMIT, widget(false, "unknown", 0));
		widgets.put(InterfaceID.GotrHud.ENERGY_TITLE, widget(false, "unavailable", 0));
		widgets.put(InterfaceID.GotrHud.PORTAL_TIME, widget(false, "unavailable", 0));
		widgets.put(InterfaceID.GotrHud.ELEMENTAL_PORTAL, widget(false, "", 4354));
		widgets.put(InterfaceID.GotrHud.CATALYTIC_PORTAL, widget(false, "", 9999));
		widgets.put(InterfaceID.GotrHud.PORTAL_SHORTCUT_LAYER, widget(true, "", 0));
		state = GuardiansOfTheRift.getState(client(varbits, widgets, null));
		assert state.isHudVisible();
		assert state.getGuardianPower() == -1;
		assert state.getAltarRotationSecondsRemaining() == -1;
		assert state.getElementalAltar() == GuardiansOfTheRift.Altar.UNKNOWN;
		assert state.getCatalyticAltar() == GuardiansOfTheRift.Altar.UNKNOWN;
		assert state.getActiveGuardians() == -1;
		assert state.getGuardianLimit() == -1;
		assert !state.isGuardianEssencePortalOpen();
		assert state.getGuardianEssencePortalPosition().isEmpty();
		assert state.getGuardianEssencePortalSecondsRemaining() == -1;
		assert !state.isInMainTemple();

		widgets.put(InterfaceID.GotrHud.UNIVERSE, widget(true, "", 0));
		state = GuardiansOfTheRift.getState(
			client(varbits, widgets, new WorldPoint(3616, 9482, 0)));
		assert !state.isHudVisible();
		assert state.getGuardianPower() == -1;
		assert !state.isRoundActive();
		assert state.isInLobby();
		assert !state.isInArena();
		assert state.getCurrentAltar() == GuardiansOfTheRift.Altar.UNKNOWN;

		widgets.put(InterfaceID.GotrHud.UNIVERSE, widget(false, "", 0));
		widgets.put(InterfaceID.GotrHud.ENERGY_TITLE,
			widget(false, "Guardian's Power: 0%", 0));
		widgets.put(InterfaceID.GotrHud.PORTAL_TIME, widget(true, "", 0));
		state = GuardiansOfTheRift.getState(
			client(varbits, widgets, new WorldPoint(3616, 9492, 0)));
		assert !state.isRoundActive();
		assert state.isInArena();
		assert !state.isInLobby();
		assert state.getAltarRotationSecondsRemaining() == -1;

		widgets.put(InterfaceID.GotrHud.ENERGY_TITLE,
			widget(false, "Guardian's Power: 63%", 0));
		state = GuardiansOfTheRift.getState(
			client(varbits, widgets, new WorldPoint(2848, 4832, 0)));
		assert state.getCurrentAltar() == GuardiansOfTheRift.Altar.AIR;
		assert !state.isInMainTemple();
		assert !state.isInLobby();
		assert !state.isInArena();
		assert state.getMiningArea() == GuardiansOfTheRift.MiningArea.NONE;

		int[][] flags = new int[7][7];
		for (int i = 0; i < flags.length; i++)
		{
			flags[0][i] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
			flags[3][i] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
			flags[6][i] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
			flags[i][0] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
			flags[i][6] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
		}
		Set<Integer> component = GuardiansOfTheRift.flood(flags,
			Set.of(GuardiansOfTheRift.pack(1, 1)));
		assert component.size() == 10;
		assert component.contains(GuardiansOfTheRift.pack(2, 5));
		assert !component.contains(GuardiansOfTheRift.pack(4, 1));

		WorldPoint east = new WorldPoint(3637, 9503, 0);
		WorldPoint west = new WorldPoint(3590, 9505, 0);
		assert GuardiansOfTheRift.MiningArea.fromTiles(east, Set.of(east), Set.of(west))
			== GuardiansOfTheRift.MiningArea.EAST_LARGE_REMAINS;
		assert GuardiansOfTheRift.MiningArea.fromTiles(west, Set.of(east), Set.of(west))
			== GuardiansOfTheRift.MiningArea.WEST_HUGE_REMAINS;
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

	private static Widget widget(boolean hidden, String text, int spriteId)
	{
		return proxy(Widget.class, (method, args) ->
		{
			switch (method)
			{
				case "isHidden":
					return hidden;
				case "getText":
					return text;
				case "getSpriteId":
					return spriteId;
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
