package net.runelite.client.plugins.m8aq.api.misc;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.ToIntFunction;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;

/** Provides a read-only snapshot of essence-pouch contents, degradation, and repair state. */
public final class EssencePouches
{
	private EssencePouches()
	{
	}

	/**
	 * Reads the current essence-pouch state from the client.
	 * This method must be called on the client thread.
	 *
	 * @param client RuneLite client
	 * @return immutable state snapshot
	 */
	public static State getState(Client client)
	{
		Objects.requireNonNull(client, "client");
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		ItemContainer equipment = client.getItemContainer(InventoryID.WORN);
		EnumMap<Pouch, PouchState> pouches = new EnumMap<>(Pouch.class);
		for (Pouch pouch : Pouch.values())
		{
			pouches.put(pouch, pouch.read(client, inventory));
		}

		return new State(
			pouches,
			client.getVarbitValue(VarbitID.GOTR_CORDELIA_REPAIR_POUCH) == 1,
			inventory == null ? 0 : inventory.count(ItemID.ABYSSAL_PEARL),
			client.getVarbitValue(VarbitID.GOTR_IS_PLAYING) == 1
				&& equipment != null
				&& equipment.contains(ItemID.ABYSSAL_LANTERN_REDWOOD));
	}

	/** Essence-pouch repair route. All routes repair the same pouch degradation. */
	public enum RepairMethod
	{
		DARK_MAGE(false, 0),
		ASTRAL_CONTACT(false, 0),
		APPRENTICE_CORDELIA(true, 1);

		/** @return whether this route requires visiting the Temple of the Eye, not an active round */
		@Getter
		private final boolean templeOfTheEyeOnly;
		/** @return abyssal pearls consumed per repair */
		@Getter
		private final int abyssalPearlCost;

		RepairMethod(boolean templeOfTheEyeOnly, int abyssalPearlCost)
		{
			this.templeOfTheEyeOnly = templeOfTheEyeOnly;
			this.abyssalPearlCost = abyssalPearlCost;
		}

	}

	/** Essence-pouch size and client values. */
	public enum Pouch
	{
		SMALL(
			VarbitID.SMALL_ESSENCE_POUCH,
			client -> 0,
			ItemID.RCU_POUCH_SMALL,
			-1,
			3),
		MEDIUM(
			VarbitID.MEDIUM_ESSENCE_POUCH,
			client -> client.getVarpValue(VarPlayerID.RCU_POUCH_DEGRADATION_MED),
			ItemID.RCU_POUCH_MEDIUM,
			ItemID.RCU_POUCH_MEDIUM_DEGRADE,
			6,
			800, 0,
			400, 3),
		LARGE(
			VarbitID.LARGE_ESSENCE_POUCH,
			client -> client.getVarpValue(VarPlayerID.RCU_POUCH_DEGRADATION_LARGE),
			ItemID.RCU_POUCH_LARGE,
			ItemID.RCU_POUCH_LARGE_DEGRADE,
			9,
			1000, 0,
			800, 3,
			600, 5,
			400, 7),
		GIANT(
			VarbitID.GIANT_ESSENCE_POUCH,
			client -> client.getVarpValue(VarPlayerID.RCU_POUCH_DEGRADATION_GIANT),
			ItemID.RCU_POUCH_GIANT,
			ItemID.RCU_POUCH_GIANT_DEGRADE,
			12,
			1200, 0,
			1000, 3,
			800, 5,
			600, 6,
			400, 7,
			300, 8,
			200, 9),
		COLOSSAL(
			VarbitID.COLOSSAL_ESSENCE_POUCH,
			client -> client.getVarbitValue(VarbitID.RCU_POUCH_DEGRADATION_COLOSSAL),
			ItemID.RCU_POUCH_COLOSSAL,
			ItemID.RCU_POUCH_COLOSSAL_DEGRADE,
			40,
			1020, 0,
			1015, 5,
			995, 10,
			950, 15,
			870, 20,
			745, 25,
			565, 30,
			320, 35)
			{
				@Override
				int scaleCapacity(Client client, int capacity)
				{
					if (capacity == 0)
					{
						return 0;
					}
					int level = client.getRealSkillLevel(Skill.RUNECRAFT);
					int maximum = level >= 85 ? 40 : level >= 75 ? 27 : level >= 50 ? 16 : 8;
					return Math.max(1, capacity * maximum / 40);
				}
			};

		private final int amountVarbit;
		private final ToIntFunction<Client> degradationReader;
		private final int itemId;
		private final int degradedItemId;
		private final int maximumCapacity;
		private final int[] degradationLevels;

