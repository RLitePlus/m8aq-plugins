package net.runelite.client.plugins.m8aq.api.misc;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;

/** Minimal runnable check for {@link EssencePouches}. */
public final class EssencePouchesSelfTest
{
	private EssencePouchesSelfTest()
	{
	}

	/** Runs the checks with Java assertions enabled. */
	public static void main(String[] args)
	{
		Map<Integer, Integer> varbits = new HashMap<>();
		Map<Integer, Integer> varps = new HashMap<>();
		Map<Integer, Integer> inventory = new HashMap<>();
		Map<Integer, Integer> equipment = new HashMap<>();

		varbits.put(VarbitID.SMALL_ESSENCE_POUCH, 3);
		varbits.put(VarbitID.MEDIUM_ESSENCE_POUCH, 2);
		varbits.put(VarbitID.LARGE_ESSENCE_POUCH, 5);
		varbits.put(VarbitID.GIANT_ESSENCE_POUCH, 3);
		varbits.put(VarbitID.COLOSSAL_ESSENCE_POUCH, 20);
		varbits.put(VarbitID.RCU_POUCH_DEGRADATION_COLOSSAL, 565);
		varbits.put(VarbitID.GOTR_CORDELIA_REPAIR_POUCH, 1);
		varbits.put(VarbitID.GOTR_IS_PLAYING, 1);
		varps.put(VarPlayerID.RCU_POUCH_DEGRADATION_MED, 400);
		varps.put(VarPlayerID.RCU_POUCH_DEGRADATION_LARGE, 600);
		varps.put(VarPlayerID.RCU_POUCH_DEGRADATION_GIANT, 1000);
		inventory.put(ItemID.RCU_POUCH_SMALL, 1);
		inventory.put(ItemID.RCU_POUCH_MEDIUM_DEGRADE, 1);
		inventory.put(ItemID.RCU_POUCH_COLOSSAL_DEGRADE, 1);
		inventory.put(ItemID.ABYSSAL_PEARL, 2);
		equipment.put(ItemID.ABYSSAL_LANTERN_REDWOOD, 1);

		EssencePouches.State state = EssencePouches.getState(
			client(varbits, varps, inventory, equipment, 75));
		assert state.getPouch(EssencePouches.Pouch.SMALL).getCapacity() == 3;
		assert state.getPouch(EssencePouches.Pouch.SMALL).getStoredEssence() == 3;
		assert !state.getPouch(EssencePouches.Pouch.SMALL).needsRepair();
		assert state.getPouch(EssencePouches.Pouch.MEDIUM).getCapacity() == 3;
		assert state.getPouch(EssencePouches.Pouch.LARGE).getCapacity() == 5;
		assert state.getPouch(EssencePouches.Pouch.GIANT).getCapacity() == 3;
		assert state.getPouch(EssencePouches.Pouch.COLOSSAL).getMaximumCapacity() == 27;
		assert state.getPouch(EssencePouches.Pouch.COLOSSAL).getCapacity() == 20;
		assert state.getPouch(EssencePouches.Pouch.MEDIUM).isVisiblyDegradedInInventory();
		assert !state.getPouch(EssencePouches.Pouch.LARGE).isInInventory();
		assert state.needsRepair();
		assert state.isCordeliaRepairUnlocked();
		assert state.getAbyssalPearls() == 2;
		assert state.canPayForCordeliaRepair();
		assert state.isGotrDecayProtectionActive();
		assert EssencePouches.RepairMethod.APPRENTICE_CORDELIA.isTempleOfTheEyeOnly();
		assert !EssencePouches.RepairMethod.DARK_MAGE.isTempleOfTheEyeOnly();
		assert !EssencePouches.RepairMethod.ASTRAL_CONTACT.isTempleOfTheEyeOnly();
	}

	private static Client client(
		Map<Integer, Integer> varbits,
		Map<Integer, Integer> varps,
		Map<Integer, Integer> inventory,
		Map<Integer, Integer> equipment,
		int runecraftLevel)
	{
		ItemContainer inventoryContainer = itemContainer(inventory);
		ItemContainer equipmentContainer = itemContainer(equipment);
		return proxy(Client.class, (method, args) ->
		{
			switch (method)
			{
				case "getVarbitValue":
					return varbits.getOrDefault((int) args[0], 0);
				case "getVarpValue":
					return varps.getOrDefault((int) args[0], 0);
				case "getItemContainer":
					return (int) args[0] == InventoryID.INV ? inventoryContainer : equipmentContainer;
				case "getRealSkillLevel":
					return args[0] == Skill.RUNECRAFT ? runecraftLevel : 1;
				default:
					return null;
			}
		});
	}

	private static ItemContainer itemContainer(Map<Integer, Integer> items)
	{
		return proxy(ItemContainer.class, (method, args) ->
		{
			switch (method)
			{
				case "contains":
					return items.containsKey((int) args[0]);
				case "count":
					return items.getOrDefault((int) args[0], 0);
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
