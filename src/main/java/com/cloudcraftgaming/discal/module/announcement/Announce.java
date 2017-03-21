package com.cloudcraftgaming.discal.module.announcement;

import com.cloudcraftgaming.discal.Main;
import com.cloudcraftgaming.discal.database.DatabaseManager;
import com.cloudcraftgaming.discal.internal.calendar.CalendarAuth;
import com.cloudcraftgaming.discal.internal.calendar.event.EventMessageFormatter;
import com.cloudcraftgaming.discal.internal.data.BotData;
import com.cloudcraftgaming.discal.internal.email.EmailSender;
import com.cloudcraftgaming.discal.utils.Message;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.handle.obj.IRole;
import sx.blah.discord.handle.obj.IUser;
import sx.blah.discord.util.EmbedBuilder;

import java.io.IOException;
import java.util.List;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

/**
 * Created by Nova Fox on 3/4/2017.
 * Website: www.cloudcraftgaming.com
 * For Project: DisCal
 */
public class Announce extends TimerTask {
    @Override
    public void run() {
        //EmailSender.getSender().sendDebugEmail(this.getClass(), "01", "Announcement Runnable Start");
        DateTime now = new DateTime(System.currentTimeMillis());
        Long nowMS = System.currentTimeMillis();
        for (IGuild guild : Main.client.getGuilds()) {
            try {
                BotData data = DatabaseManager.getManager().getData(guild.getID());
                String guildId = data.getGuildId();
                for (Announcement a : DatabaseManager.getManager().getAnnouncements(guildId)) {
                    if (a.getAnnouncementType().equals(AnnouncementType.SPECIFIC)) {
                        try {
                            Calendar service = CalendarAuth.getCalendarService();
                            Event event = service.events().get(data.getCalendarAddress(), a.getEventId()).execute();

                            //Test for the time...
                            Long eventMS = event.getStart().getDateTime().getValue();
                            Long timeUntilEvent = eventMS - nowMS;
                            Long minutesToEvent = TimeUnit.MILLISECONDS.toMinutes(timeUntilEvent);
                            Long announcementTime = Integer.toUnsignedLong(a.getMinutesBefore() + (a.getHoursBefore() * 60));
                            Long difference = minutesToEvent - announcementTime;
                            if (difference >= 0) {
                                if (difference <= 10) {
                                    //Right on time
                                    sendAnnouncementMessage(a, event, data);

                                    //Delete announcement to ensure it does not spam fire
                                    DatabaseManager.getManager().deleteAnnouncement(a.getAnnouncementId().toString());
                                }
                            } else {
                                //Event past... Delete announcement so we need not worry about useless data in the Db costing memory.
                                DatabaseManager.getManager().deleteAnnouncement(a.getAnnouncementId().toString());
                            }
                        } catch (IOException e) {
                            //Event may not exist...
                            EmailSender.getSender().sendExceptionEmail(e, this.getClass());
                        }
                    } else {
                        try {
                            Calendar service = CalendarAuth.getCalendarService();
                            Events events = service.events().list(data.getCalendarAddress())
                                    .setMaxResults(10)
                                    .setTimeMin(now)
                                    .setOrderBy("startTime")
                                    .setSingleEvents(true)
                                    .execute();
                            List<Event> items = events.getItems();
                            if (items.size() > 0) {
                                for (Event event : items) {
                                    //Test for the time...
                                    Long eventMS = event.getStart().getDateTime().getValue();
                                    Long timeUntilEvent = eventMS - nowMS;
                                    Long minutesToEvent = TimeUnit.MILLISECONDS.toMinutes(timeUntilEvent);
                                    Long announcementTime = Integer.toUnsignedLong(a.getMinutesBefore() + (a.getHoursBefore() * 60));
                                    Long difference = minutesToEvent - announcementTime;
                                    if (difference >= 0 && difference <= 10) {
                                        //Right on time
                                        sendAnnouncementMessage(a, event, data);
                                    }
                                }
                            }
                        } catch (IOException e) {
                            EmailSender.getSender().sendExceptionEmail(e, this.getClass());
                        }
                    }
                }
            } catch (Exception e) {
                EmailSender.getSender().sendExceptionEmail(e, this.getClass());
            }
        }
        //EmailSender.getSender().sendDebugEmail(this.getClass(), "02", "Announcement Runnable completed!");
    }

    /**
     * Sends an embed with the announcement info in a proper format.
     * @param announcement The announcement to send info about.
     * @param event the calendar event the announcement is for.
     * @param data The BotData belonging to the guild.
     */
    private void sendAnnouncementMessage(Announcement announcement, Event event, BotData data) {
        EmbedBuilder em = new EmbedBuilder();
        em.withAuthorIcon(Main.client.getGuildByID("266063520112574464").getIconURL());
        em.withAuthorName("DisCal");
        em.withTitle("!~Event Announcement~!");
        em.appendField("Event Name/Summary", event.getSummary(), true);
        em.appendField("Event Date", EventMessageFormatter.getHumanReadableDate(event.getStart()), true);
        em.appendField("Event Time", EventMessageFormatter.getHumanReadableTime(event.getStart()), false);
        try {
            em.appendField("TimeZone", event.getStart().getTimeZone(), true);
        } catch (Exception e) {
            try {
                Calendar service = CalendarAuth.getCalendarService();
                String tz = service.calendars().get(data.getCalendarAddress()).execute().getTimeZone();
                em.appendField("TimeZone", tz, true);
            } catch (Exception e1) {
                em.appendField("TimeZone", "Unknown *Error Occurred", true);
            }
        }
        em.appendField("Event ID", event.getId(), false);
        em.appendField("Additional Info", announcement.getInfo(), false);
        em.withUrl(event.getHtmlLink());
        em.withFooterText("Announcement ID: " + announcement.getAnnouncementId().toString());
        em.withColor(36, 153, 153);

        IGuild guild = Main.client.getGuildByID(announcement.getGuildId());

        String userMentions = "";
        for (String userId : announcement.getSubscriberUserIds()) {
            try {
                IUser user = guild.getUserByID(userId);
                if (user != null) {
                    userMentions = userMentions + user.mention(true) + " ";
                }
            } catch (Exception e) {
                //User does not exist, safely ignore.
            }
        }

        String roleMentions = "";
        for (String roleId : announcement.getSubscriberRoleIds()) {
            try {
                IRole role = guild.getRoleByID(roleId);
                if (role != null) {
                    roleMentions = roleMentions + role.mention() + " ";
                }
            } catch (Exception e) {
                //Role does not exist, safely ignore.
            }
        }

        String message = "Subscribers: " + userMentions + " " + roleMentions;

        IChannel channel = guild.getChannelByID(announcement.getAnnouncementChannelId());
        Message.sendMessage(em.build(), message, channel, Main.client);
    }
}