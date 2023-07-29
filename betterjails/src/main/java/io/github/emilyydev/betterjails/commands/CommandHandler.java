//
// This file is part of BetterJails, licensed under the MIT License.
//
// Copyright (c) 2022 emilyy-dev
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.
//

package io.github.emilyydev.betterjails.commands;

import com.github.fefo.betterjails.api.event.plugin.PluginReloadEvent;
import io.github.emilyydev.betterjails.BetterJailsPlugin;
import io.github.emilyydev.betterjails.util.Util;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;

import static io.github.emilyydev.betterjails.util.Util.color;
import static io.github.emilyydev.betterjails.util.Util.uuidOrNil;

public class CommandHandler implements CommandExecutor, Listener {

  private final BetterJailsPlugin plugin;
  private final Server server;
  private final Map<String, UUID> namesToUuid = new HashMap<>();
  private ConfigurationSection messages;

  public CommandHandler(final BetterJailsPlugin plugin) {
    this.plugin = plugin;
    this.server = plugin.getServer();
    this.messages = plugin.getConfig().getConfigurationSection("messages");
    this.server.getPluginManager().registerEvent(
        PlayerJoinEvent.class, this, EventPriority.HIGH,
        (l, e) -> playerJoin((PlayerJoinEvent) e), this.plugin
    );
    for (final OfflinePlayer offlinePlayer : this.server.getOfflinePlayers()) {
      final String name = offlinePlayer.getName();
      if (name != null) {
        this.namesToUuid.put(name.toLowerCase(Locale.ROOT), offlinePlayer.getUniqueId());
      }
    }
  }

  @Override
  public boolean onCommand(
      final @NotNull CommandSender sender,
      final @NotNull Command cmd,
      final @NotNull String alias,
      final @NotNull String @NotNull [] args
  ) {
    switch (cmd.getName()) {
      case "betterjails":
        if (args.length == 1) {
          return betterjails(sender, args[0]);
        } else {
          sender.sendMessage(color("&bBetterJails &3by &bemilyy-dev &3- v%s", this.plugin.getDescription().getVersion()));
        }
        return true;

      case "jail":
        if (args.length == 1 && "info".equalsIgnoreCase(args[0]) && sender.hasPermission("betterjails.jail.owninfo"))  {
          return jailInfo(sender, null);
        } else if (args.length == 2 && "info".equalsIgnoreCase(args[0]) && sender.hasPermission("betterjails.jail.info")) {
          return jailInfo(sender, args[1]);
        } else if (args.length >= 3 && sender.hasPermission("betterjails.jail.setinjail")) {
          return jailPlayer(sender, args);
        } 

        if (sender.hasPermission("betterjails.jail.setinjail")) {
          sender.sendMessage(color(this.messages.getString("jailCmdUsage")));
        }

        if (sender.hasPermission("betterjails.jail.info")) {
          sender.sendMessage(color(this.messages.getString("infoCmdUsage")));
        } else if (sender.hasPermission("betterjails.jail.owninfo")) {
           sender.sendMessage(color(this.messages.getString("ownInfoCmdUsage")));
        }

        return false;
      case "jails":
        return jails(sender);

      case "unjail":
        if (args.length != 1) {
          return false;
        } else {
          return releasePlayer(sender, args[0]);
        }

      case "setjail":
        if (args.length != 1) {
          return false;
        } else {
          return setjail(sender, args[0]);
        }

      case "deljail":
        if (args.length != 1) {
          return false;
        } else {
          return deljail(sender, args[0]);
        }
    }
    return false;
  }

  private boolean betterjails(final CommandSender sender, final String argument) {
    if (argument.equalsIgnoreCase("reload") && sender.hasPermission("betterjails.betterjails.reload")) {
      try {
        this.plugin.dataHandler.reload();
        this.messages = this.plugin.getConfig().getConfigurationSection("messages");
        sender.sendMessage(color(this.messages.getString("reload").replace("{player}", sender.getName())));

        this.plugin.eventBus().post(PluginReloadEvent.class, sender);
      } catch (IOException exception) {
        sender.sendMessage(color(
            "&cThere was an internal error while trying to reload the data files.\n" +
            "Please check console for more information."
        ));
        exception.printStackTrace();
      }
    } else if (argument.equalsIgnoreCase("save") && sender.hasPermission("betterjails.betterjails.save")) {
      try {
        this.plugin.dataHandler.save();
        sender.sendMessage(color(this.messages.getString("save").replace("{player}", sender.getName())));

      } catch (final IOException exception) {
        sender.sendMessage(color(
            "&cThere was an internal error while trying to save the data files.\n" +
            "Please check console for more information."
        ));
        exception.printStackTrace();
      }

    } else {
      sender.sendMessage(color("&bBetterJails &3by &bemilyy-dev &3- v%s", this.plugin.getDescription().getVersion()));
    }
    return true;
  }

