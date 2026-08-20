package net.runelite.client.plugins.m8aq.api.smithing;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;

/** Provides a read-only snapshot of the player-visible Giants' Foundry state. */
public final class GiantsFoundry
{
	private static final int GIANTS_FOUNDRY_REGION_ID = 13491;
	private static final int CRUCIBLE_CAPACITY = 28;
	private static final int MAX_COMPLETION = 1000;
	private static final int MAX_DIFFICULTY = 130;
	private static final int BONUS_COLOR = 0xfcd703;

	private GiantsFoundry()
	{
	}

	/**
	 * Reads the current Giants' Foundry state from the client.
	 * This method must be called on the client thread.
	 *
	 * @param client RuneLite client
	 * @return immutable state snapshot
	 */
	public static State getState(Client client)
	{
		Objects.requireNonNull(client, "client");

		EnumSet<CommissionType> commissionTypes = EnumSet.noneOf(CommissionType.class);
		addCommissionType(commissionTypes,
			client.getVarbitValue(VarbitID.GIANTS_FOUNDRY_COMMISSION_WORD_1));
		addCommissionType(commissionTypes,
			client.getVarbitValue(VarbitID.GIANTS_FOUNDRY_COMMISSION_WORD_2));

		EnumMap<Bar, Integer> crucibleContents = new EnumMap<>(Bar.class);
		for (Bar bar : Bar.values())
		{
			int count = client.getVarbitValue(bar.varbit);
			if (count != 0)
			{
				crucibleContents.put(bar, count);
			}
		}

		int difficulty = client.getVarbitValue(VarbitID.GIANTS_FOUNDRY_PREFORM_DIFICULTY);
		int quality = client.getVarbitValue(VarbitID.GIANTS_FOUNDRY_PREFORM_QUALITY);
		int temperature = client.getVarbitValue(VarbitID.GIANTS_FOUNDRY_PREFORM_TEMPERATURE);
		int completion = client.getVarbitValue(VarbitID.GIANTS_FOUNDRY_PREFORM_COMPLETION);
		boolean preformStored = client.getVarbitValue(VarbitID.GIANTS_FOUNDRY_PREFORM_STORED) == 1;
		boolean refining = isVisible(client.getWidget(InterfaceID.GiantsFoundryHud.UNIVERSE));

		Tool requiredTool = Tool.NONE;
		if ((refining || preformStored) && quality > 0 && completion < MAX_COMPLETION)
		{
			requiredTool = requiredTool(client, difficulty, completion);
		}

		Player localPlayer = client.getLocalPlayer();
		boolean atGiantsFoundry = localPlayer != null
			&& localPlayer.getWorldLocation().getRegionID() == GIANTS_FOUNDRY_REGION_ID;

		return new State(
			commissionTypes,
			crucibleContents,
			refining,
			preformStored,
			temperature,
			completion,
			quality,
			client.getVarbitValue(VarbitID.GIANTS_FOUNDRY_PREFORM_START_QUALITY),
			requiredTool,
			temperatureStatus(requiredTool, difficulty, temperature),
			isBonusActive(client.getWidget(InterfaceID.GiantsFoundryHud.SWEETSPOT_LAYER)),
			isVisible(client.getWidget(InterfaceID.GiantsFoundryHud.COMPLETION_BAR_COVER)),
			atGiantsFoundry);
	}

	private static void addCommissionType(Set<CommissionType> types, int value)
	{
		CommissionType type = CommissionType.fromValue(value);
		if (type != null)
		{
			types.add(type);
		}
	}

	private static boolean isVisible(Widget widget)
	{
		return widget != null && !widget.isHidden();
	}

	private static boolean isBonusActive(Widget widget)
	{
		if (widget == null || widget.getChildren() == null || widget.getChildren().length == 0)
		{
			return false;
		}
		Widget child = widget.getChildren()[0];
		return child != null && child.getTextColor() == BONUS_COLOR;
	}

	private static Tool requiredTool(Client client, int difficulty, int completion)
	{
		int section = completion * sectionCount(difficulty) / MAX_COMPLETION;
		if (section == 0)
		{
			return Tool.TRIP_HAMMER;
		}

		int seedVarbit;
		switch (section)
		{
			case 1:
				seedVarbit = VarbitID.GIANTS_FOUNDRY_SEED_1;
				break;
			case 2:
				seedVarbit = VarbitID.GIANTS_FOUNDRY_SEED_2;
				break;
			case 3:
				seedVarbit = VarbitID.GIANTS_FOUNDRY_SEED_3;
				break;
			case 4:
				seedVarbit = VarbitID.GIANTS_FOUNDRY_SEED_4;
				break;
			case 5:
				seedVarbit = VarbitID.GIANTS_FOUNDRY_SEED_5;
				break;
			case 6:
				seedVarbit = VarbitID.GIANTS_FOUNDRY_SEED_6;
				break;
			default:
				return Tool.NONE;
		}
		return Tool.fromSeed(client.getVarbitValue(seedVarbit));
	}

	private static int sectionCount(int difficulty)
	{
		if (difficulty < 20)
		{
			return 3;
		}
		if (difficulty < 60)
		{
			return 4;
		}
		if (difficulty < 90)
		{
			return 5;
		}
		if (difficulty < 120)
		{
			return 6;
		}
		return 7;
	}

