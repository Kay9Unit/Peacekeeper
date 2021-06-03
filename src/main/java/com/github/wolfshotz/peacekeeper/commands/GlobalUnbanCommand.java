package com.github.wolfshotz.peacekeeper.commands;

import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.rest.util.Color;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

public class GlobalUnbanCommand extends GlobalBanCommand
{
    public GlobalUnbanCommand(String name, String description)
    {
        super(name, description);
    }

    @Override
    protected Flux<Message> banUserIn(User user, Flux<Guild> guilds, Member executor, String reason)
    {
        return guilds.flatMap(guild -> guild.unban(user.getId(), reason)
                .then(executor.getGuild())
                .flatMap(from -> guild.getPublicUpdatesChannel()
                        .flatMap(channel -> createBanEmbed(channel, user, executor, from, reason))))
                .onErrorResume(t -> Flux.empty());
    }

    @Override
    protected String createFollowupMessage(User user, String reason)
    {
        return String.format("User `%s` (ID: `%s`) has been unbanned across all servers for: \"%s\"",
            user.getTag(),
            user.getId().asString(),
            reason);
    }

    @Override
    protected Mono<Message> createBanEmbed(TextChannel channel, User user, Member executor, Guild guild, String reason)
    {
        return channel.createEmbed(spec -> spec.setAuthor(executor.getUsername() + " || " + guild.getName(), null, executor.getAvatarUrl())
                .setTitle(String.format("User `%s` (ID: `%s`) has been unbanned globally.", user.getTag(), user.getId().asString()))
                .setThumbnail(user.getAvatarUrl())
                .setDescription("**Reason:** " + reason)
                .setColor(Color.DEEP_SEA)
                .setTimestamp(Instant.now()))
                .onErrorResume(t -> Mono.empty());
    }
}
