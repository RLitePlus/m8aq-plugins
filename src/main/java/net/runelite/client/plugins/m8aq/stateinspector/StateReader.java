package net.runelite.client.plugins.m8aq.stateinspector;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.api.Client;

final class StateReader
{
	private static final Gson GSON = new GsonBuilder().serializeNulls().create();

	private StateReader()
	{
	}

	static Map<String, String> read(Class<?> apiClass, Client client) throws ReflectiveOperationException
	{
		Method getState = apiClass.getMethod("getState", Client.class);
		if (!Modifier.isStatic(getState.getModifiers()))
		{
			throw new IllegalArgumentException(apiClass.getName() + ".getState(Client) must be static");
		}

		Object state = getState.invoke(null, client);
		if (state == null)
		{
			return Collections.singletonMap("state", "null");
		}

		Map<String, String> values = new LinkedHashMap<>();
		Arrays.stream(state.getClass().getMethods())
			.filter(StateReader::isAccessor)
			.sorted((left, right) -> left.getName().compareTo(right.getName()))
			.forEach(method -> values.put(method.getName(), invoke(method, state)));
		return Collections.unmodifiableMap(values);
	}

	private static boolean isAccessor(Method method)
	{
		String name = method.getName();
		return method.getParameterCount() == 0
			&& !name.equals("getClass")
			&& (name.startsWith("get") || name.startsWith("is")
				|| name.startsWith("can") || name.startsWith("needs"));
	}

	private static String invoke(Method method, Object state)
	{
		try
		{
			return format(method.invoke(state));
		}
		catch (IllegalAccessException ex)
		{
			return error(ex);
		}
		catch (InvocationTargetException ex)
		{
			return error(ex.getCause());
		}
	}

	private static String format(Object value)
	{
		return value == null || value instanceof Number || value instanceof Boolean
			|| value instanceof CharSequence || value instanceof Character || value.getClass().isEnum()
			? String.valueOf(value)
			: GSON.toJson(value);
	}

	private static String error(Throwable throwable)
	{
		String message = throwable.getMessage();
		return "<" + throwable.getClass().getSimpleName()
			+ (message == null ? "" : ": " + message) + ">";
	}
}
