package org.dreamexposure.discal.server.api.endpoints.v2.calendar;

import com.google.api.services.calendar.Calendar;

import org.dreamexposure.discal.core.calendar.CalendarAuth;
import org.dreamexposure.discal.core.database.DatabaseManager;
import org.dreamexposure.discal.core.logger.Logger;
import org.dreamexposure.discal.core.object.GuildSettings;
import org.dreamexposure.discal.core.object.calendar.CalendarData;
import org.dreamexposure.discal.core.object.web.AuthenticationState;
import org.dreamexposure.discal.core.utils.CalendarUtils;
import org.dreamexposure.discal.core.utils.JsonUtils;
import org.dreamexposure.discal.core.utils.TimeZoneUtils;
import org.dreamexposure.discal.server.utils.Authentication;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import discord4j.core.object.util.Snowflake;

@RestController
@RequestMapping("/v2/calendar")
public class UpdateEndpoint {
	@PostMapping(value = "/update", produces = "application/json")
	public String updateCalendar(HttpServletRequest request, HttpServletResponse response, @RequestBody String requestBody) {
		//Authenticate...
		AuthenticationState authState = Authentication.authenticate(request);
		if (!authState.isSuccess()) {
			response.setStatus(authState.getStatus());
			response.setContentType("application/json");
			return authState.toJson();
		} else if (authState.isReadOnly()) {
			response.setStatus(401);
			response.setContentType("application/json");
			return JsonUtils.getJsonResponseMessage("Read-Only key not Allowed");
		}

		//Okay, now handle actual request.
		try {
			JSONObject jsonMain = new JSONObject(requestBody);
			Snowflake guildId = Snowflake.of(jsonMain.getLong("guild_id"));
			int calNumber = jsonMain.getInt("calendar_number");

			GuildSettings settings = DatabaseManager.getManager().getSettings(guildId);
			CalendarData calData = DatabaseManager.getManager().getCalendar(guildId, calNumber);

			if (!calData.getCalendarAddress().equalsIgnoreCase("primary")
					&& CalendarUtils.calendarExists(calData, settings)) {
				Calendar service = CalendarAuth.getCalendarService(settings);
				com.google.api.services.calendar.model.Calendar cal = service.calendars()
						.get(calData.getCalendarAddress())
						.execute();

				if (jsonMain.has("summary"))
					cal.setSummary(jsonMain.getString("summary"));
				if (jsonMain.has("description"))
					cal.setDescription("description");
				if (jsonMain.has("timezone")) {
					String tzRaw = jsonMain.getString("timezone");
					if (TimeZoneUtils.isValid(tzRaw)) {
						cal.setTimeZone(tzRaw);
					}
				}

				com.google.api.services.calendar.model.Calendar confirmed = service.calendars()
						.update(calData.getCalendarAddress(), cal)
						.execute();

				response.setContentType("application/json");
				if (confirmed != null) {
					response.setStatus(200);
					return JsonUtils.getJsonResponseMessage("Calendar successfully updated");
				} else {
					response.setStatus(500);
					return JsonUtils.getJsonResponseMessage("Calendar update failed. Perhaps google is at fault!");
				}
			} else {
				response.setContentType("application/json");
				response.setStatus(404);
				return JsonUtils.getJsonResponseMessage("Calendar not found");
			}
		} catch (JSONException e) {
			e.printStackTrace();

			response.setContentType("application/json");
			response.setStatus(400);
			return JsonUtils.getJsonResponseMessage("Bad Request");
		} catch (Exception e) {
			Logger.getLogger().exception(null, "[API-v2] Internal get calendar error", e, true, this.getClass());

			response.setContentType("application/json");
			response.setStatus(500);
			return JsonUtils.getJsonResponseMessage("Internal Server Error");
		}
	}
}