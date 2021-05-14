package com.github.wolfshotz.peacekeeper;

import com.github.wolfshotz.peacekeeper.commands.Command;
import com.github.wolfshotz.peacekeeper.commands.GlobalBanCommand;
import com.github.wolfshotz.peacekeeper.commands.PingCommand;
import com.github.wolfshotz.peacekeeper.commands.RefreshAdminsCommand;
import discord4j.core.DiscordClient;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.ReactiveEventAdapter;
import discord4j.core.event.domain.InteractionCreateEvent;
import discord4j.rest.RestClient;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.Logger;
import reactor.util.Loggers;

import java.util.ArrayList;
import java.util.Map;
import java.util.Scanner;

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

        registerCommands(client.getRestClient());
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

        teardown(client);
    }

    private static void registerCommands(RestClient rest)
    {
        rest.getApplicationId()
                .flatMap(l -> rest.getApplicationService()
                        .bulkOverwriteGlobalApplicationCommand(l, new ArrayList<>(COMMANDS.values()))
                        .then())
                .onErrorMap(t -> new RuntimeException("Error registering commands", t))
                .block();
    }

    private static void teardown(GatewayDiscordClient client)
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

        Mono.zip(console, client.onDisconnect()).then().block();
    }
}
