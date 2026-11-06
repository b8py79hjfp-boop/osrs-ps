package tp.runelitemcp;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.gameval.InterfaceID;

/**
 * Reverse lookup of interface group / component names from RuneLite's generated
 * {@link InterfaceID} constants (reflection, built once on first use).
 */
final class GameNames
{
	private static Map<Integer, String> groups;
	private static Map<Integer, String> components;

	private GameNames()
	{
	}

	/** Name of an interface group id, e.g. {@code BANKMAIN}, or {@code null} if unknown. */
	static synchronized String group(int groupId)
	{
		init();
		return groups.get(groupId);
	}

	/** Name of a packed component id, e.g. {@code Bankmain.ITEMS}, or {@code null} if unknown. */
	static synchronized String component(int packedId)
	{
		init();
		return components.get(packedId);
	}

	private static void init()
	{
		if (groups != null)
		{
			return;
		}
		groups = new HashMap<>();
		components = new HashMap<>();
		try
		{
			for (Field f : InterfaceID.class.getDeclaredFields())
			{
				if (isIntConstant(f))
				{
					groups.putIfAbsent(f.getInt(null), f.getName());
				}
			}
			for (Class<?> nested : InterfaceID.class.getDeclaredClasses())
			{
				for (Field f : nested.getDeclaredFields())
				{
					if (isIntConstant(f))
					{
						components.putIfAbsent(f.getInt(null), nested.getSimpleName() + "." + f.getName());
					}
				}
			}
		}
		catch (ReflectiveOperationException | RuntimeException e)
		{
			// names are a convenience only
		}
	}

	private static boolean isIntConstant(Field f)
	{
		int m = f.getModifiers();
		return f.getType() == int.class && Modifier.isStatic(m) && Modifier.isFinal(m) && Modifier.isPublic(m);
	}
}
