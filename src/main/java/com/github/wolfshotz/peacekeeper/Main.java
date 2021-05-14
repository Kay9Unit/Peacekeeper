package com.github.wolfshotz.peacekeeper;

import com.github.wolfshotz.peacekeeper.commands.Command;
import com.github.wolfshotz.peacekeeper.commands.GlobalBanCommand;
import com.github.wolfshotz.peacekeeper.commands.PingCommand;
import com.github.wolfshotz.peacekeeper.commands.RefreshAdminsCommand;
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
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.TextChannel;
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
import reactor.core.scheduler.Schedulers;
import reactor.util.Logger;
import reactor.util.Loggers;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class Main
{
    public static final Logger LOG = Loggers.getLogger(Main.class);
    public static final Map<String, Command> COMMANDS = Command.compile(
            new GlobalBanCommand(),
            new PingCommand(),
            new RefreshAdminsCommand()
    );

    public static void main(String[] args)
    {
        GatewayDiscordClient client = DiscordClient.create(System.getProperty("token")).login().block();

        AdminList.collectAdmins();

        client.on(new ReactiveEventAdapter()
        {
            @Override
            public Publisher<?> onInteractionCreate(InteractionCreateEvent event)
            {
                Command cmd = COMMANDS.get(event.getCommandName());
                if (cmd != null) return cmd.execute(event);
                return Mono.empty();
            }
        }).blockLast();

        teardown(client).block();
    }

    private static Mono<Void> teardown(GatewayDiscordClient client)
    {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> client.logout().block()));

        Mono<Void> console = Mono.<Void>fromCallable(() ->
        {
            Scanner input = new Scanner(System.in);
            while (true)
            {
                while (input.hasNextLine())
                {
                    if (input.nextLine().equals("stop"))
                    {
                        input.close();
                        LOG.info("Stopping...");
                        return null;
                    }
                }
                Thread.sleep(100);
            }
        }).subscribeOn(Schedulers.newSingle("Console Listener", true))
                .doOnError(t -> LOG.error("Error while listening to console:", t));

        return Mono.zip(console, client.onDisconnect()).then();
    }
}
