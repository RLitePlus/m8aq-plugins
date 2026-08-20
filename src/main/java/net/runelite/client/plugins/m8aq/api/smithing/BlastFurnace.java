package net.runelite.client.plugins.m8aq.api.smithing;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;

/**
 * Provides a read-only snapshot of the player-visible Blast Furnace state on
 * themed worlds. Because the values can persist outside the minigame, the
 * snapshot also records whether the local player is in the Blast Furnace region.
 */
public final class BlastFurnace
{
	private static final int BLAST_FURNACE_REGION_ID = 7757;

	private BlastFurnace()
	{
	}

	/**
	 * Reads the current Blast Furnace state from the client.
	 * This method must be called on the client thread.
	 *
	 * @param client RuneLite client
	 * @return immutable state snapshot
	 */
	public static State getState(Client client)
	{
		Objects.requireNonNull(client, "client");

		EnumMap<Material, Integer> materials = new EnumMap<>(Material.class);
		for (Material material : Material.values())
		{
			putNonZero(materials, material, client.getVarbitValue(material.varbit));
		}

		EnumMap<Bar, Integer> bars = new EnumMap<>(Bar.class);
		for (Bar bar : Bar.values())
		{
			putNonZero(bars, bar, client.getVarbitValue(bar.varbit));
		}

		ItemContainer equipment = client.getItemContainer(InventoryID.WORN);
		boolean coolingGlovesEquipped = equipment != null
			&& (equipment.contains(ItemID.ICE_GLOVES)
				|| equipment.contains(ItemID.SMITHING_UNIFORM_GLOVES_ICE));
		Player localPlayer = client.getLocalPlayer();
		boolean atBlastFurnace = localPlayer != null
			&& localPlayer.getWorldLocation().getRegionID() == BLAST_FURNACE_REGION_ID;

		return new State(
			DispenserState.fromValue(client.getVarbitValue(VarbitID.BLAST_FURNACE_BARS_HOT)),
			client.getVarbitValue(VarbitID.BLAST_FURNACE_COFFER),
			materials,
			bars,
			coolingGlovesEquipped,
			atBlastFurnace);
	}

	private static <K extends Enum<K>> void putNonZero(Map<K, Integer> counts, K key, int count)
	{
		if (count != 0)
		{
			counts.put(key, count);
		}
	}

	/** Bar-dispenser state. */
	public enum DispenserState
	{
		UNKNOWN(-1),
		EMPTY(0),
		PROCESSING(1),
		HOT(2),
		COOLED(3);

		/**
		 * Returns the raw varbit value represented by this state.
		 *
		 * @return raw dispenser value, or {@code -1} for {@link #UNKNOWN}
		 */
		@Getter
		private final int value;

		DispenserState(int value)
		{
			this.value = value;
		}

		private static DispenserState fromValue(int value)
		{
			for (DispenserState state : values())
			{
				if (state.value == value)
				{
					return state;
				}
			}
			return UNKNOWN;
		}
	}

	/** Materials currently held by the furnace. */
	public enum Material
	{
		COAL(VarbitID.BLAST_FURNACE_COAL),
		COPPER_ORE(VarbitID.BLAST_FURNACE_COPPER_ORE),
		TIN_ORE(VarbitID.BLAST_FURNACE_TIN_ORE),
		IRON_ORE(VarbitID.BLAST_FURNACE_IRON_ORE),
		SILVER_ORE(VarbitID.BLAST_FURNACE_SILVER_ORE),
		GOLD_ORE(VarbitID.BLAST_FURNACE_GOLD_ORE),
		PERFECT_GOLD_ORE(VarbitID.BLAST_FURNACE_PERFECT_GOLD_ORE),
		MITHRIL_ORE(VarbitID.BLAST_FURNACE_MITHRIL_ORE),
		ADAMANTITE_ORE(VarbitID.BLAST_FURNACE_ADAMANTITE_ORE),
		RUNITE_ORE(VarbitID.BLAST_FURNACE_RUNITE_ORE),
		LEAD_ORE(VarbitID.BLAST_FURNACE_LEAD_ORE),
		NICKEL_ORE(VarbitID.BLAST_FURNACE_NICKEL_ORE),
		SMITHING_CATALYST(VarbitID.BLAST_FURNACE_CATALYST);

