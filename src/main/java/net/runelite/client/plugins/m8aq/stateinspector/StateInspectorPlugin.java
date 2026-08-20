package net.runelite.client.plugins.m8aq.stateinspector;

import com.google.common.reflect.ClassPath;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

/** Provides a local sidebar for exercising snapshot-style RuneLite API classes. */
@PluginDescriptor(
	name = "State Inspector",
	description = "Reads local API snapshots for development"
)
public final class StateInspectorPlugin extends Plugin
{
	private static final String API_PACKAGE = "net.runelite.client.plugins.m8aq.api";
	private static final List<Class<?>> API_CLASSES = discoverApiClasses(
		StateInspectorPlugin.class.getClassLoader());

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	private StateInspectorPanel panel;
	private NavigationButton navigationButton;

	static List<Class<?>> discoverApiClasses(ClassLoader classLoader)
	{
		try
		{
			return ClassPath.from(classLoader)
				.getTopLevelClassesRecursive(API_PACKAGE)
				.stream()
				.map(ClassPath.ClassInfo::load)
				.filter(StateInspectorPlugin::isApiClass)
				.sorted(Comparator.comparing(Class::getName))
				.collect(Collectors.toUnmodifiableList());
		}
		catch (IOException ex)
		{
			throw new IllegalStateException("Unable to discover API classes", ex);
		}
	}

	private static boolean isApiClass(Class<?> type)
	{
		if (!Modifier.isPublic(type.getModifiers()))
		{
			return false;
		}

		try
		{
			Method getState = type.getMethod("getState", Client.class);
			return Modifier.isStatic(getState.getModifiers())
				&& getState.getReturnType() != Void.TYPE;
		}
		catch (NoSuchMethodException ex)
		{
			return false;
		}
	}

	@Override
	protected void startUp()
	{
		panel = new StateInspectorPanel(API_CLASSES, this::refresh);
		navigationButton = NavigationButton.builder()
			.tooltip("State Inspector")
			.icon(createIcon())
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navigationButton);
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navigationButton);
		panel.dispose();
		panel = null;
		navigationButton = null;
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		StateInspectorPanel currentPanel = panel;
		if (currentPanel != null && currentPanel.isActive())
		{
			refresh(currentPanel.selectedClass());
		}
	}

	private void refresh(Class<?> apiClass)
	{
		StateInspectorPanel currentPanel = panel;
		clientThread.invoke(() ->
		{
			try
			{
				Map<String, String> values = StateReader.read(apiClass, client);
				SwingUtilities.invokeLater(() -> showValues(currentPanel, values));
			}
			catch (ReflectiveOperationException | RuntimeException ex)
			{
				SwingUtilities.invokeLater(() -> showError(currentPanel, ex));
			}
		});
	}

	private void showValues(StateInspectorPanel target, Map<String, String> values)
	{
		if (panel == target)
		{
			target.showValues(values);
		}
	}

	private void showError(StateInspectorPanel target, Throwable throwable)
	{
		if (panel == target)
		{
			target.showError(throwable);
		}
	}

	private static BufferedImage createIcon()
	{
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setColor(Color.WHITE);
		graphics.setStroke(new BasicStroke(2));
		graphics.drawOval(1, 1, 9, 9);
		graphics.drawLine(9, 9, 14, 14);
		graphics.dispose();
		return image;
	}
}