		Pouch(
			int amountVarbit,
			ToIntFunction<Client> degradationReader,
			int itemId,
			int degradedItemId,
			int maximumCapacity,
			int... degradationLevels)
		{
			this.amountVarbit = amountVarbit;
			this.degradationReader = degradationReader;
			this.itemId = itemId;
			this.degradedItemId = degradedItemId;
			this.maximumCapacity = maximumCapacity;
			this.degradationLevels = degradationLevels;
		}

		private PouchState read(Client client, ItemContainer inventory)
		{
			int degradation = degradationReader.applyAsInt(client);
			int capacity = maximumCapacity;
			for (int index = 0; index < degradationLevels.length; index += 2)
			{
				if (degradation >= degradationLevels[index])
				{
					capacity = degradationLevels[index + 1];
					break;
				}
			}
			return new PouchState(
				this,
				client.getVarbitValue(amountVarbit),
				scaleCapacity(client, capacity),
				scaleCapacity(client, maximumCapacity),
				degradation,
				inventory != null && (inventory.contains(itemId)
					|| degradedItemId >= 0 && inventory.contains(degradedItemId)),
				inventory != null && degradedItemId >= 0 && inventory.contains(degradedItemId));
		}

		int scaleCapacity(Client client, int capacity)
		{
			return capacity;
		}
	}

	/** Immutable state for one pouch size. */
	public static final class PouchState
	{
		/** @return pouch size */
		@Getter
		private final Pouch pouch;
		/** @return essence currently stored in the pouch */
		@Getter
		private final int storedEssence;
		/** @return capacity at the current degradation and Runecraft level */
		@Getter
		private final int capacity;
		/** @return capacity with no degradation at the current Runecraft level */
		@Getter
		private final int maximumCapacity;
		/** @return raw degradation counter; this is not a remaining-use count */
		@Getter
		private final int degradation;
		/** @return whether this pouch size is currently visible in inventory */
		@Getter
		private final boolean inInventory;
		/** @return whether the inventory item is the visibly degraded variant */
		@Getter
		private final boolean visiblyDegradedInInventory;

		private PouchState(
			Pouch pouch,
			int storedEssence,
			int capacity,
			int maximumCapacity,
			int degradation,
			boolean inInventory,
			boolean visiblyDegradedInInventory)
		{
			this.pouch = pouch;
			this.storedEssence = storedEssence;
			this.capacity = capacity;
			this.maximumCapacity = maximumCapacity;
			this.degradation = degradation;
			this.inInventory = inInventory;
			this.visiblyDegradedInInventory = visiblyDegradedInInventory;
		}

		/** @return whether any accumulated degradation can be repaired */
		public boolean needsRepair()
		{
			return degradation > 0;
		}

		@Override
		public String toString()
		{
			return "{storedEssence=" + storedEssence
				+ ", capacity=" + capacity
				+ ", maximumCapacity=" + maximumCapacity
				+ ", degradation=" + degradation
				+ ", inInventory=" + inInventory
				+ ", visiblyDegraded=" + visiblyDegradedInInventory
				+ ", needsRepair=" + needsRepair() + "}";
		}
	}

	/** Immutable aggregate essence-pouch state captured from one client read. */
	public static final class State
	{
		/** @return immutable state for every pouch size */
		@Getter
		private final Map<Pouch, PouchState> pouches;
		/** @return whether Cordelia's 25-pearl repair unlock has been purchased */
		@Getter
		private final boolean cordeliaRepairUnlocked;
		/** @return abyssal pearls currently in inventory */
		@Getter
		private final int abyssalPearls;
		/**
		 * @return whether a redwood-lit lantern is currently preventing degradation in GOTR
		 */
		@Getter
		private final boolean gotrDecayProtectionActive;

		private State(
			EnumMap<Pouch, PouchState> pouches,
			boolean cordeliaRepairUnlocked,
			int abyssalPearls,
			boolean gotrDecayProtectionActive)
		{
			this.pouches = Collections.unmodifiableMap(pouches);
			this.cordeliaRepairUnlocked = cordeliaRepairUnlocked;
			this.abyssalPearls = abyssalPearls;
			this.gotrDecayProtectionActive = gotrDecayProtectionActive;
		}

		/**
		 * Returns one pouch-size state.
		 *
		 * @param pouch pouch size
		 * @return pouch state
		 */
		public PouchState getPouch(Pouch pouch)
		{
			return pouches.get(Objects.requireNonNull(pouch, "pouch"));
		}

		/** @return whether any pouch has accumulated repairable degradation */
		public boolean needsRepair()
		{
			for (PouchState pouch : pouches.values())
			{
				if (pouch.needsRepair())
				{
					return true;
				}
			}
			return false;
		}

		/** @return whether the player has unlocked and can pay for Cordelia's next repair */
		public boolean canPayForCordeliaRepair()
		{
			return cordeliaRepairUnlocked
				&& abyssalPearls >= RepairMethod.APPRENTICE_CORDELIA.getAbyssalPearlCost();
		}

	}
}
