package net.runelite.client.plugins.m8aq.api.runecrafting;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.GameObject;
import net.runelite.api.GroundObject;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Quest;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;

/** Provides a read-only snapshot of the player-visible Guardians of the Rift state. */
public final class GuardiansOfTheRift
{
	private static final int MAIN_TEMPLE_REGION_ID = 14484;
	private static final int ENTRY_BARRIER_Y = 9483;
	private static final int REWARD_ENERGY_REQUIREMENT = 300;
	private static final int[][] DIRECTIONS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
	private static final int[] BLOCKED_FROM = {
		CollisionDataFlag.BLOCK_MOVEMENT_EAST,
		CollisionDataFlag.BLOCK_MOVEMENT_WEST,
		CollisionDataFlag.BLOCK_MOVEMENT_NORTH,
		CollisionDataFlag.BLOCK_MOVEMENT_SOUTH
	};

	private GuardiansOfTheRift()
	{
	}

	/**
	 * Reads the current Guardians of the Rift state from the client.
	 * This method must be called on the client thread.
	 *
	 * @param client RuneLite client
	 * @return immutable state snapshot
	 */
	public static State getState(Client client)
	{
		Objects.requireNonNull(client, "client");

		Widget hud = client.getWidget(InterfaceID.GotrHud.UNIVERSE);
		boolean hudVisible = isVisible(hud);
		int[] guardians = hudVisible
			? readPair(client.getWidget(InterfaceID.GotrHud.GUARDIAN_LIMIT))
			: new int[]{-1, -1};
		boolean guardianEssencePortalOpen = hudVisible
			&& isVisible(client.getWidget(InterfaceID.GotrHud.PORTAL_SHORTCUT_LAYER));
		String guardianEssencePortalStatus = guardianEssencePortalOpen
			? text(client.getWidget(InterfaceID.GotrHud.PORTAL_POSITION))
			: "";
		Player player = client.getLocalPlayer();
		WorldPoint location = player == null ? null : player.getWorldLocation();
		boolean inMainTemple = location != null
			&& location.getRegionID() == MAIN_TEMPLE_REGION_ID;
		InventoryState inventory = InventoryState.fromContainer(
			client.getItemContainer(InventoryID.INV));
		Set<Barrier> barriers = readBarriers(client.getTopLevelWorldView());

		return new State(
			hudVisible,
			client.getVarbitValue(VarbitID.GOTR_PLAYER_HAS_FINISHED_GAME) == 1,
			hudVisible ? readFirstInt(client.getWidget(InterfaceID.GotrHud.ENERGY_TITLE)) : -1,
			hudVisible
				? readDurationSeconds(client.getWidget(InterfaceID.GotrHud.PORTAL_TIME))
				: -1,
			client.getVarbitValue(VarbitID.GOTR_ELEMENTAL_EARNED_THIS_GAME),
			client.getVarbitValue(VarbitID.GOTR_CATALYTIC_EARNED_THIS_GAME),
			hudVisible ? Altar.fromWidget(client.getWidget(InterfaceID.GotrHud.ELEMENTAL_PORTAL), false) : Altar.UNKNOWN,
			hudVisible ? Altar.fromWidget(client.getWidget(InterfaceID.GotrHud.CATALYTIC_PORTAL), true) : Altar.UNKNOWN,
			guardians[0],
			guardians[1],
			guardianEssencePortalOpen,
			readPortalPosition(guardianEssencePortalStatus),
			readPortalSeconds(guardianEssencePortalStatus),
			inMainTemple,
			inMainTemple && location.getY() > ENTRY_BARRIER_Y,
			hudVisible && location != null
				? Altar.fromRegion(location.getRegionID())
				: Altar.UNKNOWN,
			inMainTemple ? MiningArea.fromClient(client, location) : MiningArea.NONE,
			inventory,
			barriers);
	}

