package dev.saseq.manager;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.NewsChannel;
import net.dv8tion.jda.api.entities.channel.concrete.StageChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages multiple Discord bots (JDA instances) within a single runtime instance.
 * Provides smart routing across bots by Guild ID and Channel ID.
 */
@Component
public class DiscordBotManager {
    private static final Logger log = LoggerFactory.getLogger(DiscordBotManager.class);

    private final Map<String, JDA> bots = new ConcurrentHashMap<>();
    private volatile String defaultBotAlias = "default";

    @Value("${DISCORD_TOKEN:}")
    private String primaryToken;

    @Value("${DISCORD_TOKENS:}")
    private String multiTokens;

    @PostConstruct
    public void init() {
        // Register primary token if present
        if (primaryToken != null && !primaryToken.trim().isEmpty()) {
            try {
                registerBot("default", primaryToken.trim());
                log.info("Initialized default bot successfully.");
            } catch (Exception e) {
                log.error("Failed to initialize default bot: {}", e.getMessage(), e);
            }
        }

        // Register multi-tokens if present (format: "alias1:token1,alias2:token2" or "token1,token2")
        if (multiTokens != null && !multiTokens.trim().isEmpty()) {
            String[] entries = multiTokens.split(",");
            int counter = 1;
            for (String entry : entries) {
                entry = entry.trim();
                if (entry.isEmpty()) continue;
                String alias;
                String token;
                if (entry.contains(":")) {
                    String[] parts = entry.split(":", 2);
                    alias = parts[0].trim();
                    token = parts[1].trim();
                } else {
                    alias = "bot-" + (counter++);
                    token = entry;
                }
                if (!bots.containsKey(alias)) {
                    try {
                        registerBot(alias, token);
                        log.info("Initialized multi-token bot: {}", alias);
                    } catch (Exception e) {
                        log.error("Failed to initialize bot '{}': {}", alias, e.getMessage());
                    }
                }
            }
        }
    }

    public synchronized String registerBot(String alias, String token) throws InterruptedException {
        if (alias == null || alias.trim().isEmpty()) {
            throw new IllegalArgumentException("Bot alias cannot be empty");
        }
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("Bot token cannot be empty");
        }
        alias = alias.trim();
        if (bots.containsKey(alias)) {
            unregisterBot(alias);
        }

