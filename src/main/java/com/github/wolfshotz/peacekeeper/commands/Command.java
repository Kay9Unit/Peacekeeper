package com.github.wolfshotz.peacekeeper.commands;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import discord4j.core.event.domain.InteractionCreateEvent;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.discordjson.json.MessageData;
import discord4j.discordjson.possible.Possible;
import discord4j.rest.http.client.ClientException;
import discord4j.rest.json.response.ErrorResponse;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.function.Predicate;

@JsonSerialize
@JsonDeserialize
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
    @JsonProperty("name")
    public String name()
    {
        return name;
    }

    @Override
    @JsonProperty("description")
    public String description()
    {
        return description;
    }

    @Override
    @JsonProperty("options")
    public Possible<List<ApplicationCommandOptionData>> options()
    {
        return options == null? Possible.absent() : Possible.of(options);
    }

    @Override
    public String toString()
    {
        return name;
    }

    public abstract Mono<MessageData> execute(InteractionCreateEvent event);

    public static Map<String, Command> compile(Command... cmds)
    {
        Map<String, Command> map = new HashMap<>();
        for (Command cmd : cmds) map.put(cmd.name(), cmd);
        return map;
    }

    public static Predicate<Throwable> clientError(int errorCode)
    {
        return t ->
        {
            if (t instanceof ClientException)
            {
                ClientException exception = (ClientException) t;
                Optional<ErrorResponse> er = exception.getErrorResponse();
                if (er.isPresent())
                {
                    Object obj = er.get().getFields().get("code");
                    return obj instanceof Integer && obj.equals(errorCode);
                }
            }
            return false;
        };
    }
}
