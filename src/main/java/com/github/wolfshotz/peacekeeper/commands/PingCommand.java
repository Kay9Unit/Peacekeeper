package com.github.wolfshotz.peacekeeper.commands;

import discord4j.core.event.domain.InteractionCreateEvent;
import discord4j.discordjson.json.MessageData;
import reactor.core.publisher.Mono;

public class PingCommand extends Command
{
    public PingCommand()
    {
        super("ping", "Pong!");
    }

    @Override
    public Mono<MessageData> execute(InteractionCreateEvent event)
    {
        return event.acknowledge()
                .then(event.getInteractionResponse()
                        .createFollowupMessage("Pong!"));
    }
}