  private boolean jailPlayer(final CommandSender sender, final String[] args) {
    final String prisoner = args[0];
    final String jail = args[1];
    final String time = args[2];
    final String[] reason = Arrays.copyOfRange(args, 3, args.length);
    String reasonString = "";
    final OfflinePlayer player =
        this.server.getOfflinePlayer(this.namesToUuid.getOrDefault(prisoner.toLowerCase(Locale.ROOT), Util.NIL_UUID));
    if (!player.getUniqueId().equals(Util.NIL_UUID)) {
      if (player.isOnline() && player.getPlayer().hasPermission("betterjails.jail.exempt")) {
        sender.sendMessage(color(
            this.messages.getString("jailFailedPlayerExempt")
                .replace("{prisoner}", prisoner)
                .replace("{player}", sender.getName())
                .replace("{jail}", jail)
                .replace("{time}", time)
        ));
        return true;
      }

      if (reason != null && reason.length > 0) {
        reasonString = Util.convertStringArrayToString(reason, " ");
      }

      if (time.matches("^(\\d{1,10}(\\.\\d{1,2})?)[yMwdhms]$")) {
        final double scale;
        switch (time.charAt(time.length() - 1)) {
          case 's':
            scale = 1;
            break;

          case 'm':
          default:
            scale = 60;
            break;

          case 'h':
            scale = 3600;
            break;

          case 'd':
            scale = 3600 * 24;
            break;

          case 'w':
            scale = 3600 * 24 * 7;
            break;

          case 'M':
            scale = 3600 * 24 * 30.4375;
            break;

          case 'y':
            scale = 3600 * 24 * 365.25;
            break;
        }

        final long seconds = (long) (scale * Double.parseDouble(time.substring(0, time.length() - 1)));
        try {
          if (!this.plugin.dataHandler.addJailedPlayer(player, jail, uuidOrNil(sender), sender.getName(), seconds, reasonString)) {
            sender.sendMessage(color(
                this.messages.getString("jailFailedJailNotFound")
                    .replace("{prisoner}", prisoner)
                    .replace("{player}", sender.getName())
                    .replace("{jail}", jail)
                    .replace("{time}", time)
            ));
            return true;
          }
        } catch (final IOException exception) {
          sender.sendMessage(color("&4Fatal error! Could not save player data"));
          exception.printStackTrace();
        }

        for (final Player playerToBroadcast : this.server.getOnlinePlayers()) {
          if (playerToBroadcast.hasPermission("betterjails.receivebroadcast") && playerToBroadcast != player.getPlayer()) {
            playerToBroadcast.sendMessage(color(
                this.messages.getString("jailSuccess")
                    .replace("{prisoner}", prisoner)
                    .replace("{player}", sender.getName())
                    .replace("{jail}", jail)
                    .replace("{time}", time)
                    .replace("{reason}", reasonString)
            ));
          }
        }
        this.server.getConsoleSender().sendMessage(color(
            this.messages.getString("jailSuccess")
                .replace("{prisoner}", prisoner)
                .replace("{player}", sender.getName())
                .replace("{jail}", jail)
                .replace("{time}", time)
                .replace("{reason}", reasonString)
        ));

        if (player.isOnline()) {
          player.getPlayer().sendMessage(color(
              this.messages.getString("jailInfoPrisoner")
                  .replace("{prisoner}", prisoner)
                  .replace("{player}", sender.getName())
                  .replace("{jail}", jail)
                  .replace("{time}", time)
                  .replace("{reason}", reasonString)
          ));
        }

      } else {
        sender.sendMessage(color(
            this.messages.getString("jailFailedTimeIncorrect")
                .replace("{prisoner}", prisoner)
                .replace("{player}", sender.getName())
                .replace("{jail}", jail)
                .replace("{time}", time)
        ));
      }
      return true;
    }
    sender.sendMessage(color(
        this.messages.getString("jailFailedPlayerNeverJoined")
            .replace("{prisoner}", prisoner)
            .replace("{player}", sender.getName())
            .replace("{jail}", jail)
            .replace("{time}", time)
    ));
    return true;
  }

