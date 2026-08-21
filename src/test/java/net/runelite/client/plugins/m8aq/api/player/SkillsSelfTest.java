package net.runelite.client.plugins.m8aq.api.player;

import java.lang.reflect.Proxy;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Skill;

/** Minimal runnable checks for {@link Skills}. */
public final class SkillsSelfTest
{
	private SkillsSelfTest()
	{
	}

	/** Runs the checks with Java assertions enabled. */
	public static void main(String[] args)
	{
		Skills.State state = Skills.getState(client(true));
		assert state.isAvailable();
		assert state.getSkills().size() == Skill.values().length;
		assert state.getSkills().containsKey(Skill.SAILING);
		assert state.getTotalLevel() == 1500;
		assert state.getTotalExperience() == 2_200_000_579L;

		Skills.SkillState attack = state.getSkill(Skill.ATTACK);
		assert attack.getRealLevel() == 10;
		assert attack.getBoostedLevel() == 12;
		assert attack.getExperience() == 123;
		assert attack.getBoostDelta() == 2;
		assert state.getSkill(Skill.DEFENCE).getBoostDelta() == -2;
		assert state.getSkill(null) == null;
		assertUnmodifiable(state.getSkills());

		Skills.State unavailable = Skills.getState(client(false));
		assert !unavailable.isAvailable();
		assert unavailable.getSkills().isEmpty();
		assert unavailable.getTotalLevel() == 0;
		assert unavailable.getTotalExperience() == 0L;
	}

	private static void assertUnmodifiable(Map<Skill, Skills.SkillState> skills)
	{
		try
		{
			skills.clear();
			assert false;
		}
		catch (UnsupportedOperationException expected)
		{
			// Expected.
		}
	}

	private static Client client(boolean loggedIn)
	{
		Player player = loggedIn ? proxy(Player.class, (method, args) -> null) : null;
		return proxy(Client.class, (method, args) ->
		{
			switch (method)
			{
				case "getLocalPlayer":
					return player;
				case "getRealSkillLevel":
					return level((Skill) args[0], false);
				case "getBoostedSkillLevel":
					return level((Skill) args[0], true);
				case "getSkillExperience":
					return experience((Skill) args[0]);
				case "getTotalLevel":
					return 1500;
				default:
					return null;
			}
		});
	}

	private static int level(Skill skill, boolean boosted)
	{
		if (skill == Skill.ATTACK)
		{
			return boosted ? 12 : 10;
		}
		if (skill == Skill.DEFENCE)
		{
			return boosted ? 18 : 20;
		}
		return 50;
	}

	private static int experience(Skill skill)
	{
		if (skill == Skill.ATTACK)
		{
			return 123;
		}
		if (skill == Skill.DEFENCE)
		{
			return 456;
		}
		return 100_000_000;
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
