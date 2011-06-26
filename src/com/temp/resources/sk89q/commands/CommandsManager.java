// $Id$
/*
 * WorldEdit
 * Copyright (C) 2010 sk89q <http://www.sk89q.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.temp.resources.sk89q.commands;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.entity.Player;

import com.temp.NPCs.NPCManager;
import com.temp.Utils.MessageUtils;
import com.temp.Utils.Messaging;
import com.temp.resources.redecouverte.NPClib.HumanNPC;

/**
 * <p>
 * Manager for handling commands. This allows you to easily process commands,
 * including nested commands, by correctly annotating methods of a class.
 * </p>
 * 
 * <p>
 * To use this, it is merely a matter of registering classes containing the
 * commands (as methods with the proper annotations) with the manager. When you
 * want to process a command, use one of the <code>execute</code> methods. If
 * something is wrong, such as incorrect usage, insufficient permissions, or a
 * missing command altogether, an exception will be raised for upstream
 * handling.
 * </p>
 * 
 * <p>
 * Methods of a class to be registered can be static, but if an injector is
 * registered with the class, the instances of the command classes will be
 * created automatically and methods will be called non-statically.
 * </p>
 * 
 * <p>
 * To mark a method as a command, use {@link Command}. For nested commands, see
 * {@link NestedCommand}. To handle permissions, use {@link CommandPermissions}.
 * </p>
 * 
 * <p>
 * This uses Java reflection extensively, but to reduce the overhead of
 * reflection, command lookups are completely cached on registration. This
 * allows for fast command handling. Method invocation still has to be done with
 * reflection, but this is quite fast in that of itself.
 * </p>
 * 
 * @author sk89q
 * @param <T>
 *            command sender class
 */
public abstract class CommandsManager<T extends Player> {

	/**
	 * Logger for general errors.
	 */
	protected static final Logger logger = Logger
			.getLogger(CommandsManager.class.getCanonicalName());

	/**
	 * Mapping of commands (including aliases) with a description. Root commands
	 * are stored under a key of null, whereas child commands are cached under
	 * their respective {@link Method}. The child map has the key of the command
	 * name (one for each alias) with the method.
	 */
	protected Map<Method, Map<CommandIdentifier, Method>> commands = new HashMap<Method, Map<CommandIdentifier, Method>>();

	/**
	 * Used to store the instances associated with a method.
	 */
	protected Map<Method, Object> instances = new HashMap<Method, Object>();

	/**
	 * Mapping of commands (not including aliases) with a description. This is
	 * only for top level commands.
	 */
	protected Map<CommandIdentifier, String> descs = new HashMap<CommandIdentifier, String>();

	/**
	 * Stores the injector used to getInstance.
	 */
	protected Injector injector;

	/**
	 * Register an class that contains commands (denoted by {@link Command}. If
	 * no dependency injector is specified, then the methods of the class will
	 * be registered to be called statically. Otherwise, new instances will be
	 * created of the command classes and methods will not be called statically.
	 * 
	 * @param cls
	 */
	public void register(Class<?> cls) {
		registerMethods(cls, null);
	}

	/**
	 * Register the methods of a class. This will automatically construct
	 * instances as necessary.
	 * 
	 * @param cls
	 * @param parent
	 */
	private void registerMethods(Class<?> cls, Method parent) {
		try {
			if (getInjector() == null) {
				registerMethods(cls, parent, null);
			} else {
				Object obj = null;
				obj = getInjector().getInstance(cls);
				registerMethods(cls, parent, obj);
			}
		} catch (InvocationTargetException e) {
			logger.log(Level.SEVERE, "Failed to register commands", e);
		} catch (IllegalAccessException e) {
			logger.log(Level.SEVERE, "Failed to register commands", e);
		} catch (InstantiationException e) {
			logger.log(Level.SEVERE, "Failed to register commands", e);
		}
	}

	/**
	 * Register the methods of a class.
	 * 
	 * @param cls
	 * @param parent
	 */
	private void registerMethods(Class<?> cls, Method parent, Object obj) {
		Map<CommandIdentifier, Method> map;

		// Make a new hash map to cache the commands for this class
		// as looking up methods via reflection is fairly slow
		if (commands.containsKey(parent)) {
			map = commands.get(parent);
		} else {
			map = new HashMap<CommandIdentifier, Method>();
			commands.put(parent, map);
		}

		for (Method method : cls.getMethods()) {
			if (!method.isAnnotationPresent(Command.class)) {
				continue;
			}
			boolean isStatic = Modifier.isStatic(method.getModifiers());

			Command cmd = method.getAnnotation(Command.class);
			String[] modifiers = cmd.modifiers();

			// Cache the aliases too
			for (String alias : cmd.aliases()) {
				for (String modifier : modifiers) {
					map.put(new CommandIdentifier(alias, modifier), method);
				}
			}

			// We want to be able invoke with an instance
			if (!isStatic) {
				// Can't register this command if we don't have an instance
				if (obj == null) {
					continue;
				}

				instances.put(method, obj);
				Messaging.log("Put instance.");
			}

			// Build a list of commands and their usage details, at least for
			// root level commands
			if (parent == null) {
				if (cmd.usage().length() == 0) {
					descs.put(
							new CommandIdentifier(cmd.aliases()[0], cmd
									.modifiers()[0]), cmd.desc());
				} else {
					descs.put(
							new CommandIdentifier(cmd.aliases()[0], cmd
									.modifiers()[0]),
							cmd.usage() + " - " + cmd.desc());
				}
			}

			// Look for nested commands -- if there are any, those have
			// to be cached too so that they can be quickly looked
			// up when processing commands
			if (method.isAnnotationPresent(NestedCommand.class)) {
				NestedCommand nestedCmd = method
						.getAnnotation(NestedCommand.class);

				for (Class<?> nestedCls : nestedCmd.value()) {
					registerMethods(nestedCls, method);
				}
			}
		}
	}

