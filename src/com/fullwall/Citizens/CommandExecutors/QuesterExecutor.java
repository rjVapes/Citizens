package com.fullwall.Citizens.CommandExecutors;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.fullwall.Citizens.Citizens;
import com.fullwall.Citizens.Permission;
import com.fullwall.Citizens.NPCs.NPCManager;
import com.fullwall.Citizens.Properties.PropertyManager;
import com.fullwall.Citizens.Questers.Quest;
import com.fullwall.Citizens.Utils.HelpUtils;
import com.fullwall.Citizens.Utils.MessageUtils;
import com.fullwall.Citizens.Utils.StringUtils;
import com.fullwall.resources.redecouverte.NPClib.HumanNPC;

public class QuesterExecutor implements CommandExecutor {
	@SuppressWarnings("unused")
	private Citizens plugin;

	public QuesterExecutor(Citizens plugin) {
		this.plugin = plugin;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command,
			String commandLabel, String[] args) {
		if (!(sender instanceof Player)) {
			sender.sendMessage(MessageUtils.mustBeIngameMessage);
			return true;
		}
		Player player = (Player) sender;
		HumanNPC npc = null;
		boolean returnval = false;
		if (NPCManager.validateSelected((Player) sender)) {
			npc = NPCManager
					.getNPC(NPCManager.NPCSelected.get(player.getName()));
		} else {
			sender.sendMessage(ChatColor.RED
					+ MessageUtils.mustHaveNPCSelectedMessage);
			return true;
		}
		if (!NPCManager.validateOwnership(player, npc.getUID())) {
			sender.sendMessage(MessageUtils.notOwnerMessage);
			return true;
		}
		if (!npc.isQuester()) {
			sender.sendMessage(ChatColor.RED + "Your NPC isn't a quester yet.");
			return true;
		} else {
			if (args.length == 1 && args[0].equalsIgnoreCase("help")) {
				if (Permission.hasPermission("citizens.quester.help", sender)) {
					HelpUtils.sendQuesterHelp(sender);
				} else {
					sender.sendMessage(MessageUtils.noPermissionsMessage);
				}
				returnval = true;
			} else if (args.length == 2
					&& args[0].equalsIgnoreCase("createquest")) {
				if (Permission.hasPermission("citizens.quester.managequests",
						sender)) {
					createQuest(player, npc, args[1]);
				} else {
					sender.sendMessage(MessageUtils.noPermissionsMessage);
				}
				returnval = true;
			} else if (args.length == 2 && args[0].equalsIgnoreCase("type")) {
				if (Permission.hasPermission("citizens.quester.managequests",
						sender)) {
					changeQuestType(player, args[1]);
				} else {
					sender.sendMessage(MessageUtils.noPermissionsMessage);
				}
				returnval = true;
			} else if (args.length >= 2 && args[0].equalsIgnoreCase("desc")) {
				if (Permission.hasPermission("citizens.quester.managequests",
						sender)) {
					// TODO set the quest description
				} else {
					sender.sendMessage(MessageUtils.noPermissionsMessage);
				}
				returnval = true;
			}
			PropertyManager.get("quester").saveState(npc);
		}
		return returnval;
	}

	private void createQuest(Player player, HumanNPC npc, String name) {
		Quest quest = new Quest(name, npc.getStrippedName());
		quest.createFile();
		player.sendMessage(ChatColor.GREEN
				+ "You created a new quest file called "
				+ StringUtils.yellowify(name + ".quest")
				+ ". You can modify the file in-game and/or edit it manually.");
	}

	private void changeQuestType(Player player, String type) {
		// TODO do stuff
	}
}