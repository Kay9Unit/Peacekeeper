package com.github.wolfshotz.peacekeeper.commands;

import discord4j.core.event.domain.InteractionCreateEvent;
import discord4j.discordjson.json.MessageData;
import reactor.core.publisher.Mono;

import java.util.stream.Collectors;

public class ListServersCommand extends Command
{
    public ListServersCommand()
    {
        super("listservers", "Lists the servers Peacekeeper currently resides in");
    }

    @Override
    public Mono<MessageData> execute(InteractionCreateEvent event)
    {
        return event.acknowledge()
                .then(event.getClient()
                        .getGuilds()
                        .map(g -> "`" + g.getName() + "`")
                        .collect(Collectors.toList()))
                .flatMap(l -> event.getInteractionResponse().createFollowupMessage("Peacekeeper currently resides in: " + l));
    }
}
