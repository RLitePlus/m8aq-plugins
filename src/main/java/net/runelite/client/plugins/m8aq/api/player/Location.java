package net.runelite.client.plugins.m8aq.api.player;

import java.util.Objects;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

/** Provides a read-only snapshot of the local player's coordinates. */
public final class Location
{
	private Location()
	{
	}

	/**
	 * Reads the current local-player location from the client.
	 * This method must be called on the client thread.
	 *
	 * @param client RuneLite client
	 * @return immutable location snapshot
	 */
	public static State getState(Client client)
	{
		Objects.requireNonNull(client, "client");
		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return unavailable();
		}

		WorldPoint worldPoint = player.getWorldLocation();
		LocalPoint localPoint = player.getLocalLocation();
		WorldView worldView = player.getWorldView();
		if (worldPoint == null || localPoint == null || worldView == null)
		{
			return unavailable();
		}

		return new State(
			true,
			worldPoint,
			localPoint,
			worldPoint.getPlane(),
			worldPoint.getRegionID(),
			worldView.isInstance(),
			worldView.getId());
	}

	private static State unavailable()
	{
		return new State(false, null, null, -1, -1, false, -1);
	}

	/** Immutable local-player location captured from one client read. */
	public static final class State
	{
		/** @return whether complete coordinate state was available */
		@Getter
		private final boolean available;
		/** @return server/world coordinate, or {@code null} when unavailable */
		@Getter
		private final WorldPoint worldPoint;
		/** @return rendered local coordinate, or {@code null} when unavailable */
		@Getter
		private final LocalPoint localPoint;
		/** @return current plane, or {@code -1} when unavailable */
		@Getter
		private final int plane;
		/** @return map-region ID, or {@code -1} when unavailable */
		@Getter
		private final int regionId;
		/** @return whether the actor's world view is instanced */
		@Getter
		private final boolean instanced;
		/** @return actor world-view ID, or {@code -1} when unavailable */
		@Getter
		private final int worldViewId;

		private State(
			boolean available,
			WorldPoint worldPoint,
			LocalPoint localPoint,
			int plane,
			int regionId,
			boolean instanced,
			int worldViewId)
		{
			this.available = available;
			this.worldPoint = worldPoint;
			this.localPoint = localPoint;
			this.plane = plane;
			this.regionId = regionId;
			this.instanced = instanced;
			this.worldViewId = worldViewId;
		}
	}
}
