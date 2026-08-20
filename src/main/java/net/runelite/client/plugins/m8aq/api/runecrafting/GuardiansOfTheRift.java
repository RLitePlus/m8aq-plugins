package net.runelite.client.plugins.m8aq.api.runecrafting;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.GameObject;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
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
			inMainTemple ? MiningArea.fromClient(client, location) : MiningArea.NONE);
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

	/** Rune altar available in Guardians of the Rift. */
	public enum Altar
	{
		UNKNOWN(-1, false, -1),
		AIR(4353, false, 11339),
		MIND(4354, true, 11083),
		WATER(4355, false, 10827),
		EARTH(4356, false, 10571),
		FIRE(4357, false, 10315),
		BODY(4358, true, 10059),
		COSMIC(4359, true, 8523),
		CHAOS(4360, true, 9035),
		NATURE(4361, true, 9547),
		LAW(4362, true, 9803),
		DEATH(4363, true, 8779),
		BLOOD(4364, true, 12875);

		private final int spriteId;
		private final boolean catalytic;
		private final int regionId;

		Altar(int spriteId, boolean catalytic, int regionId)
		{
			this.spriteId = spriteId;
			this.catalytic = catalytic;
			this.regionId = regionId;
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
		private final boolean hudVisible;
		private final boolean finishedGame;
		private final int guardianPower;
		private final int altarRotationSecondsRemaining;
		private final int elementalEnergy;
		private final int catalyticEnergy;
		private final Altar elementalAltar;
		private final Altar catalyticAltar;
		private final int activeGuardians;
		private final int guardianLimit;
		private final boolean guardianEssencePortalOpen;
		private final String guardianEssencePortalPosition;
		private final int guardianEssencePortalSecondsRemaining;
		private final boolean inMainTemple;
		private final boolean inArena;
		private final Altar currentAltar;
		private final MiningArea miningArea;

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
			MiningArea miningArea)
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
		}

		/** @return whether the Guardians of the Rift HUD is visible, including inside rune altars */
		public boolean isHudVisible()
		{
			return hudVisible;
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

		/** @return displayed Great Guardian power percentage, or {@code -1} when unavailable */
		public int getGuardianPower()
		{
			return guardianPower;
		}

		/** @return seconds until the active altar pair rotates, or {@code -1} when unavailable */
		public int getAltarRotationSecondsRemaining()
		{
			return altarRotationSecondsRemaining;
		}

		/** @return elemental energy earned in the current game */
		public int getElementalEnergy()
		{
			return elementalEnergy;
		}

		/** @return catalytic energy earned in the current game */
		public int getCatalyticEnergy()
		{
			return catalyticEnergy;
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

		/** @return active elemental altar, or {@link Altar#UNKNOWN} */
		public Altar getElementalAltar()
		{
			return elementalAltar;
		}

		/** @return active catalytic altar, or {@link Altar#UNKNOWN} */
		public Altar getCatalyticAltar()
		{
			return catalyticAltar;
		}

		/** @return rune altar the player currently occupies, or {@link Altar#UNKNOWN} */
		public Altar getCurrentAltar()
		{
			return currentAltar;
		}

		/** @return active rift guardians, or {@code -1} when unavailable */
		public int getActiveGuardians()
		{
			return activeGuardians;
		}

		/** @return maximum active rift guardians, or {@code -1} when unavailable */
		public int getGuardianLimit()
		{
			return guardianLimit;
		}

		/** @return whether the portal to the huge guardian remains is displayed as open */
		public boolean isGuardianEssencePortalOpen()
		{
			return guardianEssencePortalOpen;
		}

		/** @return displayed portal compass position, or an empty string while closed */
		public String getGuardianEssencePortalPosition()
		{
			return guardianEssencePortalPosition;
		}

		/** @return seconds until the huge-remains portal closes, or {@code -1} when unavailable */
		public int getGuardianEssencePortalSecondsRemaining()
		{
			return guardianEssencePortalSecondsRemaining;
		}

		/** @return whether the player is in the main Temple of the Eye minigame region */
		public boolean isInMainTemple()
		{
			return inMainTemple;
		}

		/** @return whether the player is south of the entry barrier in the temple lobby */
		public boolean isInLobby()
		{
			return inMainTemple && !inArena;
		}

		/** @return whether the player is north of the entry barrier in the playable arena */
		public boolean isInArena()
		{
			return inArena;
		}

		/** @return separated east or west mining pocket occupied by the player */
		public MiningArea getMiningArea()
		{
			return miningArea;
		}
	}
}
