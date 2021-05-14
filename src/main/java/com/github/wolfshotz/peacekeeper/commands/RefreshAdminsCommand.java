package com.github.wolfshotz.peacekeeper.commands;

import com.github.wolfshotz.peacekeeper.AdminList;
import discord4j.core.event.domain.InteractionCreateEvent;
import discord4j.discordjson.json.MessageData;
import discord4j.discordjson.json.WebhookMessageEditRequest;
import reactor.core.publisher.Mono;

import java.util.concurrent.Executors;

public class RefreshAdminsCommand extends Command
{
    public RefreshAdminsCommand()
    {
        super("refresh_admins", "Re-Retrieves the admin list from the host");
    }

    @Override
    public Mono<MessageData> execute(InteractionCreateEvent event)
    {
        return event.acknowledge()
                .then(event.getInteractionResponse()
                        .createFollowupMessage("Retrieving admin list <a:loading:842766889033793536>"))
                .doOnNext(data -> refreshAdmins(event, data.id().asLong()));
    }

    private static void refreshAdmins(InteractionCreateEvent event, long messageId)
    {
        Executors.newSingleThreadExecutor().submit(() ->
        {
            AdminList.collectAdmins();
            event.getInteractionResponse()
                    .editFollowupMessage(messageId, WebhookMessageEditRequest.builder()
                            .content("Admin list has been reloaded \uD83D\uDC4D")
                            .build(), false)
                    .block();
        });
    }
}