 private boolean jailInfo(final CommandSender sender, final String _prisoner) {
    boolean ownInfo = false;
    String prisoner = _prisoner;
    if (prisoner == null) {
      ownInfo = true;
      prisoner = sender.getName();
    }

    final OfflinePlayer player =
        this.server.getOfflinePlayer(this.namesToUuid.getOrDefault(prisoner.toLowerCase(Locale.ROOT), Util.NIL_UUID));
    if (!player.getUniqueId().equals(Util.NIL_UUID)) {
      final UUID uuid = player.getUniqueId();
      if (this.plugin.dataHandler.isPlayerJailed(prisoner)) {
        if (
            this.plugin.dataHandler.isReleased(uuid, false) ||
            this.plugin.dataHandler.getSecondsLeft(uuid, 0) <= 0
        ) {
          sender.sendMessage(color(
              this.messages.getString(ownInfo ? "ownInfoFailedPlayerNotJailed" : "infoFailedPlayerNotJailed")
                  .replace("{prisoner}", prisoner)
                  .replace("{player}", sender.getName())
          ));
          return true;
        }


        double secondsLeft = this.plugin.dataHandler.getSecondsLeft(uuid, 0);
        char timeUnit = 's';
        if (secondsLeft >= 3600 * 24 * 365.25) {
          secondsLeft /= 3600 * 24 * 365.25;
          timeUnit = 'y';

        } else if (secondsLeft >= 3600 * 24 * 30.4375) {
          secondsLeft /= 3600 * 24 * 30.4375;
          timeUnit = 'M';

        } else if (secondsLeft >= 3600 * 24 * 7) {
          secondsLeft /= 3600 * 24 * 7;
          timeUnit = 'w';

        } else if (secondsLeft >= 3600 * 24) {
          secondsLeft /= 3600 * 24;
          timeUnit = 'd';

        } else if (secondsLeft >= 3600) {
          secondsLeft /= 3600;
          timeUnit = 'h';

        } else if (secondsLeft >= 60) {
          secondsLeft /= 60;
          timeUnit = 'm';
        }

        final Location lastLocation = this.plugin.dataHandler.getLastLocation(uuid);
        final String lastLocationString = color(
            "x:%,d y:%,d z%,d &7in &f%s",
            lastLocation.getBlockX(),
            lastLocation.getBlockY(),
            lastLocation.getBlockZ(),
            lastLocation.getWorld().getName()
        );

        final StringJoiner joiner = new StringJoiner(", ");
        joiner.setEmptyValue(this.plugin.getConfig().getBoolean("changeGroup", false) ? "&oNone" : "&oFeature not enabled");
        this.plugin.dataHandler.getAllParentGroups(uuid).forEach(joiner::add);

        String text = this.messages.getString("jailInfoText");
        if (ownInfo) {
          text = this.messages.getString("jailOwnInfoText");
        }

        String infoLines = color(text
          .replace("{prisoner}", this.plugin.dataHandler.getName(uuid, "&oundefined"))
          .replace("{uuid}", uuid.toString())
          .replace("{time}", String.format("%,.2f", secondsLeft))
          .replace("{timeUnit}", Character.toString(timeUnit))
          .replace("{jail}", this.plugin.dataHandler.getJail(uuid, "&oundefined"))
          .replace("{player}", this.plugin.dataHandler.getJailer(uuid, "&oundefined"))
          .replace("{lastLocation}", lastLocationString)
          .replace("{primayGroup}", this.plugin.dataHandler.getPrimaryGroup(
            uuid, this.plugin.getConfig().getBoolean("changeGroup", false) ? "&oundefined" : "&oFeature not enabled"
          ))
          .replace("{reason}", this.plugin.dataHandler.getReason(uuid, "")
          .replace("{parentGroups}", joiner.toString())
        ));

        sender.sendMessage(infoLines);
      } else {
        sender.sendMessage(color(
            this.messages.getString(ownInfo ? "ownInfoFailedPlayerNotJailed" : "infoFailedPlayerNotJailed")
                .replace("{prisoner}", prisoner)
                .replace("{player}", sender.getName())
        ));
        return true;
      }
    } else {
      sender.sendMessage(color(
          this.messages.getString("infoFailedPlayerNeverJoined")
              .replace("{prisoner}", prisoner)
              .replace("{player}", sender.getName())
      ));
    }
    return true;
  }