	/**
	 * Checks to see whether there is a command named such at the root level.
	 * This will check aliases as well.
	 * 
	 * @param command
	 * @return
	 */
	public boolean hasCommand(String command, String modifier) {
		return commands.get(null).containsKey(
				new CommandIdentifier(command.toLowerCase(), modifier
						.toLowerCase()));
	}

	/**
	 * Get a list of command descriptions. This is only for root commands.
	 * 
	 * @return
	 */
	public Map<CommandIdentifier, String> getCommands() {
		return descs;
	}

	/**
	 * Get the usage string for a command.
	 * 
	 * @param args
	 * @param level
	 * @param cmd
	 * @return
	 */
	protected String getUsage(String[] args, int level, Command cmd) {
		StringBuilder command = new StringBuilder();

		command.append("/");

		for (int i = 0; i <= level; i++) {
			command.append(args[i] + " ");
		}

		command.append(cmd.flags().length() > 0 ? "[-" + cmd.flags() + "] "
				: "");
		command.append(cmd.usage());

		return command.toString();
	}

	/**
	 * Get the usage string for a nested command.
	 * 
	 * @param args
	 * @param level
	 * @param method
	 * @param player
	 * @return
	 * @throws CommandException
	 */
	protected String getNestedUsage(String[] args, int level, Method method,
			T player) throws CommandException {

		StringBuilder command = new StringBuilder();

		command.append("/");

		for (int i = 0; i <= level; i++) {
			command.append(args[i] + " ");
		}

		Map<CommandIdentifier, Method> map = commands.get(method);
		boolean found = false;

		command.append("<");

		Set<String> allowedCommands = new HashSet<String>();

		for (Map.Entry<CommandIdentifier, Method> entry : map.entrySet()) {
			Method childMethod = entry.getValue();
			found = true;

			HumanNPC npc = getSelectedNPC(player);

			if (hasPermission(childMethod, player, npc)) {
				Command childCmd = childMethod.getAnnotation(Command.class);

				allowedCommands.add(childCmd.aliases()[0]);
			}
		}

		if (allowedCommands.size() > 0) {
			command.append(joinString(allowedCommands, "|", 0));
		} else {
			if (!found) {
				command.append("?");
			} else {
				// command.append("action");
				throw new CommandPermissionsException();
			}
		}

		command.append(">");

		return command.toString();
	}

	public static String joinString(Collection<?> str, String delimiter,
			int initialIndex) {
		if (str.size() == 0) {
			return "";
		}
		StringBuilder buffer = new StringBuilder();
		int i = 0;
		for (Object o : str) {
			if (i >= initialIndex) {
				if (i > 0) {
					buffer.append(delimiter);
				}
				buffer.append(o.toString());
			}
			i++;
		}
		return buffer.toString();
	}

	/**
	 * Attempt to execute a command. This version takes a separate command name
	 * (for the root command) and then a list of following arguments.
	 * 
	 * @param cmd
	 *            command to run
	 * @param args
	 *            arguments
	 * @param player
	 *            command source
	 * @param methodArgs
	 *            method arguments
	 * @throws CommandException
	 */
	public void execute(String cmd, String[] args, T player,
			Object... methodArgs) throws CommandException {

		String[] newArgs = new String[args.length + 1];
		System.arraycopy(args, 0, newArgs, 1, args.length);
		newArgs[0] = cmd;
		Object[] newMethodArgs = new Object[methodArgs.length + 1];
		System.arraycopy(methodArgs, 0, newMethodArgs, 1, methodArgs.length);

		executeMethod(null, newArgs, player, newMethodArgs, 0);
	}

	/**
	 * Attempt to execute a command.
	 * 
	 * @param args
	 * @param player
	 * @param methodArgs
	 * @throws CommandException
	 */
	public void execute(String[] args, T player, Object... methodArgs)
			throws CommandException {
		Object[] newMethodArgs = new Object[methodArgs.length + 1];
		System.arraycopy(methodArgs, 0, newMethodArgs, 1, methodArgs.length);
		executeMethod(null, args, player, newMethodArgs, 0);
	}

