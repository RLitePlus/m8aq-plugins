package net.runelite.client.plugins.hotreload;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.invoke.MethodHandles;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import javax.swing.SwingUtilities;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.ExternalPluginsChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.util.ReflectUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Replaces one generation of development plugins with the next rebuilt JAR. */
final class HotreloadService implements AutoCloseable
{
	private static final Logger LOG = LoggerFactory.getLogger(HotreloadService.class);
	private static final long POLL_MILLIS = 750L;

	private final Path devJar;
	private final PluginManager pluginManager;
	private final EventBus eventBus;
	private final ClassLoader parent;

	private volatile boolean running;
	private long lastModified;
	private Thread watcher;
	private URLClassLoader currentLoader;
	private List<Plugin> currentPlugins = Collections.emptyList();

	HotreloadService(Path devJar, PluginManager pluginManager, EventBus eventBus, ClassLoader parent)
	{
		this.devJar = devJar;
		this.pluginManager = pluginManager;
		this.eventBus = eventBus;
		this.parent = parent;
	}

	void start()
	{
		running = true;
		watcher = new Thread(this::watch, "hotreload-watch");
		watcher.setDaemon(true);
		watcher.start();
	}

	private void watch()
	{
		while (running && !Thread.currentThread().isInterrupted())
		{
			try
			{
				Thread.sleep(POLL_MILLIS);
				if (!Files.isRegularFile(devJar))
				{
					continue;
				}

				long modified = Files.getLastModifiedTime(devJar).toMillis();
				if (modified == lastModified)
				{
					continue;
				}

				Thread.sleep(POLL_MILLIS);
				if (Files.getLastModifiedTime(devJar).toMillis() != modified)
				{
					continue;
				}

				lastModified = modified;
				reload();
			}
			catch (InterruptedException ex)
			{
				Thread.currentThread().interrupt();
				return;
			}
			catch (Exception | LinkageError ex)
			{
				LOG.warn("Reload failed; keeping the current generation", ex);
			}
		}
	}

	private void reload() throws Exception
	{
		Candidate candidate = openCandidate();
		try
		{
			onEventDispatchThread(() -> install(candidate));
		}
		catch (Exception | LinkageError ex)
		{
			candidate.loader.close();
			throw ex;
		}
	}

	private Candidate openCandidate() throws IOException, ClassNotFoundException
	{
		HotreloadClassLoader loader = new HotreloadClassLoader(
			new URL[]{devJar.toUri().toURL()}, parent);
		try
		{
			List<Class<?>> plugins = new ArrayList<>();
			try (JarFile jar = new JarFile(devJar.toFile()))
			{
				Enumeration<JarEntry> entries = jar.entries();
				while (entries.hasMoreElements())
				{
					String name = entries.nextElement().getName();
					if (!name.endsWith(".class") || name.startsWith("META-INF/")
						|| name.equals("module-info.class"))
					{
						continue;
					}

					Class<?> type = Class.forName(
						name.substring(0, name.length() - 6).replace('/', '.'), false, loader);
					if (isPluginClass(type))
					{
						if (type.getClassLoader() != loader)
						{
							throw new IllegalStateException(type.getName()
								+ " is already on the application classpath");
						}
						plugins.add(type);
					}
				}
			}

			plugins.sort(Comparator.comparing(Class::getName));
			if (plugins.isEmpty())
			{
				throw new IllegalStateException("No @PluginDescriptor classes found in " + devJar);
			}
			return new Candidate(loader, plugins);
		}
		catch (IOException | ClassNotFoundException | RuntimeException | LinkageError ex)
		{
			loader.close();
			throw ex;
		}
	}

	static boolean isPluginClass(Class<?> type)
	{
		return type.getSuperclass() == Plugin.class
			&& type.isAnnotationPresent(PluginDescriptor.class)
			&& !Modifier.isAbstract(type.getModifiers());
	}