		private final int varbit;

		Material(int varbit)
		{
			this.varbit = varbit;
		}
	}

	/** Bars currently held by the dispenser. */
	public enum Bar
	{
		BRONZE(VarbitID.BLAST_FURNACE_BRONZE_BARS),
		IRON(VarbitID.BLAST_FURNACE_IRON_BARS),
		STEEL(VarbitID.BLAST_FURNACE_STEEL_BARS),
		SILVER(VarbitID.BLAST_FURNACE_SILVER_BARS),
		GOLD(VarbitID.BLAST_FURNACE_GOLD_BARS),
		PERFECT_GOLD(VarbitID.BLAST_FURNACE_PERFECT_GOLD_BARS),
		MITHRIL(VarbitID.BLAST_FURNACE_MITHRIL_BARS),
		ADAMANTITE(VarbitID.BLAST_FURNACE_ADAMANTITE_BARS),
		RUNITE(VarbitID.BLAST_FURNACE_RUNITE_BARS),
		LEAD(VarbitID.BLAST_FURNACE_LEAD_BARS),
		CUPRONICKEL(VarbitID.BLAST_FURNACE_CUPRONICKEL_BARS);

		private final int varbit;

		Bar(int varbit)
		{
			this.varbit = varbit;
		}
	}

	/** Immutable Blast Furnace state captured from one client read. */
	public static final class State
	{
		/** @return current dispenser state */
		@Getter
		private final DispenserState dispenserState;
		/** @return coffer balance in coins */
		@Getter
		private final int cofferCoins;
		/** @return immutable map containing nonzero stored-material counts */
		@Getter
		private final Map<Material, Integer> materials;
		/** @return immutable map containing nonzero stored-bar counts */
		@Getter
		private final Map<Bar, Integer> bars;
		/** @return whether ice-capable gloves are currently equipped */
		@Getter
		private final boolean coolingGlovesEquipped;
		/** @return whether the local player is in the Blast Furnace region */
		@Getter
		private final boolean atBlastFurnace;

		private State(
			DispenserState dispenserState,
			int cofferCoins,
			EnumMap<Material, Integer> materials,
			EnumMap<Bar, Integer> bars,
			boolean coolingGlovesEquipped,
			boolean atBlastFurnace)
		{
			this.dispenserState = dispenserState;
			this.cofferCoins = cofferCoins;
			this.materials = Collections.unmodifiableMap(materials);
			this.bars = Collections.unmodifiableMap(bars);
			this.coolingGlovesEquipped = coolingGlovesEquipped;
			this.atBlastFurnace = atBlastFurnace;
		}

		/**
		 * Returns a stored-material count.
		 *
		 * @param material material to query
		 * @return stored quantity, or zero
		 */
		public int getMaterialCount(Material material)
		{
			return materials.getOrDefault(material, 0);
		}

		/**
		 * Returns a stored-bar count.
		 *
		 * @param bar bar to query
		 * @return stored quantity, or zero
		 */
		public int getBarCount(Bar bar)
		{
			return bars.getOrDefault(bar, 0);
		}

		/** @return whether the conveyor/dispenser is processing a batch */
		public boolean isProcessing()
		{
			return dispenserState == DispenserState.PROCESSING;
		}

		/** @return whether hot bars cannot currently be collected */
		public boolean needsCooling()
		{
			return dispenserState == DispenserState.HOT && !coolingGlovesEquipped;
		}

		/** @return whether bars are currently ready to collect */
		public boolean isReadyToCollect()
		{
			return dispenserState == DispenserState.COOLED
				|| dispenserState == DispenserState.HOT && coolingGlovesEquipped;
		}

	}
}
