package net.runelite.client.plugins.m8aq.api.player;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.Skill;

/** Provides a read-only snapshot of the local player's skill progression. */
public final class Skills
{
	private Skills()
	{
	}

	/**
	 * Reads the current skill state from the client.
	 * This method must be called on the client thread.
	 *
	 * @param client RuneLite client
	 * @return immutable skill snapshot
	 */
	public static State getState(Client client)
	{
		Objects.requireNonNull(client, "client");
		if (client.getLocalPlayer() == null)
		{
			return new State(false, Collections.emptyMap(), 0, 0L);
		}

		Map<Skill, SkillState> skills = new EnumMap<>(Skill.class);
		long totalExperience = 0L;
		Skill[] values = Skill.values();
		for (Skill skill : values)
		{
			int experience = client.getSkillExperience(skill);
			skills.put(skill, new SkillState(
				client.getRealSkillLevel(skill),
				client.getBoostedSkillLevel(skill),
				experience));
			totalExperience += experience;
		}
		return new State(
			true,
			Collections.unmodifiableMap(skills),
			client.getTotalLevel(),
			totalExperience);
	}

	/** Immutable progression state for one trainable skill. */
	public static final class SkillState
	{
		/** @return persistent base level */
		@Getter
		private final int realLevel;
		/** @return current level after boosts or drains */
		@Getter
		private final int boostedLevel;
		/** @return total experience in the skill */
		@Getter
		private final int experience;

		private SkillState(int realLevel, int boostedLevel, int experience)
		{
			this.realLevel = realLevel;
			this.boostedLevel = boostedLevel;
			this.experience = experience;
		}

		/** @return boosted level minus real level */
		public int getBoostDelta()
		{
			return boostedLevel - realLevel;
		}
	}

	/** Immutable all-skill state captured from one client read. */
	public static final class State
	{
		/** @return whether logged-in skill state was available */
		@Getter
		private final boolean available;
		/** @return immutable state for every trainable RuneLite skill */
		@Getter
		private final Map<Skill, SkillState> skills;
		/** @return total real level reported by the client */
		@Getter
		private final int totalLevel;
		/** @return sum of experience across every trainable skill */
		@Getter
		private final long totalExperience;

		private State(
			boolean available,
			Map<Skill, SkillState> skills,
			int totalLevel,
			long totalExperience)
		{
			this.available = available;
			this.skills = skills;
			this.totalLevel = totalLevel;
			this.totalExperience = totalExperience;
		}

		/**
		 * Returns one trainable skill state.
		 *
		 * @param skill RuneLite skill
		 * @return immutable skill state, or {@code null} for null/non-trainable skills
		 */
		public SkillState getSkill(Skill skill)
		{
			return skill == null ? null : skills.get(skill);
		}
	}
}