	private void install(Candidate candidate) throws Exception
	{
		if (!running)
		{
			candidate.loader.close();
			return;
		}

		stopCurrent();
		List<Plugin> incoming = pluginManager.loadPlugins(candidate.plugins, null);
		try
		{
			pluginManager.loadDefaultPluginConfiguration(incoming);
			for (Plugin plugin : incoming)
			{
				if (pluginManager.isPluginEnabled(plugin))
				{
					pluginManager.startPlugin(plugin);
				}
			}
		}
		catch (Exception ex)
		{
			stopPlugins(incoming);
			throw ex;
		}

		currentLoader = candidate.loader;
		currentPlugins = incoming;
		eventBus.post(new ExternalPluginsChanged());
		LOG.info("Reloaded {} plugin(s) from {}", incoming.size(), devJar.getFileName());
	}

	private void stopCurrent()
	{
		stopPlugins(currentPlugins);
		currentPlugins = Collections.emptyList();
		if (currentLoader != null)
		{
			try
			{
				currentLoader.close();
			}
			catch (IOException ex)
			{
				LOG.warn("Could not close the previous plugin classloader", ex);
			}
			currentLoader = null;
		}
	}

	private void stopPlugins(List<Plugin> plugins)
	{
		List<Plugin> reversed = new ArrayList<>(plugins);
		Collections.reverse(reversed);
		for (Plugin plugin : reversed)
		{
			try
			{
				pluginManager.stopPlugin(plugin);
			}
			catch (Exception ex)
			{
				LOG.warn("Could not stop {} cleanly", plugin.getClass().getName(), ex);
			}
			pluginManager.remove(plugin);
		}
	}

	private static void onEventDispatchThread(ThrowingRunnable task) throws Exception
	{
		if (SwingUtilities.isEventDispatchThread())
		{
			task.run();
			return;
		}

		try
		{
			SwingUtilities.invokeAndWait(() ->
			{
				try
				{
					task.run();
				}
				catch (Exception ex)
				{
					throw new ReloadException(ex);
				}
			});
		}
		catch (InvocationTargetException ex)
		{
			Throwable cause = ex.getCause();
			if (cause instanceof ReloadException)
			{
				throw (Exception) cause.getCause();
			}
			if (cause instanceof RuntimeException)
			{
				throw (RuntimeException) cause;
			}
			if (cause instanceof Error)
			{
				throw (Error) cause;
			}
			throw new IllegalStateException(cause);
		}
	}

	@Override
	public void close()
	{
		running = false;
		if (watcher != null)
		{
			watcher.interrupt();
			watcher = null;
		}

		try
		{
			onEventDispatchThread(this::stopCurrent);
		}
		catch (Exception ex)
		{
			LOG.warn("Could not unload the current plugin generation", ex);
		}
	}

	@FunctionalInterface
	private interface ThrowingRunnable
	{
		void run() throws Exception;
	}

	private static final class Candidate
	{
		private final URLClassLoader loader;
		private final List<Class<?>> plugins;

		private Candidate(URLClassLoader loader, List<Class<?>> plugins)
		{
			this.loader = loader;
			this.plugins = plugins;
		}
	}

	private static final class HotreloadClassLoader extends URLClassLoader
		implements ReflectUtil.PrivateLookupableClassLoader
	{
		private MethodHandles.Lookup lookup;

		private HotreloadClassLoader(URL[] urls, ClassLoader parent)
		{
			super(urls, parent);
			ReflectUtil.installLookupHelper(this);
		}

		@Override
		public Class<?> defineClass0(String name, byte[] bytes, int offset, int length)
			throws ClassFormatError
		{
			return super.defineClass(name, bytes, offset, length);
		}

		@Override
		public MethodHandles.Lookup getLookup()
		{
			return lookup;
		}

		@Override
		public void setLookup(MethodHandles.Lookup lookup)
		{
			this.lookup = lookup;
		}
	}

	private static final class ReloadException extends RuntimeException
	{
		private static final long serialVersionUID = 1L;

		private ReloadException(Exception cause)
		{
			super(cause);
		}
	}
}