        log.info("Connecting Discord bot with alias '{}'...", alias);
        JDA jda = JDABuilder.createDefault(token.trim())
                .enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_VOICE_STATES, GatewayIntent.SCHEDULED_EVENTS)
                .build()
                .awaitReady();

        bots.put(alias, jda);
        if (defaultBotAlias == null || !bots.containsKey(defaultBotAlias)) {
            defaultBotAlias = alias;
        }

        String username = jda.getSelfUser().getAsTag();
        String id = jda.getSelfUser().getId();
        int guildCount = jda.getGuilds().size();
        log.info("Bot '{}' ({}) connected to {} guilds.", alias, username, guildCount);
        return "Bot registered successfully: " + alias + " [" + username + ", ID: " + id + "] connected to " + guildCount + " servers.";
    }

    public synchronized String unregisterBot(String alias) {
        if (alias == null || alias.trim().isEmpty()) {
            throw new IllegalArgumentException("Bot alias cannot be empty");
        }
        alias = alias.trim();
        JDA jda = bots.remove(alias);
        if (jda == null) {
            return "Bot alias '" + alias + "' not found.";
        }
        try {
            jda.shutdown();
        } catch (Exception e) {
            log.warn("Error shutting down bot '{}': {}", alias, e.getMessage());
        }
        if (alias.equals(defaultBotAlias)) {
            defaultBotAlias = bots.keySet().stream().findFirst().orElse(null);
        }
        return "Bot '" + alias + "' successfully unregistered and disconnected.";
    }

    public Map<String, JDA> getBots() {
        return Collections.unmodifiableMap(bots);
    }

    public JDA getBot(String alias) {
        return bots.get(alias);
    }

    public String getDefaultBotAlias() {
        return defaultBotAlias;
    }

    public void setDefaultBotAlias(String alias) {
        if (alias == null || !bots.containsKey(alias)) {
            throw new IllegalArgumentException("Unknown bot alias: " + alias);
        }
        this.defaultBotAlias = alias;
    }

    public JDA getDefaultJda() {
        if (defaultBotAlias != null) {
            JDA jda = bots.get(defaultBotAlias);
            if (jda != null) return jda;
        }
        return bots.values().stream().findFirst().orElse(null);
    }

    public Guild findGuildById(String guildId) {
        for (JDA bot : bots.values()) {
            Guild g = bot.getGuildById(guildId);
            if (g != null) return g;
        }
        return null;
    }

    public Guild findGuildById(long guildId) {
        for (JDA bot : bots.values()) {
            Guild g = bot.getGuildById(guildId);
            if (g != null) return g;
        }
        return null;
    }

    public TextChannel findTextChannelById(String channelId) {
        for (JDA bot : bots.values()) {
            TextChannel c = bot.getTextChannelById(channelId);
            if (c != null) return c;
        }
        return null;
    }

    public VoiceChannel findVoiceChannelById(String channelId) {
        for (JDA bot : bots.values()) {
            VoiceChannel c = bot.getVoiceChannelById(channelId);
            if (c != null) return c;
        }
        return null;
    }

    public StageChannel findStageChannelById(String channelId) {
        for (JDA bot : bots.values()) {
            StageChannel c = bot.getStageChannelById(channelId);
            if (c != null) return c;
        }
        return null;
    }

    public NewsChannel findNewsChannelById(String channelId) {
        for (JDA bot : bots.values()) {
            NewsChannel c = bot.getNewsChannelById(channelId);
            if (c != null) return c;
        }
        return null;
    }

    public ThreadChannel findThreadChannelById(String channelId) {
        for (JDA bot : bots.values()) {
            ThreadChannel c = bot.getThreadChannelById(channelId);
            if (c != null) return c;
        }
        return null;
    }

    public GuildChannel findGuildChannelById(String channelId) {
        for (JDA bot : bots.values()) {
            GuildChannel c = bot.getGuildChannelById(channelId);
            if (c != null) return c;
        }
        return null;
    }

    public Role findRoleById(String roleId) {
        for (JDA bot : bots.values()) {
            Role r = bot.getRoleById(roleId);
            if (r != null) return r;
        }
        return null;
    }

    public List<Guild> getAllGuilds() {
        Map<String, Guild> guildMap = new LinkedHashMap<>();
        for (JDA bot : bots.values()) {
            for (Guild g : bot.getGuilds()) {
                guildMap.putIfAbsent(g.getId(), g);
            }
        }
        return new ArrayList<>(guildMap.values());
    }

    public JDA createRoutingProxy() {
        return (JDA) Proxy.newProxyInstance(
                JDA.class.getClassLoader(),
                new Class<?>[]{JDA.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        String name = method.getName();
                        Class<?>[] paramTypes = method.getParameterTypes();

                        if ("getGuildById".equals(name)) {
                            if (paramTypes.length == 1 && paramTypes[0] == String.class) {
                                return findGuildById((String) args[0]);
                            } else if (paramTypes.length == 1 && (paramTypes[0] == long.class || paramTypes[0] == Long.class)) {
                                return findGuildById((Long) args[0]);
                            }
                        } else if ("getTextChannelById".equals(name)) {
                            if (paramTypes.length == 1 && paramTypes[0] == String.class) {
                                return findTextChannelById((String) args[0]);
                            }
                        } else if ("getVoiceChannelById".equals(name)) {
                            if (paramTypes.length == 1 && paramTypes[0] == String.class) {
                                return findVoiceChannelById((String) args[0]);
                            }
                        } else if ("getStageChannelById".equals(name)) {
                            if (paramTypes.length == 1 && paramTypes[0] == String.class) {
                                return findStageChannelById((String) args[0]);
                            }
                        } else if ("getNewsChannelById".equals(name)) {
                            if (paramTypes.length == 1 && paramTypes[0] == String.class) {
                                return findNewsChannelById((String) args[0]);
                            }
                        } else if ("getThreadChannelById".equals(name)) {
                            if (paramTypes.length == 1 && paramTypes[0] == String.class) {
                                return findThreadChannelById((String) args[0]);
                            }
                        } else if ("getGuildChannelById".equals(name)) {
                            if (paramTypes.length == 1 && paramTypes[0] == String.class) {
                                return findGuildChannelById((String) args[0]);
                            }
                        } else if ("getRoleById".equals(name)) {
                            if (paramTypes.length == 1 && paramTypes[0] == String.class) {
                                return findRoleById((String) args[0]);
                            }
                        } else if ("getGuilds".equals(name) && (paramTypes == null || paramTypes.length == 0)) {
                            return getAllGuilds();
                        } else if ("toString".equals(name)) {
                            return "MultiBotJdaProxy[bots=" + bots.keySet() + ", default=" + defaultBotAlias + "]";
                        } else if ("equals".equals(name)) {
                            return proxy == (args != null && args.length > 0 ? args[0] : null);
                        } else if ("hashCode".equals(name)) {
                            return System.identityHashCode(proxy);
                        }

                        JDA defaultJda = getDefaultJda();
                        if (defaultJda == null) {
                            throw new IllegalStateException("No Discord bot is currently registered or connected.");
                        }
                        return method.invoke(defaultJda, args);
                    }
                }
        );
    }

    @PreDestroy
    public void cleanup() {
        for (Map.Entry<String, JDA> entry : bots.entrySet()) {
            try {
                entry.getValue().shutdown();
            } catch (Exception ignored) {}
        }
        bots.clear();
    }
}
