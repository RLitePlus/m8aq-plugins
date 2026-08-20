package net.runelite.client.plugins.m8aq.api.runecrafting;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.GroundObject;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
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
		ItemContainer inventory = inventory(
			new Item(ItemID.GOTR_GUARDIAN_FRAGMENT, 42),
			new Item(ItemID.GOTR_GUARDIAN_ESSENCE, 1),
			new Item(ItemID.GOTR_GUARDIAN_ESSENCE, 1),
			new Item(ItemID.GOTR_GUARDIAN_ESSENCE, 1),
			new Item(ItemID.GOTR_CELL_UNCHARGED, 7),
			new Item(ItemID.GOTR_CELL_TIER3, 1),
			new Item(ItemID.GOTR_GUARDIAN_STONE_CATALYTIC, 2),
			new Item(ItemID.GOTR_GUARDIAN_STONE_ELEMENTAL, 3),
			new Item(ItemID.GOTR_GUARDIAN_STONE_POLYELEMENTAL, 4),
			new Item(ItemID.GOTR_GUARDIAN_STONE_POLYCATALYTIC, 5),
			new Item(ItemID.GOTR_PORTAL_TALISMAN_AIR, 1),
			new Item(ItemID.GOTR_PORTAL_TALISMAN_LAW, 2),
			new Item(ItemID.CHISEL, 1));
		GroundObject[] barrierObjects = new GroundObject[GuardiansOfTheRift.BarrierState.values().length + 1];
		for (int index = 0; index < GuardiansOfTheRift.BarrierState.values().length; index++)
		{
			GuardiansOfTheRift.BarrierState barrierState = GuardiansOfTheRift.BarrierState.values()[index];
			barrierObjects[index] = groundObject(
				barrierState.getObjectId(), new WorldPoint(3600 + index, 9500, 0));
		}
		barrierObjects[barrierObjects.length - 1] = groundObject(9999, new WorldPoint(3620, 9500, 0));

		GuardiansOfTheRift.State state = GuardiansOfTheRift.getState(
			client(varbits, widgets, new WorldPoint(3616, 9492, 0), inventory,
				worldView(barrierObjects)));
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
		GuardiansOfTheRift.InventoryState inventoryState = state.getInventory();
		assert inventoryState.getGuardianFragments() == 42;
		assert inventoryState.getGuardianEssence() == 3;
		assert inventoryState.getUnchargedCells() == 7;
		assert inventoryState.getChargedCellTier() == GuardiansOfTheRift.CellTier.STRONG;
		assert inventoryState.getCatalyticGuardianStones() == 2;
		assert inventoryState.getElementalGuardianStones() == 3;
		assert inventoryState.getPolyelementalGuardianStones() == 4;
		assert inventoryState.getPolycatalyticGuardianStones() == 5;
		assert inventoryState.getPortalTalismanCount(GuardiansOfTheRift.Altar.AIR) == 1;
		assert inventoryState.getPortalTalismanCount(GuardiansOfTheRift.Altar.LAW) == 2;
		assert inventoryState.getPortalTalismanCount(GuardiansOfTheRift.Altar.BLOOD) == 0;
		assert inventoryState.isChiselPresent();
		assert inventoryState.getEmptySlots() == 15;
		assert state.getBarriers().size() == GuardiansOfTheRift.BarrierState.values().length;
		GuardiansOfTheRift.Barrier repairableBarrier = state.getBarriers().stream()
			.filter(barrier -> barrier.getState() == GuardiansOfTheRift.BarrierState.BROKEN_REPAIRABLE)
			.findFirst()
			.orElseThrow(AssertionError::new);
		assert repairableBarrier.getState().isBroken();
		assert repairableBarrier.getState().isRepairable();
		assert !repairableBarrier.getState().acceptsCell();
		assert GuardiansOfTheRift.BarrierState.OVERCHARGED.acceptsCell();
		assert !GuardiansOfTheRift.BarrierState.BROKEN.isRepairable();
		assert !GuardiansOfTheRift.BarrierState.INACTIVE_NO_OP.acceptsCell();

		assert !GuardiansOfTheRift.Altar.AIR.isCatalytic();
		assert GuardiansOfTheRift.Altar.AIR.getRequiredRunecraftLevel() == 1;
		assert GuardiansOfTheRift.Altar.AIR.getCellTier() == GuardiansOfTheRift.CellTier.WEAK;
		assert GuardiansOfTheRift.Altar.AIR.getPortalTalismanItemId()
			== ItemID.GOTR_PORTAL_TALISMAN_AIR;
		assert GuardiansOfTheRift.Altar.COSMIC.getRequiredQuest() == Quest.LOST_CITY;
		assert GuardiansOfTheRift.Altar.LAW.getRequiredQuest() == Quest.TROLL_STRONGHOLD;
		assert GuardiansOfTheRift.Altar.DEATH.getRequiredQuest() == Quest.MOURNINGS_END_PART_II;
		assert GuardiansOfTheRift.Altar.BLOOD.getRequiredQuest() == Quest.SINS_OF_THE_FATHER;
		assert GuardiansOfTheRift.Altar.BLOOD.getRequiredRunecraftLevel() == 77;
		assert GuardiansOfTheRift.Altar.BLOOD.getCellTier()
			== GuardiansOfTheRift.CellTier.OVERCHARGED;

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
		return client(varbits, widgets, location, null, null);
	}

	private static Client client(
		Map<Integer, Integer> varbits,
		Map<Integer, Widget> widgets,
		WorldPoint location,
		ItemContainer inventory,
		WorldView worldView)
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
				case "getItemContainer":
					return inventory;
				case "getTopLevelWorldView":
					return worldView;
				default:
					return null;
			}
		});
	}

	private static ItemContainer inventory(Item... items)
	{
		return proxy(ItemContainer.class, (method, args) ->
		{
			switch (method)
			{
				case "getItems":
					return items;
				case "contains":
					return Arrays.stream(items).anyMatch(item -> item.getId() == (int) args[0]);
				case "count":
					return args == null
						? items.length
						: Arrays.stream(items)
							.filter(item -> item.getId() == (int) args[0])
							.mapToInt(Item::getQuantity)
							.sum();
				case "size":
					return 28;
				default:
					return null;
			}
		});
	}

	private static GroundObject groundObject(int id, WorldPoint location)
	{
		return proxy(GroundObject.class, (method, args) ->
		{
			switch (method)
			{
				case "getId":
					return id;
				case "getWorldLocation":
					return location;
				default:
					return null;
			}
		});
	}

	private static WorldView worldView(GroundObject... objects)
	{
		Tile[][][] tiles = new Tile[1][objects.length][1];
		for (int index = 0; index < objects.length; index++)
		{
			GroundObject object = objects[index];
			tiles[0][index][0] = proxy(Tile.class,
				(method, args) -> "getGroundObject".equals(method) ? object : null);
		}
		Scene scene = proxy(Scene.class,
			(method, args) -> "getTiles".equals(method) ? tiles : null);
		return proxy(WorldView.class, (method, args) ->
		{
			switch (method)
			{
				case "getScene":
					return scene;
				case "getPlane":
					return 0;
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