	/**
	 * Attempt to execute a command.
	 * 
	 * @param parent
	 * @param args
	 * @param player
	 * @param methodArgs
	 * @param level
	 * @throws CommandException
	 */
	public void executeMethod(Method parent, String[] args, T player,
			Object[] methodArgs, int level) throws CommandException {
		String cmdName = args[level];
		String modifier = "";
		if (args.length > level + 1)
			modifier = args[level + 1];

		Map<CommandIdentifier, Method> map = commands.get(parent);
		Method method = map.get(new CommandIdentifier(cmdName.toLowerCase(),
				modifier.toLowerCase()));

		if (method == null) {
			if (parent == null) { // Root
				throw new UnhandledCommandException();
			} else {
				throw new MissingNestedCommandException("Unknown command: "
						+ cmdName, getNestedUsage(args, level - 1, parent,
						player));
			}
		}
		HumanNPC npc = getSelectedNPC(player);

		if (!hasPermission(method, player, npc)) {
			throw new CommandPermissionsException();
		}

		int argsCount = args.length - 1 - level;

		if (method.isAnnotationPresent(NestedCommand.class)) {
			if (argsCount == 0) {
				throw new MissingNestedCommandException(
						"Sub-command required.", getNestedUsage(args, level,
								method, player));
			} else {
				executeMethod(method, args, player, methodArgs, level + 1);
			}
		} else {
			boolean hasAnnotation = false;
			String requiredType = "";
			boolean requireOwnership = false, requireSelected = false;
			if (method.getClass()
					.isAnnotationPresent(CommandRequirements.class)) {
				CommandRequirements requirements = method.getClass()
						.getAnnotation(CommandRequirements.class);
				requiredType = requirements.requiredType();
				requireOwnership = requirements.requireOwnership();
				requireSelected = requirements.requireSelected();
				hasAnnotation = true;
			}
			if (method.isAnnotationPresent(CommandRequirements.class)) {
				// Method annotations override class annotations.
				CommandRequirements requirements = method
						.getAnnotation(CommandRequirements.class);
				requiredType = requirements.requiredType();
				requireOwnership = requirements.requireOwnership();
				requireSelected = requirements.requireSelected();
				hasAnnotation = true;
			}
			if (hasAnnotation) {
				if (requireSelected && npc == null) {
					throw new RequirementMissingException(
							MessageUtils.mustHaveNPCSelectedMessage);
				}
				if (requireOwnership && npc != null
						&& !NPCManager.validateOwnership(player, npc.getUID())) {
					throw new RequirementMissingException(
							MessageUtils.notOwnerMessage);
				}
				if (npc != null && !requiredType.isEmpty()) {
					if (!npc.isType(requiredType)) {
						throw new RequirementMissingException(
								"Your NPC isn't a " + requiredType + " yet.");
					}
				}
			}
		}
		Command cmd = method.getAnnotation(Command.class);

		String[] newArgs = new String[args.length - level];
		System.arraycopy(args, level, newArgs, 0, args.length - level);

		CommandContext context = new CommandContext(newArgs);

		if (context.argsLength() < cmd.min()) {
			throw new CommandUsageException("Too few arguments.", getUsage(
					args, level, cmd));
		}

		if (cmd.max() != -1 && context.argsLength() > cmd.max()) {
			throw new CommandUsageException("Too many arguments.", getUsage(
					args, level, cmd));
		}

		for (char flag : context.getFlags()) {
			if (cmd.flags().indexOf(String.valueOf(flag)) == -1) {
				throw new CommandUsageException("Unknown flag: " + flag,
						getUsage(args, level, cmd));
			}
		}

		methodArgs[0] = context;
		Object instance = instances.get(method);
		try {
			method.invoke(instance, methodArgs);
		} catch (IllegalArgumentException e) {
			logger.log(Level.SEVERE, "Failed to execute command", e);
		} catch (IllegalAccessException e) {
			logger.log(Level.SEVERE, "Failed to execute command", e);
		} catch (InvocationTargetException e) {
			if (e.getCause() instanceof CommandException) {
				throw (CommandException) e.getCause();
			}

			throw new WrappedCommandException(e.getCause());
		}
	}

	private HumanNPC getSelectedNPC(T player) {
		if (NPCManager.validateSelected(player)) {
			return NPCManager
					.get(NPCManager.selectedNPCs.get(player.getName()));
		}
		return null;
	}

	/**
	 * Returns whether a player has access to a command.
	 * 
	 * @param method
	 * @param player
	 * @return
	 */
	protected boolean hasPermission(Method method, T player, HumanNPC npc) {
		CommandPermissions perms = method
				.getAnnotation(CommandPermissions.class);
		if (perms == null) {
			return true;
		}

		for (String perm : perms.value()) {
			if (hasPermission(player, npc, perm)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Returns whether a player permission..
	 * 
	 * @param player
	 * @param perm
	 * @return
	 */
	public abstract boolean hasPermission(T player, HumanNPC npc, String perm);

	/**
	 * Get the injector used to create new instances. This can be null, in which
	 * case only classes will be registered statically.
	 */
	public Injector getInjector() {
		return injector;
	}

	/**
	 * Set the injector for creating new instances.
	 * 
	 * @param injector
	 *            injector or null
	 */
	public void setInjector(Injector injector) {
		this.injector = injector;
	}
}