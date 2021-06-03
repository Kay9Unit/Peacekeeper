package com.github.wolfshotz.peacekeeper;

import com.github.wolfshotz.peacekeeper.commands.*;
import discord4j.core.DiscordClient;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.ReactiveEventAdapter;
import discord4j.core.event.domain.InteractionCreateEvent;
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.rest.RestClient;
import discord4j.rest.service.ApplicationService;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import reactor.util.Logger;
import reactor.util.Loggers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Main
{
    public static final Logger LOG = Loggers.getLogger(Main.class);
    private static final long TESTING_GUILD = 811301329330372659L;
    public static final Map<String, Command> COMMANDS = Command.compile(
            new GlobalBanCommand("globalban", "Ban a user across multiple servers."),
            new GlobalUnbanCommand("globalunban", "Pardons a user accross multiple servers."),
            new PingCommand(),
            new RefreshAdminsCommand(),
            new ListServersCommand()
    );

    public static void main(String[] args)
    {
        GatewayDiscordClient client = DiscordClient.create(System.getProperty("token")).login().block();

        registerCommands(client.getRestClient(), System.getProperties().containsKey("testing"));
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

        Runtime.getRuntime().addShutdownHook(new Thread(() -> client.logout().block()));
        client.onDisconnect().block();
    }

    private static void registerCommands(RestClient rest, boolean testing)
    {
        ApplicationService service = rest.getApplicationService();
        List<ApplicationCommandRequest> commands = new ArrayList<>(COMMANDS.values());
        rest.getApplicationId()
                .flatMap(l -> (testing? service.bulkOverwriteGuildApplicationCommand(l, TESTING_GUILD, commands) :
                                        service.bulkOverwriteGlobalApplicationCommand(l, commands))
                .then())
                .onErrorMap(t -> new RuntimeException("Error registering commands", t))
                .block();

        LOG.info("Commands Registered: " + commands);
    }
}
