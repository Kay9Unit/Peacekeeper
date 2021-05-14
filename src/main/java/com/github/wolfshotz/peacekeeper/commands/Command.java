package com.github.wolfshotz.peacekeeper.commands;

import discord4j.core.event.domain.InteractionCreateEvent;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.discordjson.json.MessageData;
import discord4j.discordjson.possible.Possible;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class Command implements ApplicationCommandRequest
{
    private final String name;
    private final String description;
    private List<ApplicationCommandOptionData> options = null;

    public Command(String name, String description)
    {
        this.name = name;
        this.description = description;
    }

    protected void arg(ApplicationCommandOptionData data)
    {
        if (options == null) options = new ArrayList<>();
        options.add(data);
    }

    @Override
    public String name()
    {
        return name;
    }

    @Override
    public String description()
    {
        return description;
    }

    @Override
    public Possible<List<ApplicationCommandOptionData>> options()
    {
        return options == null? Possible.absent() : Possible.of(options);
    }

    public abstract Mono<MessageData> execute(InteractionCreateEvent event);

    public static Map<String, Command> compile(Command... cmds)
    {
        Map<String, Command> map = new HashMap<>();
        for (Command cmd : cmds) map.put(cmd.name(), cmd);
        return map;
    }
}