  private boolean jails(final CommandSender sender) {
    final List<String> jailList = new ArrayList<>();
    if (this.plugin.dataHandler.getJails().size() == 0) {
      jailList.add(color(this.messages.getString("listNoJails")));
    } else {
      jailList.add(color(this.messages.getString("listJailsPremessage")));

      switch (this.messages.getString("jailsFormat")) {
        case "list":
          this.plugin.dataHandler.getJails().forEach((k, v) -> jailList.add("&7· " + k));
          break;

        case "line":
          jailList.add("&7" + String.join(", ", this.plugin.dataHandler.getJails().keySet()) + '.');
          break;
      }
    }

    jailList.replaceAll(Util::color);
    jailList.forEach(sender::sendMessage);
    return true;
  }

  private boolean releasePlayer(final CommandSender sender, final String prisoner) {
    final OfflinePlayer player =
        this.server.getOfflinePlayer(this.namesToUuid.getOrDefault(prisoner.toLowerCase(Locale.ROOT), Util.NIL_UUID));
    if (!player.getUniqueId().equals(Util.NIL_UUID)) {
      final boolean wasReleased =
          this.plugin.dataHandler.releaseJailedPlayer(player.getUniqueId(), uuidOrNil(sender), sender.getName());

      if (wasReleased) {
        for (final Player playerToBroadcast : this.server.getOnlinePlayers()) {
          if (playerToBroadcast.hasPermission("betterjails.receivebroadcast") && playerToBroadcast != player.getPlayer()) {
            playerToBroadcast.sendMessage(color(
                this.messages.getString("unjailSuccess")
                    .replace("{prisoner}", prisoner)
                    .replace("{player}", sender.getName())
            ));
          }
        }
        this.server.getConsoleSender().sendMessage(color(
            this.messages.getString("unjailSuccess")
                .replace("{prisoner}", prisoner)
                .replace("{player}", sender.getName())
        ));
        if(player.isOnline()) {
            player.getPlayer().sendMessage(color(
                this.messages.getString("unjailInfoPrisoner")
                    .replace("{prisoner}", prisoner)
                    .replace("{player}", sender.getName())
            ));
        }
      } else {
        sender.sendMessage(color(
            this.messages.getString("unjailFailedPlayerNotJailed")
                .replace("{prisoner}", prisoner)
                .replace("{player}", sender.getName())
        ));
      }
      return true;
    }
    sender.sendMessage(color(
        this.messages.getString("unjailFailedPlayerNeverJoined")
            .replace("{prisoner}", prisoner)
            .replace("{player}", sender.getName())
    ));
    return true;
  }

  private boolean setjail(final CommandSender sender, final String jail) {
    if (!(sender instanceof Player)) {
      sender.sendMessage(color(
          this.messages.getString("setjailFromConsole")
              .replace("{player}", sender.getName())
              .replace("{jail}", jail)
      ));

    } else {
      final Player player = (Player) sender;
      try {
        this.plugin.dataHandler.addJail(jail, player.getLocation());
        sender.sendMessage(color(
            this.messages.getString("setjailSuccess")
                .replace("{player}", sender.getName())
                .replace("{jail}", jail)
        ));

      } catch (final IOException exception) {
        sender.sendMessage(color("&cThere was an error while trying to add the jail."));
        exception.printStackTrace();
      }
    }

    return true;
  }

  private boolean deljail(final CommandSender sender, final String jail) {
    if (this.plugin.dataHandler.getJail(jail) != null) {
      try {
        this.plugin.dataHandler.removeJail(jail);
        sender.sendMessage(color(
            this.messages.getString("deljailSuccess")
                .replace("{player}", sender.getName())
                .replace("{jail}", jail)
        ));
      } catch (final IOException exception) {
        sender.sendMessage(color("&cThere was an error while trying to remove the jail."));
        exception.printStackTrace();
      }
    } else {
      sender.sendMessage(color(
          this.messages.getString("deljailFailed")
              .replace("{player}", sender.getName())
              .replace("{jail}", jail)
      ));
    }
    return true;
  }

  private void playerJoin(final PlayerJoinEvent event) {
    final Player player = event.getPlayer();
    this.namesToUuid.put(player.getName().toLowerCase(Locale.ROOT), player.getUniqueId());
  }
}
