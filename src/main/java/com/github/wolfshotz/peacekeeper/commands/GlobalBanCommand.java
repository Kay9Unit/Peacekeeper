package com.github.wolfshotz.peacekeeper.commands;

import com.github.wolfshotz.peacekeeper.AdminList;
import com.github.wolfshotz.peacekeeper.ErrorCodes;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
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
import discord4j.discordjson.json.MessageData;
import discord4j.rest.interaction.InteractionResponse;
import discord4j.rest.util.ApplicationCommandOptionType;
import discord4j.rest.util.Color;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

public class GlobalBanCommand extends Command
{
    public GlobalBanCommand(String name, String description)
    {
        super(name, description);

        arg(ApplicationCommandOptionData.builder()
                .name("userid")
                .description("The 18-digit unique identifier of the user")
                .type(ApplicationCommandOptionType.STRING.getValue())
                .required(true)
                .build());
        arg(ApplicationCommandOptionData.builder()
                .name("reason")
                .description("A reason for the ban")
                .type(ApplicationCommandOptionType.STRING.getValue())
                .required(true)
                .build());
    }

    @Override
    public Mono<MessageData> execute(InteractionCreateEvent event)
    {
        InteractionResponse response = event.getInteractionResponse();
        GatewayDiscordClient client = event.getClient();
        Member executor = event.getInteraction().getMember().orElse(null);

        if (!AdminList.contains(executor))
            return event.acknowledge().then(response.createFollowupMessage("You don't have permission to use this, buddy."));

        ApplicationCommandInteraction args = event.getInteraction().getCommandInteraction();
        long userId = args.getOption("userid")
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asString)
                .map(GlobalBanCommand::parseLongSafely)
                .orElse(0L);
        String reason = args.getOption("reason")
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asString)
                .orElse("<No Reason Specified>");

        return event.acknowledge()
                .then(client.getUserById(Snowflake.of(userId))
                        .onErrorMap(clientError(ErrorCodes.UNKNOWN_USER), t -> new CommandException("Are you gonna supply an ACTUAL user id?", t)))
                .flatMap(user -> banUserChecked(user, client.getGuilds(), executor, reason))
                .flatMap(user -> response.createFollowupMessage(createFollowupMessage(user, reason)));
    }

    protected Mono<User> banUserChecked(User user, Flux<Guild> guilds, Member executor, String reason)
    {
        return (AdminList.contains(user)?
                Flux.error(new CommandException("This user cann't be banned, idjot.")) :
                banUserIn(user, guilds, executor, reason)
        ).then(Mono.just(user));
    }

    protected Flux<Message> banUserIn(User user, Flux<Guild> guilds, Member executor, String reason)
    {
        return guilds.flatMap(guild -> guild.ban(user.getId(), spec -> spec.setReason(reason))
                .onErrorResume(clientError(ErrorCodes.ALREADY_BANNED), t -> Mono.empty())
                .then(executor.getGuild())
                .flatMap(from -> guild.getPublicUpdatesChannel()
                        .flatMap(channel -> createBanEmbed(channel, user, executor, from, reason))));
    }

    protected Mono<Message> createBanEmbed(TextChannel channel, User user, Member executor, Guild guild, String reason)
    {
        return channel.createEmbed(spec -> spec.setAuthor(executor.getUsername() + " || " + guild.getName(), null, executor.getAvatarUrl())
                .setTitle(String.format("User `%s` (ID: `%s`) has been banned globally.", user.getTag(), user.getId().asString()))
                .setThumbnail(user.getAvatarUrl())
                .setDescription("**Reason:** " + reason)
                .setColor(Color.RED)
                .setTimestamp(Instant.now()))
                .onErrorResume(t -> Mono.empty());
    }

    protected String createFollowupMessage(User user, String reason)
    {
        return String.format("User `%s` (ID: `%s`) has been banned across all servers for: \"%s\"",
                user.getTag(),
                user.getId().asString(),
                reason);
    }

    private static long parseLongSafely(String s)
    {
        try
        {
            return Long.parseLong(s);
        }
        catch (NumberFormatException e)
        {
            return 0L;
        }
    }
}
