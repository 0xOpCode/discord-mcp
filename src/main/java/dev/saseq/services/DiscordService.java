package dev.saseq.services;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DiscordService {

    private final JDA jda;

    @Value("${DISCORD_GUILD_ID:}")
    private String defaultGuildId;

    public DiscordService(JDA jda) {
        this.jda = jda;
    }

    private String resolveGuildId(String guildId) {
        if ((guildId == null || guildId.isEmpty()) && defaultGuildId != null && !defaultGuildId.isEmpty()) {
            return defaultGuildId;
        }
        return guildId;
    }

    /**
     * Lists all Discord servers the bot is currently in.
     */
    @Tool(name = "list_servers", description = "List all Discord servers (guilds) the bot is currently in, including name, ID, member count, and admin status")
    public String listServers() {
        List<Guild> guilds = jda.getGuilds();
        if (guilds.isEmpty()) {
            return "Bot is not in any Discord servers.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("### 🌐 Connected Discord Servers (").append(guilds.size()).append(" total):\n");
        sb.append(String.format("| %-28s | %-20s | %-10s | %-8s |\n", "Server Name", "Server ID", "Members", "Admin"));
        sb.append("|------------------------------|----------------------|------------|----------|\n");
        for (Guild g : guilds) {
            String name = g.getName();
            if (name.length() > 28) name = name.substring(0, 25) + "...";
            Member self = g.getSelfMember();
            boolean isAdmin = self.hasPermission(Permission.ADMINISTRATOR);
            sb.append(String.format("| %-28s | %-20s | %-10d | %-8s |\n",
                    name,
                    g.getId(),
                    g.getMemberCount(),
                    isAdmin ? "✅ YES" : "❌ NO"));
        }
        return sb.toString();
    }

    /**
     * Checks all permissions the bot has in a specific server.
     */
    @Tool(name = "check_bot_permissions", description = "Check all permissions the bot currently has in a specific Discord server")
    public String checkBotPermissions(@ToolParam(description = "Discord server ID", required = false) String guildId) {
        guildId = resolveGuildId(guildId);
        if (guildId == null || guildId.isEmpty()) {
            throw new IllegalArgumentException("Discord server ID cannot be null");
        }
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            throw new IllegalArgumentException("Discord server not found by guildId: " + guildId + ". Make sure the bot is invited to this server.");
        }
        Member self = guild.getSelfMember();
        boolean isAdmin = self.hasPermission(Permission.ADMINISTRATOR);
        StringBuilder sb = new StringBuilder();
        sb.append("### 🛡️ Bot Permissions in: ").append(guild.getName()).append(" (ID: ").append(guild.getId()).append(")\n");
        sb.append("**Bot User:** ").append(self.getUser().getAsTag()).append(" (ID: ").append(self.getId()).append(")\n");
        sb.append("**Is Administrator:** ").append(isAdmin ? "✅ YES (Full Administrator Access)" : "❌ NO").append("\n\n");
        sb.append("**Key Permissions Breakdown:**\n");
        Permission[] keyPerms = {
                Permission.ADMINISTRATOR,
                Permission.MANAGE_SERVER,
                Permission.MANAGE_CHANNEL,
                Permission.MANAGE_ROLES,
                Permission.MANAGE_WEBHOOKS,
                Permission.KICK_MEMBERS,
                Permission.BAN_MEMBERS,
                Permission.MODERATE_MEMBERS,
                Permission.VIEW_AUDIT_LOGS,
                Permission.MESSAGE_SEND,
                Permission.MESSAGE_MANAGE,
                Permission.MESSAGE_HISTORY,
                Permission.VIEW_CHANNEL,
                Permission.VOICE_MOVE_OTHERS,
                Permission.VOICE_MUTE_OTHERS
        };
        for (Permission perm : keyPerms) {
            boolean has = self.hasPermission(perm);
            sb.append("- ").append(has ? "✅ " : "❌ ").append(perm.getName()).append("\n");
        }
        return sb.toString();
    }

    /**
     * Retrieves detailed information about a specified Discord server.
     *
     * @param guildId Optional ID of the Discord server (guild). If not provided, the default server will be used.
     * @return A formatted string containing server details, including name, ID, owner, creation date, member count, channel counts, and boost status.
     */
    @Tool(name = "get_server_info", description = "Get detailed discord server information")
    public String getServerInfo(@ToolParam(description = "Discord server ID", required = false) String guildId) {
        guildId = resolveGuildId(guildId);
        if (guildId == null || guildId.isEmpty()) {
            throw new IllegalArgumentException("Discord server ID cannot be null");
        }

        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            throw new IllegalArgumentException("Discord server not found by guildId");
        }
        String serverName = guild.getName();
        String serverId = guild.getId();
        Member owner = guild.retrieveOwner().complete();
        int totalMembers = guild.getMemberCount();
        int textChannelCount = guild.getTextChannels().size();
        int voiceChannelCount = guild.getVoiceChannels().size();
        int categoryCount = guild.getCategories().size();
        String creationDate = guild.getTimeCreated().toLocalDate().toString();
        int boostCount = guild.getBoostCount();
        String boostTier = guild.getBoostTier().toString();

        return "Server Name: " + serverName + "\n" +
                "Server ID: " + serverId + "\n" +
                "Owner: " + owner.getUser().getName() + "\n" +
                "Created On: " + creationDate + "\n" +
                "Members: " + totalMembers + "\n" +
                "Channels: " +
                " - Text: " + textChannelCount + "\n" +
                " - Voice: " + voiceChannelCount + "\n" +
                "  - Categories: " + categoryCount + "\n" +
                "Boosts: " +
                " - Count: " + boostCount + "\n" +
                " - Tier: " + boostTier;
    }
}