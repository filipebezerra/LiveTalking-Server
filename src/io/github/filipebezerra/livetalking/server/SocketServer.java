package io.github.filipebezerra.livetalking.server;

import io.github.filipebezerra.livetalking.server.util.JSONUtils;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.common.collect.Maps;

/**
 *
 * @author Filipe Bezerra
 * @version 0.0.1
 * @since 24/11/2014
 *
 */
@ServerEndpoint("/chat")
public class SocketServer {

	private static final Log logger = LogFactory.getLog(SocketServer.class);

	/**
	 * set to store all the live sessions
	 */
	private static final Set<Session> sessions = Collections
			.synchronizedSet(new HashSet<Session>());

	/**
	 * Mapping between session and person name
	 */
	private static final HashMap<String, String> nameSessionPair = new HashMap<String, String>();

	private JSONUtils jsonUtils = new JSONUtils();

	/**
	 * Getting query params.
	 *
	 * @param query
	 * @return
	 */
	public static Map<String, String> getQueryMap(String query) {
		Map<String, String> map = Maps.newHashMap();
		if (query != null) {
			String[] params = query.split("&");
			for (String param : params) {
				String[] nameval = param.split("=");
				map.put(nameval[0], nameval[1]);
			}
		}
		return map;
	}

	/**
	 * Called when a socket connection opened
	 *
	 **/
	@OnOpen
	public void onOpen(Session session) {

		logger.debug(String.format("%s has opened a connection",
				session.getId()));

		Map<String, String> queryParams = getQueryMap(session.getQueryString());

		String name = "";

		if (queryParams.containsKey("name")) {

			// Getting client name via query param
			name = queryParams.get("name");
			try {
				name = URLDecoder.decode(name, "UTF-8");
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}

			// Mapping client name and session id
			nameSessionPair.put(session.getId(), name);
		}

		// Adding session to session list
		sessions.add(session);

		try {
			// Sending session id to the client that just connected
			session.getBasicRemote().sendText(
					jsonUtils.getClientDetailsJson(session.getId(),
							"Your session details"));
		} catch (IOException e) {
			e.printStackTrace();
		}

		// Notifying all the clients about new person joined
		sendMessageToAll(session.getId(), name, " joined conversation!", true,
				false);

	}

	/**
	 * method called when new message received from any client
	 *
	 * @param message
	 *            JSON message from client
	 **/
	@OnMessage
	public void onMessage(String message, Session session) {

		logger.debug(String.format("Message from %s: %s", session.getId(),
				message));

		String msg = null;

		// Parsing the json and getting message
		try {
			JSONObject jObj = new JSONObject(message);
			msg = jObj.getString("message");
		} catch (JSONException e) {
			e.printStackTrace();
		}

		// Sending the message to all clients
		sendMessageToAll(session.getId(), nameSessionPair.get(session.getId()),
				msg, false, false);
	}

	/**
	 * Method called when a connection is closed
	 *
	 **/
	@OnClose
	public void onClose(Session session) {

		logger.debug(String.format("Session %s has ended", session.getId()));

		// Getting the client name that exited
		String name = nameSessionPair.get(session.getId());

		// removing the session from sessions list
		sessions.remove(session);

		// Notifying all the clients about person exit
		sendMessageToAll(session.getId(), name, " left conversation!", false,
				true);

	}

	/**
	 * Method to send message to all clients
	 *
	 * @param sessionId
	 * @param message
	 *            message to be sent to clients
	 * @param isNewClient
	 *            flag to identify that message is about new person joined
	 * @param isExit
	 *            flag to identify that a person left the conversation
	 **/
	private void sendMessageToAll(String sessionId, String name,
			String message, boolean isNewClient, boolean isExit) {

		// Looping through all the sessions and sending the message individually
		for (Session s : sessions) {
			String json = null;

			// Checking if the message is about new client joined
			if (isNewClient) {
				json = jsonUtils.getNewClientJson(sessionId, name, message,
						sessions.size());

			} else if (isExit) {
				// Checking if the person left the conversation
				json = jsonUtils.getClientExitJson(sessionId, name, message,
						sessions.size());
			} else {
				// Normal chat conversation message
				json = jsonUtils
						.getSendAllMessageJson(sessionId, name, message);
			}

			try {
				logger.debug(String.format("Sending Message To: %s, %s", json,
						sessionId, json));

				s.getBasicRemote().sendText(json);
			} catch (IOException e) {
				logger.error(String.format("error in sending. %s", s.getId()),
						e);
			}
		}
	}

}
