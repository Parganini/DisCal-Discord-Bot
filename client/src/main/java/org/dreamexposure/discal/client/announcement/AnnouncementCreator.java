package org.dreamexposure.discal.client.announcement;

import org.dreamexposure.discal.client.message.AnnouncementMessageFormatter;
import org.dreamexposure.discal.client.message.Messages;
import org.dreamexposure.discal.core.database.DatabaseManager;
import org.dreamexposure.discal.core.logger.LogFeed;
import org.dreamexposure.discal.core.logger.object.LogObject;
import org.dreamexposure.discal.core.object.GuildSettings;
import org.dreamexposure.discal.core.object.announcement.Announcement;
import org.dreamexposure.discal.core.object.announcement.AnnouncementCreatorResponse;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.message.MessageCreateEvent;
import reactor.core.publisher.Mono;

/**
 * Created by Nova Fox on 3/4/2017.
 * Website: www.cloudcraftgaming.com
 * For Project: DisCal
 */
public class AnnouncementCreator {
    static {
        instance = new AnnouncementCreator();
    }

    private static final AnnouncementCreator instance;
    private final List<Announcement> announcements = new CopyOnWriteArrayList<>();

    private AnnouncementCreator() {
    } //Prevent initialization

    public static AnnouncementCreator getCreator() {
        return instance;
    }

    //Functional
    public Mono<Announcement> init(MessageCreateEvent e, GuildSettings settings) {
        if (!hasAnnouncement(settings.getGuildID())) {
            return e.getMessage().getChannel().flatMap(channel -> {
                Announcement a = new Announcement(settings.getGuildID());
                a.setAnnouncementChannelId(channel.getId().asString());
                announcements.add(a);

                return AnnouncementMessageFormatter.getFormatAnnouncementEmbed(a, settings)
                    .flatMap(em ->
                        Messages.sendMessage(Messages.getMessage("Creator.Announcement.Create.Init", settings), em, e))
                    .doOnNext(a::setCreatorMessage)
                    .then(Messages.deleteMessage(e))
                    .thenReturn(a);
            });
        }
        return Mono.justOrEmpty(getAnnouncement(settings.getGuildID()));
    }

    public Mono<Announcement> init(MessageCreateEvent e, String announcementId, GuildSettings settings) {
        if (!hasAnnouncement(settings.getGuildID())) {
            return DatabaseManager.getAnnouncement(UUID.fromString(announcementId), settings.getGuildID())
                .flatMap(toCopy -> {
                    Announcement a = new Announcement(toCopy);
                    announcements.add(a);

                    return AnnouncementMessageFormatter.getFormatAnnouncementEmbed(a, settings)
                        .flatMap(em ->
                            Messages.sendMessage(Messages.getMessage("Creator.Announcement.Copy.Success", settings), em, e))
                        .doOnNext(a::setCreatorMessage)
                        .then(Messages.deleteMessage(e))
                        .thenReturn(a);
                }).defaultIfEmpty(getAnnouncement(settings.getGuildID()));
        }
        return Mono.justOrEmpty(getAnnouncement(settings.getGuildID()));
    }

    public Mono<Announcement> edit(MessageCreateEvent e, String announcementId, GuildSettings settings) {
        if (!hasAnnouncement(settings.getGuildID())) {
            return DatabaseManager.getAnnouncement(UUID.fromString(announcementId), settings.getGuildID())
                .flatMap(edit -> {
                    Announcement a = new Announcement(edit, true);
                    a.setEditing(true);
                    announcements.add(a);

                    return AnnouncementMessageFormatter.getFormatAnnouncementEmbed(a, settings)
                        .flatMap(em ->
                            Messages.sendMessage(Messages.getMessage("Creator.Announcement.Edit.Init", settings), em, e))
                        .doOnNext(a::setCreatorMessage)
                        .then(Messages.deleteMessage(e))
                        .thenReturn(a)
                        .onErrorResume(err -> {
                            LogFeed.log(LogObject.forException("Failed to init editor", err, this.getClass()));

                            return Mono.empty();
                        });
                });
        } else {
            return Mono.justOrEmpty(getAnnouncement(settings.getGuildID()));
        }
    }

    public void terminate(Snowflake guildId) {
        if (hasAnnouncement(guildId))
            announcements.remove(getAnnouncement(guildId));
    }

    public Mono<AnnouncementCreatorResponse> confirmAnnouncement(Snowflake guildId) {
        return Mono.justOrEmpty(getAnnouncement(guildId))
            .filter(Announcement::hasRequiredValues)
            .flatMap(a ->
                DatabaseManager.updateAnnouncement(a).map(success -> {
                    if (success) {
                        terminate(guildId);
                        return new AnnouncementCreatorResponse(true, a);
                    } else {
                        return new AnnouncementCreatorResponse(false, null);
                    }
                })
            )
            .defaultIfEmpty(new AnnouncementCreatorResponse(false, null));
    }

    //Getters
    public Announcement getAnnouncement(Snowflake guildId) {
        for (Announcement a : announcements) {
            if (a.getGuildId().equals(guildId)) {
                a.setLastEdit(System.currentTimeMillis());
                return a;
            }
        }
        return null;
    }

    public List<Announcement> getAllAnnouncements() {
        return announcements;
    }

    //Booleans/Checkers
    public boolean hasAnnouncement(Snowflake guildId) {
        for (Announcement a : announcements) {
            if (a.getGuildId().equals(guildId))
                return true;
        }
        return false;
    }
}