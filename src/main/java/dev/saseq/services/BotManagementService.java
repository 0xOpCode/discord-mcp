package dev.saseq.services;

import dev.saseq.manager.DiscordBotManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class BotManagementService {

    private final DiscordBotManager botManager;

    public BotManagementService(DiscordBotManager botManager) {
        this.botManager = botManager;
    }

    @Tool(name = "list_bots", description = "List all registered Discord bots, their aliases, status, and connected server count")
    public String listBots() {
        Map<String, JDA> bots = botManager.getBots();
        if (bots.isEmpty()) {
            return "No bots currently registered.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("### 🤖 Connected Discord Bots:\n");
        sb.append(String.format("| %-16s | %-22s | %-20s | %-12s | %-8s |\n", "Alias", "Bot Tag", "Bot ID", "Status", "Servers"));
        sb.append("|------------------|------------------------|----------------------|--------------|----------|\n");

        for (Map.Entry<String, JDA> entry : bots.entrySet()) {
            String alias = entry.getKey();
            JDA jda = entry.getValue();
            boolean isDefault = alias.equals(botManager.getDefaultBotAlias());
            String tag = jda.getSelfUser().getAsTag();
            String id = jda.getSelfUser().getId();
            String status = jda.getStatus().name();
            int guilds = jda.getGuilds().size();
            String aliasDisplay = isDefault ? alias + " (default)" : alias;
            sb.append(String.format("| %-16s | %-22s | %-20s | %-12s | %-8d |\n", aliasDisplay, tag, id, status, guilds));
        }
        return sb.toString();
    }

    @Tool(name = "add_bot", description = "Connect and add a new Discord bot to the running MCP instance using its token without restart")
    public String addBot(@ToolParam(description = "Unique alias for this bot (e.g. 'mod-bot', 'server2-bot')") String alias,
                         @ToolParam(description = "Discord Bot Token") String token) {
        try {
            return botManager.registerBot(alias, token);
        } catch (Exception e) {
            return "Failed to connect bot: " + e.getMessage();
        }
    }

    @Tool(name = "remove_bot", description = "Disconnect and remove a Discord bot by its alias")
    public String removeBot(@ToolParam(description = "Bot alias to remove") String alias) {
        return botManager.unregisterBot(alias);
    }

    @Tool(name = "list_servers", description = "List all Discord servers (Guilds) across all connected bots, showing Server Name, Server ID, Member count, and managing bot")
    public String listServers(@ToolParam(description = "Optional bot alias to filter by", required = false) String bot) {
        Map<String, JDA> bots = botManager.getBots();
        if (bots.isEmpty()) {
            return "No bots currently connected.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("### 🌐 Connected Discord Servers:\n");
        sb.append(String.format("| %-28s | %-20s | %-10s | %-15s |\n", "Server Name", "Server ID", "Members", "Bot Alias"));
        sb.append("|------------------------------|----------------------|------------|-----------------|\n");

        int totalServers = 0;
        for (Map.Entry<String, JDA> entry : bots.entrySet()) {
            String alias = entry.getKey();
            if (bot != null && !bot.trim().isEmpty() && !alias.equalsIgnoreCase(bot.trim())) {
                continue;
            }
            JDA jda = entry.getValue();
            for (Guild g : jda.getGuilds()) {
                totalServers++;
                String name = g.getName();
                if (name.length() > 28) {
                    name = name.substring(0, 25) + "...";
                }
                sb.append(String.format("| %-28s | %-20s | %-10d | %-15s |\n",
                        name,
                        g.getId(),
                        g.getMemberCount(),
                        alias));
            }
        }

        if (totalServers == 0) {
            return "No servers found" + (bot != null ? " for bot '" + bot + "'." : ".");
        }
        return sb.toString();
    }

    @Tool(name = "set_default_bot", description = "Set the default bot alias to use when no specific bot or server is specified")
    public String setDefaultBot(@ToolParam(description = "Bot alias to set as default") String alias) {
        try {
            botManager.setDefaultBotAlias(alias);
            return "Default bot set to: " + alias;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(name = "send_message_as_bot", description = "Send a message through a specific bot alias to a channel")
    public String sendMessageAsBot(@ToolParam(description = "Bot alias to send the message from") String bot,
                                   @ToolParam(description = "Discord channel ID") String channelId,
                                   @ToolParam(description = "Message content") String message) {
        JDA jda = botManager.getBot(bot);
        if (jda == null) {
            return "Bot alias '" + bot + "' not found. Use list_bots to see available bots.";
        }
        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) {
            return "Channel ID '" + channelId + "' not accessible by bot '" + bot + "'.";
        }
        channel.sendMessage(message).queue();
        return "Message sent to #" + channel.getName() + " by bot '" + bot + "'.";
    }
}