	private static Set<Barrier> readBarriers(WorldView worldView)
	{
		if (worldView == null || worldView.getScene() == null)
		{
			return Collections.emptySet();
		}
		int plane = worldView.getPlane();
		Tile[][][] sceneTiles = worldView.getScene().getTiles();
		if (plane < 0 || plane >= sceneTiles.length || sceneTiles[plane] == null)
		{
			return Collections.emptySet();
		}

		Set<Barrier> barriers = new LinkedHashSet<>();
		for (Tile[] column : sceneTiles[plane])
		{
			if (column == null)
			{
				continue;
			}
			for (Tile tile : column)
			{
				GroundObject object = tile == null ? null : tile.getGroundObject();
				BarrierState state = object == null
					? null
					: BarrierState.fromObjectId(object.getId());
				if (state != null)
				{
					barriers.add(new Barrier(object.getWorldLocation(), state));
				}
			}
		}
		return Collections.unmodifiableSet(barriers);
	}

	/**
	 * Finds the walkable tiles in one physically isolated mining pocket.
	 * This method must be called on the client thread.
	 *
	 * @param client RuneLite client
	 * @param area mining pocket to read
	 * @return immutable set of currently loaded pocket tiles
	 */
	public static Set<WorldPoint> getMiningAreaTiles(Client client, MiningArea area)
	{
		Objects.requireNonNull(client, "client");
		Objects.requireNonNull(area, "area");
		if (area == MiningArea.NONE)
		{
			return Collections.emptySet();
		}

		WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null || worldView.getScene() == null)
		{
			return Collections.emptySet();
		}
		int plane = worldView.getPlane();
		CollisionData[] collisionMaps = worldView.getCollisionMaps();
		Tile[][][] sceneTiles = worldView.getScene().getTiles();
		if (collisionMaps == null || plane < 0 || plane >= collisionMaps.length
			|| collisionMaps[plane] == null || plane >= sceneTiles.length)
		{
			return Collections.emptySet();
		}

