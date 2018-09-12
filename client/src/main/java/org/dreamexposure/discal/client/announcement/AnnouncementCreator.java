package org.dreamexposure.discal.client.announcement;

import org.dreamexposure.discal.client.message.AnnouncementMessageFormatter;
import org.dreamexposure.discal.client.message.MessageManager;
import org.dreamexposure.discal.core.database.DatabaseManager;
import org.dreamexposure.discal.core.object.GuildSettings;
import org.dreamexposure.discal.core.object.announcement.Announcement;
import org.dreamexposure.discal.core.object.announcement.AnnouncementCreatorResponse;
import org.dreamexposure.discal.core.utils.AnnouncementUtils;
import org.dreamexposure.discal.core.utils.PermissionChecker;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.obj.IMessage;

import java.util.ArrayList;
import java.util.UUID;

/**
 * Created by Nova Fox on 3/4/2017.
 * Website: www.cloudcraftgaming.com
 * For Project: DisCal
 */
@SuppressWarnings("ConstantConditions")
public class AnnouncementCreator {
	private static AnnouncementCreator instance;

	private ArrayList<Announcement> announcements = new ArrayList<>();

	private AnnouncementCreator() {
	} //Prevent initialization

	/**
	 * Gets the instance of the AnnouncementCreator.
	 *
	 * @return The instance of the AnnouncementCreator.
	 */
	public static AnnouncementCreator getCreator() {
		if (instance == null) {
			instance = new AnnouncementCreator();
		}
		return instance;
	}

	//Functionals

	/**
	 * Initiates the creator for the guild involved.
	 *
	 * @param e The event received upon init.
	 * @return A new Announcement.
	 */
	public Announcement init(MessageReceivedEvent e, GuildSettings settings) {
		if (!hasAnnouncement(settings.getGuildID())) {
			Announcement a = new Announcement(settings.getGuildID());
			a.setAnnouncementChannelId(e.getMessage().getChannel().getStringID());

			if (PermissionChecker.botHasMessageManagePerms(e)) {
				IMessage msg = MessageManager.sendMessageSync(MessageManager.getMessage("Creator.Announcement.Create.Init", settings), AnnouncementMessageFormatter.getFormatAnnouncementEmbed(a, settings), e);
				a.setCreatorMessage(msg);
			} else {
				MessageManager.sendMessageAsync(MessageManager.getMessage("Creator.Notif.MANAGE_MESSAGES", settings), e);
			}

			announcements.add(a);
			return a;
		}
		return getAnnouncement(settings.getGuildID());
	}

	public Announcement init(MessageReceivedEvent e, String announcementId, GuildSettings settings) {
		if (!hasAnnouncement(settings.getGuildID()) && AnnouncementUtils.announcementExists(announcementId, settings.getGuildID())) {
			Announcement toCopy = DatabaseManager.getManager().getAnnouncement(UUID.fromString(announcementId), settings.getGuildID());

			//Copy
			Announcement a = new Announcement(toCopy);

			if (PermissionChecker.botHasMessageManagePerms(e)) {
				IMessage msg = MessageManager.sendMessageSync(MessageManager.getMessage("Creator.Announcement.Copy.Success", settings), AnnouncementMessageFormatter.getFormatAnnouncementEmbed(a, settings), e);
				a.setCreatorMessage(msg);
			} else {
				MessageManager.sendMessageAsync(MessageManager.getMessage("Creator.Notif.MANAGE_MESSAGES", settings), e);
			}

			announcements.add(a);
			return a;
		}
		return getAnnouncement(settings.getGuildID());
	}

	public Announcement edit(MessageReceivedEvent e, String announcementId, GuildSettings settings) {
		if (!hasAnnouncement(settings.getGuildID()) && AnnouncementUtils.announcementExists(announcementId, settings.getGuildID())) {
			Announcement edit = DatabaseManager.getManager().getAnnouncement(UUID.fromString(announcementId), settings.getGuildID());

			//Copy
			Announcement a = new Announcement(edit, true);
			a.setEditing(true);

			if (PermissionChecker.botHasMessageManagePerms(e)) {
				IMessage msg = MessageManager.sendMessageSync(MessageManager.getMessage("Creator.Announcement.Edit.Init", settings), AnnouncementMessageFormatter.getFormatAnnouncementEmbed(a, settings), e);
				a.setCreatorMessage(msg);
			} else {
				MessageManager.sendMessageAsync(MessageManager.getMessage("Creator.Notif.MANAGE_MESSAGES", settings), e);
			}

			announcements.add(a);
			return a;
		}
		return getAnnouncement(settings.getGuildID());
	}

	public boolean terminate(long guildId) {
		if (hasAnnouncement(guildId)) {
			announcements.remove(getAnnouncement(guildId));
			return true;
		}
		return false;
	}

	public AnnouncementCreatorResponse confirmAnnouncement(long guildId) {
		if (hasAnnouncement(guildId)) {
			Announcement a = getAnnouncement(guildId);
			if (a.hasRequiredValues()) {
				DatabaseManager.getManager().updateAnnouncement(a);
				terminate(guildId);
				return new AnnouncementCreatorResponse(true, a);
			}
		}
		return new AnnouncementCreatorResponse(false);
	}

	//Getters

	/**
	 * Gets the Announcement in the creator for the guild.
	 *
	 * @param guildId The ID of the guild
	 * @return The Announcement in the creator for the guild.
	 */
	public Announcement getAnnouncement(long guildId) {
		for (Announcement a: announcements) {
			if (a.getGuildId() == guildId) {
				a.setLastEdit(System.currentTimeMillis());
				return a;
			}
		}
		return null;
	}

	public IMessage getCreatorMessage(long guildId) {
		if (hasAnnouncement(guildId))
			return getAnnouncement(guildId).getCreatorMessage();
		return null;
	}

	public ArrayList<Announcement> getAllAnnouncements() {
		return announcements;
	}

	//Setters
	public void setCreatorMessage(IMessage message) {
		if (message != null && hasCreatorMessage(message.getGuild().getLongID()))
			getAnnouncement(message.getGuild().getLongID()).setCreatorMessage(message);
	}

	//Booleans/Checkers

	/**
	 * Whether or not the Guild has an announcement in the creator.
	 *
	 * @param guildId The ID of the guild.
	 * @return <code>true</code> if active, else <code>false</code>.
	 */
	public boolean hasAnnouncement(long guildId) {
		for (Announcement a: announcements) {
			if (a.getGuildId() == guildId)
				return true;
		}
		return false;
	}

	public boolean hasCreatorMessage(long guildId) {
		return hasAnnouncement(guildId) && getAnnouncement(guildId).getCreatorMessage() != null;
	}
}