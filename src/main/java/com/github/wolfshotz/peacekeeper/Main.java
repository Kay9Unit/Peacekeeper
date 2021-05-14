package com.github.wolfshotz.peacekeeper;

import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClient;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.ReactiveEventAdapter;
import discord4j.core.event.domain.InteractionCreateEvent;
import discord4j.core.object.command.ApplicationCommandInteraction;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.User;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.discordjson.json.WebhookMessageEditRequest;
import discord4j.rest.RestClient;
import discord4j.rest.http.client.ClientException;
import discord4j.rest.interaction.InteractionResponse;
import discord4j.rest.json.response.ErrorResponse;
import discord4j.rest.util.ApplicationCommandOptionType;
import discord4j.rest.util.Color;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.Logger;
import reactor.util.Loggers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class Main
{
    private static final Logger LOG = Loggers.getLogger(Main.class);
    private static final String ADMINS_URL = "https://raw.githubusercontent.com/WolfShotz/Peacekeeper/master/src/main/resources/admins.txt";
    private static final List<Long> ADMIN_LIST = Collections.synchronizedList(new ArrayList<>());

    public static void main(String[] args)
    {
        GatewayDiscordClient client = DiscordClient.create(System.getProperty("token")).login().block();

        refreshCommands(client);
        collectAdmins();

        Executors.newSingleThreadExecutor().submit(() -> console(client));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> client.logout().block()));

        client.on(new ReactiveEventAdapter()
        {
            @Override
            public Publisher<?> onInteractionCreate(InteractionCreateEvent event)
            {
                switch (event.getCommandName())
                {
                    case "globalban":
                        return globalBan(client, event);
                    case "refresh_admins":
                        return event.acknowledge()
                                .then(event.getInteractionResponse()
                                        .createFollowupMessage("Admin list reloaded <a:loading:842766889033793536>"))
                                .doOnNext(data -> refreshAdmins(event, data.id().asLong()));

                    case "ping":
                        return event.acknowledge()
                                .then(event.getInteractionResponse()
                                        .createFollowupMessage("Pong!"));
                }

                return Mono.empty();
            }
        }).blockLast();

        client.onDisconnect().block();
    }

    private static void refreshAdmins(InteractionCreateEvent event, long messageId)
    {
        Executors.newSingleThreadExecutor().submit(() ->
        {
            collectAdmins();
            event.getInteractionResponse()
                    .editFollowupMessage(messageId, WebhookMessageEditRequest.builder()
                            .content("Refreshed admin list \uD83D\uDC4D")
                            .build(), false)
                    .block();
        });
    }

    private static void refreshCommands(GatewayDiscordClient client)
    {
        RestClient rest = client.getRestClient();
        long appId = rest.getApplicationId().block();

        ApplicationCommandRequest globalBan = ApplicationCommandRequest.builder()
                .name("globalban")
                .description("Ban a user across multiple servers.")
                .addOption(ApplicationCommandOptionData.builder()
                        .name("userid")
                        .description("The 18-digit unique identifier of the user")
                        .type(ApplicationCommandOptionType.STRING.getValue())
                        .required(true)
                        .build())
                .addOption(ApplicationCommandOptionData.builder()
                        .name("reason")
                        .description("A reason for the ban")
                        .type(ApplicationCommandOptionType.STRING.getValue())
                        .required(false)
                        .build())
                .build();

        ApplicationCommandRequest refreshAdmins = ApplicationCommandRequest.builder()
                .name("refresh_admins")
                .description("Re-Retrieves the admin list from the host")
                .build();
        
        ApplicationCommandRequest ping = ApplicationCommandRequest.builder()
                .name("ping")
                .description("pong!")
                .build();

        List<ApplicationCommandRequest> list = Arrays.asList(globalBan, refreshAdmins, ping);

        rest.getApplicationService()
                .bulkOverwriteGlobalApplicationCommand(appId, list)
                .doOnError(e -> LOG.error("Something happend registering a command...", e))
                .blockLast();

        LOG.info("Commands Initalized: {}", list.stream()
                .map(ApplicationCommandRequest::name)
                .collect(Collectors.toList()));
    }

    private static void console(GatewayDiscordClient client)
    {
        try
        {
            Scanner input = new Scanner(System.in);
            while (true)
            {
                while (input.hasNextLine())
                {
                    String command = input.nextLine();
                    switch (command)
                    {
                        case "stop":
                            LOG.info("Stopping...");
                            input.close();
                            System.exit(0);
                        case "refresh_commands":
                            refreshCommands(client);
                            break;
                    }
                }
                Thread.sleep(100);
            }
        }
        catch (Throwable error)
        {
            LOG.warn("Something happened while listening to console...");
            error.printStackTrace();
            System.exit(-1);
        }
    }

    // must return the response of the command
    private static Publisher<?> globalBan(GatewayDiscordClient client, InteractionCreateEvent event)
    {
        InteractionResponse response = event.getInteractionResponse();
        Member executor = event.getInteraction().getMember().orElse(null);

        if (!ADMIN_LIST.contains(executor.getId().asLong()))
            return response.createFollowupMessage("You don't have permission to use this, buddy.");

        ApplicationCommandInteraction args = event.getInteraction().getCommandInteraction();
        long userId = args.getOption("userid")
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asString)
                .map(Long::parseLong)
                .orElse(1L);
        String reason = args.getOption("reason")
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asString)
                .orElse("<No Reason Specified>");

        return event.acknowledge()
                .then(client.getUserById(Snowflake.of(userId)))
                .flatMap(user -> banUserIn(user, client.getGuilds(), executor, reason))
                .flatMap(user -> response.createFollowupMessage(String.format("User `%s` (ID: `%s`) has been banned across all servers for: \"%s\"", user.getTag(), user.getId().asString(), reason)))
                .onErrorResume(ClientException.class, e ->
                {
                    if (e.getErrorResponse().isPresent())
                    {
                        ErrorResponse er = e.getErrorResponse().get();
                        Object obj = er.getFields().get("message");
                        if (obj instanceof String && "Unknown User".equals(obj))
                            return response.createFollowupMessage("Are you gonna supply an ACTUAL user id?");
                    }

                    LOG.error("Unable to process ban", e);
                    return response.createFollowupMessage("SOMETHING went wrong when banning...");
                });
    }

    private static Mono<User> banUserIn(User user, Flux<Guild> guilds, Member executor, String reason)
    {
        return guilds.flatMap(guild -> guild.ban(user.getId(), spec -> spec.setReason(reason))
                .then(executor.getGuild())
                .flatMap(from -> guild.getPublicUpdatesChannel()
                        .flatMap(channel -> channel.createEmbed(spec -> createBanEmbed(spec, user, executor, from, reason)))))
                .then(Mono.just(user));
    }

    private static void createBanEmbed(EmbedCreateSpec spec, User user, Member executor, Guild guild, String reason)
    {
        spec.setAuthor(executor.getUsername() + " || " + guild.getName(), null, executor.getAvatarUrl())
                .setTitle(String.format("User `%s` (ID: `%s`) has been banned globally.", user.getTag(), user.getId().asString()))
                .setThumbnail(user.getAvatarUrl())
                .setDescription("**Reason:** " + reason)
                .setColor(Color.RED)
                .setTimestamp(Instant.now());
    }

    @SuppressWarnings("ConstantConditions")
    private static void collectAdmins()
    {
        ADMIN_LIST.clear();
        InputStreamReader reader;

        try
        {
            reader = new InputStreamReader(new URL(ADMINS_URL).openStream());
        }
        catch (IOException e)
        {
            reader = new InputStreamReader(Main.class.getResourceAsStream("admins.txt"));
        }

        try (BufferedReader pointer = new BufferedReader(reader))
        {
            if (reader == null) throw new NullPointerException("Could not retrieve InputStream for admin list");

            String line;
            while ((line = pointer.readLine()) != null)
                ADMIN_LIST.add(Long.parseLong(line.substring(0, 18).trim()));
        }
        catch (Exception e)
        {
            throw new RuntimeException("Could not Load admin list", e);
        }
    }
}