		int[][] flags = collisionMaps[plane].getFlags();
		Set<Integer> seeds = findPerimeterSeeds(sceneTiles[plane], flags, area.objectId);
		Set<WorldPoint> result = new HashSet<>();
		for (int tile : flood(flags, seeds))
		{
			result.add(WorldPoint.fromScene(worldView, tile >>> 16, tile & 0xffff, plane));
		}
		return Collections.unmodifiableSet(result);
	}

	private static Set<Integer> findPerimeterSeeds(Tile[][] tiles, int[][] flags, int objectId)
	{
		Set<Integer> seeds = new HashSet<>();
		for (Tile[] column : tiles)
		{
			if (column == null)
			{
				continue;
			}
			for (Tile tile : column)
			{
				if (tile == null || tile.getGameObjects() == null)
				{
					continue;
				}
				for (GameObject object : tile.getGameObjects())
				{
					if (object != null && object.getId() == objectId)
					{
						addPerimeterSeeds(seeds, flags,
							object.getSceneMinLocation(), object.getSceneMaxLocation());
					}
				}
			}
		}
		return seeds;
	}

	private static void addPerimeterSeeds(Set<Integer> seeds, int[][] flags,
		Point minimum, Point maximum)
	{
		for (int x = minimum.getX() - 1; x <= maximum.getX() + 1; x++)
		{
			for (int y = minimum.getY() - 1; y <= maximum.getY() + 1; y++)
			{
				if ((x == minimum.getX() - 1 || x == maximum.getX() + 1
					|| y == minimum.getY() - 1 || y == maximum.getY() + 1)
					&& isOpen(flags, x, y))
				{
					seeds.add(pack(x, y));
				}
			}
		}
	}

	static Set<Integer> flood(int[][] flags, Set<Integer> seeds)
	{
		Set<Integer> visited = new HashSet<>();
		Queue<Integer> pending = new ArrayDeque<>();
		for (int seed : seeds)
		{
			int x = seed >>> 16;
			int y = seed & 0xffff;
			if (isOpen(flags, x, y) && visited.add(seed))
			{
				pending.add(seed);
			}
		}

		while (!pending.isEmpty())
		{
			int tile = pending.remove();
			int x = tile >>> 16;
			int y = tile & 0xffff;
			for (int i = 0; i < DIRECTIONS.length; i++)
			{
				int nextX = x + DIRECTIONS[i][0];
				int nextY = y + DIRECTIONS[i][1];
				int next = pack(nextX, nextY);
				if (isOpen(flags, nextX, nextY)
					&& (flags[nextX][nextY] & BLOCKED_FROM[i]) == 0
					&& visited.add(next))
				{
					pending.add(next);
				}
			}
		}
		return visited;
	}

	static int pack(int x, int y)
	{
		return x << 16 | y & 0xffff;
	}

	private static boolean isOpen(int[][] flags, int x, int y)
	{
		return x >= 0 && x < flags.length && y >= 0 && y < flags[x].length
			&& (flags[x][y] & CollisionDataFlag.BLOCK_MOVEMENT_FULL) == 0;
	}

	private static boolean isVisible(Widget widget)
	{
		return widget != null && !widget.isHidden();
	}

	private static String text(Widget widget)
	{
		return widget == null || widget.getText() == null ? "" : widget.getText();
	}

	private static int readFirstInt(Widget widget)
	{
		String value = text(widget);
		int result = 0;
		boolean found = false;
		for (int i = 0; i < value.length(); i++)
		{
			char character = value.charAt(i);
			if (Character.isDigit(character))
			{
				found = true;
				result = result * 10 + character - '0';
			}
			else if (found && character != ',')
			{
				break;
			}
		}
		return found ? result : -1;
	}

	private static int[] readPair(Widget widget)
	{
		String[] values = text(widget).split("/", -1);
		if (values.length != 2)
		{
			return new int[]{-1, -1};
		}

		try
		{
			return new int[]{Integer.parseInt(values[0].trim()), Integer.parseInt(values[1].trim())};
		}
		catch (NumberFormatException ex)
		{
			return new int[]{-1, -1};
		}
	}

	private static int readDurationSeconds(Widget widget)
	{
		return isVisible(widget) ? readDurationSeconds(text(widget)) : -1;
	}

	private static int readDurationSeconds(String text)
	{
		String value = text.trim();
		int separator = value.indexOf(':');
		if (separator <= 0 || separator != value.lastIndexOf(':'))
		{
			return -1;
		}

		try
		{
			int minutes = Integer.parseInt(value.substring(0, separator));
			int seconds = Integer.parseInt(value.substring(separator + 1));
			return minutes >= 0 && seconds >= 0 && seconds < 60
				&& minutes <= (Integer.MAX_VALUE - seconds) / 60
				? minutes * 60 + seconds
				: -1;
		}
		catch (NumberFormatException ex)
		{
			return -1;
		}
	}

	private static String readPortalPosition(String status)
	{
		int separator = status.lastIndexOf(" - ");
		return (separator < 0 ? status : status.substring(0, separator)).trim();
	}

	private static int readPortalSeconds(String status)
	{
		int separator = status.lastIndexOf(" - ");
		return separator < 0 ? -1 : readDurationSeconds(status.substring(separator + 3));
	}

	/** Separated mining pocket occupied by the player. */
	public enum MiningArea
	{
		NONE(-1),
		EAST_LARGE_REMAINS(ObjectID.GOTR_ESSENCE_TIER_2),
		WEST_HUGE_REMAINS(ObjectID.GOTR_ESSENCE_TIER_3);

		private final int objectId;

		MiningArea(int objectId)
		{
			this.objectId = objectId;
		}

		private static MiningArea fromClient(Client client, WorldPoint location)
		{
			Set<WorldPoint> east = getMiningAreaTiles(client, EAST_LARGE_REMAINS);
			Set<WorldPoint> west = getMiningAreaTiles(client, WEST_HUGE_REMAINS);
			return fromTiles(location, east, west);
		}

		static MiningArea fromTiles(WorldPoint location, Set<WorldPoint> east, Set<WorldPoint> west)
		{
			if (east.contains(location))
			{
				return EAST_LARGE_REMAINS;
			}
			return west.contains(location) ? WEST_HUGE_REMAINS : NONE;
		}
	}

	/** Charged-cell strength produced by a rune altar. */
	public enum CellTier
	{
		NONE(-1),
		WEAK(ItemID.GOTR_CELL_TIER1),
		MEDIUM(ItemID.GOTR_CELL_TIER2),
		STRONG(ItemID.GOTR_CELL_TIER3),
		OVERCHARGED(ItemID.GOTR_CELL_TIER4);

		/** @return charged-cell item ID, or {@code -1} for {@link #NONE} */
		@Getter
		private final int itemId;

		CellTier(int itemId)
		{
			this.itemId = itemId;
		}

		private static CellTier fromInventory(ItemContainer inventory)
		{
			if (inventory != null)
			{
				for (CellTier tier : values())
				{
					if (tier != NONE && inventory.contains(tier.itemId))
					{
						return tier;
					}
				}
			}
			return NONE;
		}
	}

	/** Client-visible condition and available interaction of a barrier cell tile. */
	public enum BarrierState
	{
		BROKEN_REPAIRABLE(43736, true, true, false),
		BROKEN(43737, true, false, false),
		INACTIVE_NO_OP(43738, false, false, false),
		INACTIVE(43739, false, false, true),
		WEAK(43740, false, false, true),
		MEDIUM(43741, false, false, true),
		STRONG(43742, false, false, true),
		OVERCHARGED(43743, false, false, true);

		/** @return cell-tile ground-object ID */
		@Getter
		private final int objectId;
		/** @return whether the barrier is broken */
		@Getter
		private final boolean broken;
		/** @return whether the cell tile currently offers the Repair interaction */
		@Getter
		private final boolean repairable;
		private final boolean acceptsCell;

		BarrierState(int objectId, boolean broken, boolean repairable, boolean acceptsCell)
		{
			this.objectId = objectId;
			this.broken = broken;
			this.repairable = repairable;
			this.acceptsCell = acceptsCell;
		}

		/** @return whether the cell tile currently offers the Place-cell interaction */
		public boolean acceptsCell()
		{
			return acceptsCell;
		}

		private static BarrierState fromObjectId(int objectId)
		{
			for (BarrierState state : values())
			{
				if (state.objectId == objectId)
				{
					return state;
				}
			}
			return null;
		}
	}

	/** Immutable state for one currently loaded barrier cell tile. */
	public static final class Barrier
	{
		/** @return barrier cell-tile location */
		@Getter
		private final WorldPoint location;
		/** @return client-visible barrier state */
		@Getter
		private final BarrierState state;

		private Barrier(WorldPoint location, BarrierState state)
		{
			this.location = location;
			this.state = state;
		}

		@Override
		public boolean equals(Object object)
		{
			if (this == object)
			{
				return true;
			}
			if (!(object instanceof Barrier))
			{
				return false;
			}
			Barrier barrier = (Barrier) object;
			return location.equals(barrier.location) && state == barrier.state;
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(location, state);
		}

		@Override
		public String toString()
		{
			return state + "@" + location;
		}
	}

	/** Immutable GOTR-relevant inventory state. */
	public static final class InventoryState
	{
		/** @return guardian fragments in inventory */
		@Getter
		private final int guardianFragments;
		/** @return guardian essence in inventory, excluding essence pouches */
		@Getter
		private final int guardianEssence;
		/** @return uncharged cells in inventory */
		@Getter
		private final int unchargedCells;
		/** @return charged-cell tier, or {@link CellTier#NONE} */
		@Getter
		private final CellTier chargedCellTier;
		/** @return catalytic guardian stones in inventory */
		@Getter
		private final int catalyticGuardianStones;
		/** @return elemental guardian stones in inventory */
		@Getter
		private final int elementalGuardianStones;
		/** @return polyelemental guardian stones in inventory */
		@Getter
		private final int polyelementalGuardianStones;
		/** @return polycatalytic guardian stones in inventory */
		@Getter
		private final int polycatalyticGuardianStones;
		/** @return immutable portal-talisman counts keyed by altar */
		@Getter
		private final Map<Altar, Integer> portalTalismans;
		/** @return whether a chisel is in inventory */
		@Getter
		private final boolean chiselPresent;
		/** @return currently empty inventory slots, or zero when inventory is unavailable */
		@Getter
		private final int emptySlots;

		private InventoryState(
			int guardianFragments,
			int guardianEssence,
			int unchargedCells,
			CellTier chargedCellTier,
			int catalyticGuardianStones,
			int elementalGuardianStones,
			int polyelementalGuardianStones,
			int polycatalyticGuardianStones,
			EnumMap<Altar, Integer> portalTalismans,
			boolean chiselPresent,
			int emptySlots)
		{
			this.guardianFragments = guardianFragments;
			this.guardianEssence = guardianEssence;
			this.unchargedCells = unchargedCells;
			this.chargedCellTier = chargedCellTier;
			this.catalyticGuardianStones = catalyticGuardianStones;
			this.elementalGuardianStones = elementalGuardianStones;
			this.polyelementalGuardianStones = polyelementalGuardianStones;
			this.polycatalyticGuardianStones = polycatalyticGuardianStones;
			this.portalTalismans = Collections.unmodifiableMap(portalTalismans);
			this.chiselPresent = chiselPresent;
			this.emptySlots = emptySlots;
		}

		private static InventoryState fromContainer(ItemContainer inventory)
		{
			EnumMap<Altar, Integer> talismans = new EnumMap<>(Altar.class);
			for (Altar altar : Altar.values())
			{
				if (altar != Altar.UNKNOWN)
				{
					talismans.put(altar, count(inventory, altar.portalTalismanItemId));
				}
			}

			return new InventoryState(
				count(inventory, ItemID.GOTR_GUARDIAN_FRAGMENT),
				count(inventory, ItemID.GOTR_GUARDIAN_ESSENCE),
				count(inventory, ItemID.GOTR_CELL_UNCHARGED),
				CellTier.fromInventory(inventory),
				count(inventory, ItemID.GOTR_GUARDIAN_STONE_CATALYTIC),
				count(inventory, ItemID.GOTR_GUARDIAN_STONE_ELEMENTAL),
				count(inventory, ItemID.GOTR_GUARDIAN_STONE_POLYELEMENTAL),
				count(inventory, ItemID.GOTR_GUARDIAN_STONE_POLYCATALYTIC),
				talismans,
				inventory != null && inventory.contains(ItemID.CHISEL),
				countEmptySlots(inventory));
		}

		private static int countEmptySlots(ItemContainer inventory)
		{
			int emptySlots = 0;
			if (inventory != null)
			{
				for (Item item : inventory.getItems())
				{
					emptySlots += item.getId() == -1 ? 1 : 0;
				}
			}
			return emptySlots;
		}

		private static int count(ItemContainer inventory, int itemId)
		{
			return inventory == null ? 0 : inventory.count(itemId);
		}

		/**
		 * Returns the number of portal talismans held for one altar.
		 *
		 * @param altar rune altar
		 * @return portal-talisman count
		 */
		public int getPortalTalismanCount(Altar altar)
		{
			return portalTalismans.getOrDefault(Objects.requireNonNull(altar, "altar"), 0);
		}

		@Override
		public String toString()
		{
			return "fragments=" + guardianFragments
				+ ", essence=" + guardianEssence
				+ ", unchargedCells=" + unchargedCells
				+ ", chargedCell=" + chargedCellTier
				+ ", stones=" + (catalyticGuardianStones + elementalGuardianStones
					+ polyelementalGuardianStones + polycatalyticGuardianStones)
				+ ", talismans=" + portalTalismans
				+ ", chisel=" + chiselPresent
				+ ", emptySlots=" + emptySlots;
		}
	}

	/** Rune altar available in Guardians of the Rift. */
	public enum Altar
	{
		UNKNOWN(-1, false, -1, -1, CellTier.NONE, -1, null),
		AIR(4353, false, 11339, 1, CellTier.WEAK, ItemID.GOTR_PORTAL_TALISMAN_AIR, null),
		MIND(4354, true, 11083, 2, CellTier.WEAK, ItemID.GOTR_PORTAL_TALISMAN_MIND, null),
		WATER(4355, false, 10827, 5, CellTier.MEDIUM, ItemID.GOTR_PORTAL_TALISMAN_WATER, null),
		EARTH(4356, false, 10571, 9, CellTier.STRONG, ItemID.GOTR_PORTAL_TALISMAN_EARTH, null),
		FIRE(4357, false, 10315, 14, CellTier.OVERCHARGED, ItemID.GOTR_PORTAL_TALISMAN_FIRE, null),
		BODY(4358, true, 10059, 20, CellTier.WEAK, ItemID.GOTR_PORTAL_TALISMAN_BODY, null),
		COSMIC(4359, true, 8523, 27, CellTier.MEDIUM, ItemID.GOTR_PORTAL_TALISMAN_COSMIC, Quest.LOST_CITY),
		CHAOS(4360, true, 9035, 35, CellTier.MEDIUM, ItemID.GOTR_PORTAL_TALISMAN_CHAOS, null),
		NATURE(4361, true, 9547, 44, CellTier.STRONG, ItemID.GOTR_PORTAL_TALISMAN_NATURE, null),
		LAW(4362, true, 9803, 54, CellTier.STRONG, ItemID.GOTR_PORTAL_TALISMAN_LAW, Quest.TROLL_STRONGHOLD),
		DEATH(4363, true, 8779, 65, CellTier.OVERCHARGED, ItemID.GOTR_PORTAL_TALISMAN_DEATH, Quest.MOURNINGS_END_PART_II),
		BLOOD(4364, true, 12875, 77, CellTier.OVERCHARGED, ItemID.GOTR_PORTAL_TALISMAN_BLOOD, Quest.SINS_OF_THE_FATHER);

		private final int spriteId;
		/** @return whether this is a catalytic altar */
		@Getter
		private final boolean catalytic;
		private final int regionId;
		/** @return Runecraft level required to imbue essence, or {@code -1} for unknown */
		@Getter
		private final int requiredRunecraftLevel;
		/** @return charged-cell tier produced by this altar */
		@Getter
		private final CellTier cellTier;
		/** @return corresponding portal-talisman item ID, or {@code -1} for unknown */
		@Getter
		private final int portalTalismanItemId;
		/** @return quest required to enter this portal, or {@code null} when none */
		@Getter
		private final Quest requiredQuest;

		Altar(
			int spriteId,
			boolean catalytic,
			int regionId,
			int requiredRunecraftLevel,
			CellTier cellTier,
			int portalTalismanItemId,
			Quest requiredQuest)
		{
			this.spriteId = spriteId;
			this.catalytic = catalytic;
			this.regionId = regionId;
			this.requiredRunecraftLevel = requiredRunecraftLevel;
			this.cellTier = cellTier;
			this.portalTalismanItemId = portalTalismanItemId;
			this.requiredQuest = requiredQuest;
		}

		private static Altar fromWidget(Widget widget, boolean catalytic)
		{
			if (widget != null)
			{
				for (Altar altar : values())
				{
					if (altar != UNKNOWN && altar.catalytic == catalytic
						&& altar.spriteId == widget.getSpriteId())
					{
						return altar;
					}
				}
			}
			return UNKNOWN;
		}

		private static Altar fromRegion(int regionId)
		{
			for (Altar altar : values())
			{
				if (altar.regionId == regionId)
				{
					return altar;
				}
			}
			return UNKNOWN;
		}
	}

	/** Immutable Guardians of the Rift state captured from one client read. */
	public static final class State
	{
		/** @return whether the Guardians of the Rift HUD is visible, including inside rune altars */
		@Getter
		private final boolean hudVisible;
		private final boolean finishedGame;
		/** @return displayed Great Guardian power percentage, or {@code -1} when unavailable */
		@Getter
		private final int guardianPower;
		/** @return seconds until the active altar pair rotates, or {@code -1} when unavailable */
		@Getter
		private final int altarRotationSecondsRemaining;
		/** @return elemental energy earned in the current game */
		@Getter
		private final int elementalEnergy;
		/** @return catalytic energy earned in the current game */
		@Getter
		private final int catalyticEnergy;
		/** @return active elemental altar, or {@link Altar#UNKNOWN} */
		@Getter
		private final Altar elementalAltar;
		/** @return active catalytic altar, or {@link Altar#UNKNOWN} */
		@Getter
		private final Altar catalyticAltar;
		/** @return active rift guardians, or {@code -1} when unavailable */
		@Getter
		private final int activeGuardians;
		/** @return maximum active rift guardians, or {@code -1} when unavailable */
		@Getter
		private final int guardianLimit;
		/** @return whether the portal to the huge guardian remains is displayed as open */
		@Getter
		private final boolean guardianEssencePortalOpen;
		/** @return displayed portal compass position, or an empty string while closed */
		@Getter
		private final String guardianEssencePortalPosition;
		/** @return seconds until the huge-remains portal closes, or {@code -1} when unavailable */
		@Getter
		private final int guardianEssencePortalSecondsRemaining;
		/** @return whether the player is in the main Temple of the Eye minigame region */
		@Getter
		private final boolean inMainTemple;
		/** @return whether the player is north of the entry barrier in the playable arena */
		@Getter
		private final boolean inArena;
		/** @return rune altar the player currently occupies, or {@link Altar#UNKNOWN} */
		@Getter
		private final Altar currentAltar;
		/** @return separated east or west mining pocket occupied by the player */
		@Getter
		private final MiningArea miningArea;
		/** @return immutable GOTR-relevant inventory state */
		@Getter
		private final InventoryState inventory;
		/** @return immutable set of currently loaded barrier cell tiles */
		@Getter
		private final Set<Barrier> barriers;

		private State(
			boolean hudVisible,
			boolean finishedGame,
			int guardianPower,
			int altarRotationSecondsRemaining,
			int elementalEnergy,
			int catalyticEnergy,
			Altar elementalAltar,
			Altar catalyticAltar,
			int activeGuardians,
			int guardianLimit,
			boolean guardianEssencePortalOpen,
			String guardianEssencePortalPosition,
			int guardianEssencePortalSecondsRemaining,
			boolean inMainTemple,
			boolean inArena,
			Altar currentAltar,
			MiningArea miningArea,
			InventoryState inventory,
			Set<Barrier> barriers)
		{
			this.hudVisible = hudVisible;
			this.finishedGame = finishedGame;
			this.guardianPower = guardianPower;
			this.altarRotationSecondsRemaining = altarRotationSecondsRemaining;
			this.elementalEnergy = elementalEnergy;
			this.catalyticEnergy = catalyticEnergy;
			this.elementalAltar = elementalAltar;
			this.catalyticAltar = catalyticAltar;
			this.activeGuardians = activeGuardians;
			this.guardianLimit = guardianLimit;
			this.guardianEssencePortalOpen = guardianEssencePortalOpen;
			this.guardianEssencePortalPosition = guardianEssencePortalPosition;
			this.guardianEssencePortalSecondsRemaining = guardianEssencePortalSecondsRemaining;
			this.inMainTemple = inMainTemple;
			this.inArena = inArena;
			this.currentAltar = currentAltar;
			this.miningArea = miningArea;
			this.inventory = inventory;
			this.barriers = barriers;
		}

		/** @return whether a round is active, excluding the lobby period between rounds */
		public boolean isRoundActive()
		{
			return guardianPower > 0 && guardianPower < 100;
		}

		/** @return the game's raw flag indicating that the player finished the current game */
		public boolean hasFinishedGame()
		{
			return finishedGame;
		}

		/** @return total elemental and catalytic energy earned in the current game */
		public int getTotalEnergy()
		{
			return elementalEnergy + catalyticEnergy;
		}

		/** @return whether the player has earned the 300 combined energy required for rewards */
		public boolean isRewardEligible()
		{
			return getTotalEnergy() >= REWARD_ENERGY_REQUIREMENT;
		}

		/** @return whether the player is south of the entry barrier in the temple lobby */
		public boolean isInLobby()
		{
			return inMainTemple && !inArena;
		}

	}
}