	private static TemperatureStatus temperatureStatus(Tool tool, int difficulty, int temperature)
	{
		if (tool == Tool.NONE)
		{
			return TemperatureStatus.UNKNOWN;
		}

		int third = MAX_COMPLETION / 3;
		int half = third - third / 2;
		int width = third - difficulty * half / MAX_DIFFICULTY;
		int minimum = third / 2 + third * tool.temperatureBand - width / 2;
		if (temperature < minimum)
		{
			return TemperatureStatus.TOO_COLD;
		}
		if (temperature > minimum + width)
		{
			return TemperatureStatus.TOO_HOT;
		}
		return TemperatureStatus.READY;
	}

	/** Commission descriptors used to score mould choices. */
	public enum CommissionType
	{
		NARROW(1),
		LIGHT(2),
		FLAT(3),
		BROAD(4),
		HEAVY(5),
		SPIKED(6);

		private final int value;

		CommissionType(int value)
		{
			this.value = value;
		}

		private static CommissionType fromValue(int value)
		{
			for (CommissionType type : values())
			{
				if (type.value == value)
				{
					return type;
				}
			}
			return null;
		}
	}

	/** Metal bars currently held by the crucible. */
	public enum Bar
	{
		BRONZE(VarbitID.GIANTS_FOUNDRY_BRONZE_COUNT),
		IRON(VarbitID.GIANTS_FOUNDRY_IRON_COUNT),
		STEEL(VarbitID.GIANTS_FOUNDRY_STEEL_COUNT),
		MITHRIL(VarbitID.GIANTS_FOUNDRY_MITHRIL_COUNT),
		ADAMANT(VarbitID.GIANTS_FOUNDRY_ADAMANT_COUNT),
		RUNE(VarbitID.GIANTS_FOUNDRY_RUNE_COUNT);

		private final int varbit;

		Bar(int varbit)
		{
			this.varbit = varbit;
		}
	}

	/** Refinement tool required by the current completion section. */
	public enum Tool
	{
		NONE(-1),
		TRIP_HAMMER(2),
		GRINDSTONE(1),
		POLISHING_WHEEL(0);

		private final int temperatureBand;

		Tool(int temperatureBand)
		{
			this.temperatureBand = temperatureBand;
		}

		private static Tool fromSeed(int seed)
		{
			switch (seed)
			{
				case 0:
					return TRIP_HAMMER;
				case 1:
					return GRINDSTONE;
				case 2:
					return POLISHING_WHEEL;
				default:
					return NONE;
			}
		}
	}

	/** Current temperature relative to the required tool's valid range. */
	public enum TemperatureStatus
	{
		UNKNOWN,
		TOO_COLD,
		READY,
		TOO_HOT
	}

	/** Immutable Giants' Foundry state captured from one client read. */
	public static final class State
	{
		/** @return immutable set of active commission descriptors */
		@Getter
		private final Set<CommissionType> commissionTypes;
		/** @return immutable map containing nonzero crucible bar counts */
		@Getter
		private final Map<Bar, Integer> crucibleContents;
		/** @return total number of bars represented in the crucible */
		@Getter
		private final int crucibleBarCount;
		/** @return whether the refinement HUD is visible */
		@Getter
		private final boolean refining;
		/** @return whether the current preform is in Foundry storage */
		@Getter
		private final boolean preformStored;
		/** @return raw preform temperature on the 0-1000 HUD scale */
		@Getter
		private final int temperature;
		/** @return raw preform completion on the 0-1000 HUD scale */
		@Getter
		private final int completion;
		/** @return current preform quality */
		@Getter
		private final int quality;
		/** @return quality assigned when the preform was poured */
		@Getter
		private final int startingQuality;
		/** @return tool required by the current completion section */
		@Getter
		private final Tool requiredTool;
		/** @return current temperature relative to the required tool range */
		@Getter
		private final TemperatureStatus temperatureStatus;
		/** @return whether an unclaimed sweet-spot bonus is active */
		@Getter
		private final boolean bonusActive;
		/** @return whether the completed-sword hand-in indicator is visible */
		@Getter
		private final boolean readyToHandIn;
		/** @return whether the local player is in the Giants' Foundry region */
		@Getter
		private final boolean atGiantsFoundry;

		private State(
			EnumSet<CommissionType> commissionTypes,
			EnumMap<Bar, Integer> crucibleContents,
			boolean refining,
			boolean preformStored,
			int temperature,
			int completion,
			int quality,
			int startingQuality,
			Tool requiredTool,
			TemperatureStatus temperatureStatus,
			boolean bonusActive,
			boolean readyToHandIn,
			boolean atGiantsFoundry)
		{
			this.commissionTypes = Collections.unmodifiableSet(commissionTypes);
			this.crucibleContents = Collections.unmodifiableMap(crucibleContents);
			this.crucibleBarCount = crucibleContents.values().stream()
				.mapToInt(Integer::intValue)
				.sum();
			this.refining = refining;
			this.preformStored = preformStored;
			this.temperature = temperature;
			this.completion = completion;
			this.quality = quality;
			this.startingQuality = startingQuality;
			this.requiredTool = requiredTool;
			this.temperatureStatus = temperatureStatus;
			this.bonusActive = bonusActive;
			this.readyToHandIn = readyToHandIn;
			this.atGiantsFoundry = atGiantsFoundry;
		}

		/**
		 * Returns a crucible bar count.
		 *
		 * @param bar bar type to query
		 * @return stored quantity, or zero
		 */
		public int getCrucibleCount(Bar bar)
		{
			return crucibleContents.getOrDefault(bar, 0);
		}

		/** @return whether the crucible contains its full 28 bars */
		public boolean isCrucibleFull()
		{
			return crucibleBarCount == CRUCIBLE_CAPACITY;
		}

	}
}
