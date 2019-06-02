package org.aerobase.keycloak.spi;

import java.util.ArrayList;
import java.util.Arrays;

import org.jboss.logging.Logger;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.UserModel;
import org.keycloak.services.resources.admin.AdminRoot;

public class IDPLinkListenerProvider implements EventListenerProvider {
	protected static final Logger logger = Logger.getLogger(AdminRoot.class);
	private static final String PROVIDER_KEY = "identity_provider";
	private static final String PROVIDER_LINKS_KEY = "IDENTITY_PROVIDER_LINKS";

	private KeycloakSession session;

	public IDPLinkListenerProvider(KeycloakSession session) {
		this.session = session;
	}

	@Override
	public void onEvent(Event event) {
		// Register events only
		if (event.getType() != EventType.LOGIN || event.getDetails() == null
				|| !event.getDetails().containsKey(PROVIDER_KEY) || event.getUserId() == null) {
			return;
		}

		System.out.println(System.currentTimeMillis());
		// Prepare getUser
		UserModel loggedinUser = session.users().getUserById(event.getUserId(), session.getContext().getRealm());
		String newLink = event.getDetails().get(PROVIDER_KEY);

		String userIdpLink = loggedinUser.getFirstAttribute(PROVIDER_LINKS_KEY);
		loggedinUser.setSingleAttribute(PROVIDER_LINKS_KEY, putIfAbsent(userIdpLink, newLink).toString());

		System.out.println(System.currentTimeMillis());
	}

	private ArrayList<String> putIfAbsent(String userIdpLink, String newLink) {
		ArrayList<String> links = new ArrayList<String>();

		if (userIdpLink == null || userIdpLink.isEmpty()) {
			links.add(newLink);
			return links;
		}

		// Remove array like syntax and split
		String[] linksStr = userIdpLink.replaceAll("[\\]\\[ ]", "").split(",");
		links.addAll(Arrays.asList(linksStr));
		if (!links.contains(newLink)) {
			links.add(newLink);
		}

		return links;
	}

	@Override
	public void close() {

	}

	@Override
	public void onEvent(AdminEvent event, boolean includeRepresentation) {

	}

}
