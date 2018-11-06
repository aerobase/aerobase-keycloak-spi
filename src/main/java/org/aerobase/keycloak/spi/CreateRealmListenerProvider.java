package org.aerobase.keycloak.spi;

import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jboss.logging.Logger;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.AdminRoles;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserModel;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.services.ServicesLogger;
import org.keycloak.services.managers.RealmManager;
import org.keycloak.services.resources.admin.AdminAuth;
import org.keycloak.services.resources.admin.AdminRoot;
import org.keycloak.util.JsonSerialization;

public class CreateRealmListenerProvider implements EventListenerProvider {
	protected static final Logger logger = Logger.getLogger(AdminRoot.class);

	private static final String REALM_TEMPLATE = "realm.json";

	private KeycloakSession session;
	private RealmModel masterRealm;

	public CreateRealmListenerProvider(KeycloakSession session) {
		this.session = session;
		this.masterRealm = new RealmManager(session).getKeycloakAdminstrationRealm();
	}

	@Override
	public void onEvent(Event event) {
		// Register events only
		if (event.getType() != EventType.REGISTER && event.getType() != EventType.IDENTITY_PROVIDER_FIRST_LOGIN) {
			return;	
		}

		String realmName = session.getContext().getRealm().getName();

		// Only master realm are supported on this event
		if (!masterRealm.getName().equals(realmName)) {
			return;
		}

		// Prepare AdminAuth
		AdminAuth auth = new AdminAuth(masterRealm, null, session.users().getUserByUsername("admin", masterRealm),
				session.getContext().getClient());

		// Import realm first
		RealmRepresentation rep = loadJson(getClass().getResourceAsStream("/" + REALM_TEMPLATE),
				RealmRepresentation.class);

		// Replace strings if exists
		UserModel newUser = session.users().getUserById(event.getUserId(), masterRealm);
		String newRealmName = toRealmName(newUser);
		rep.setRealm(newRealmName);

		logger.debugv("importRealm: {0}", rep.getRealm());

		importRealm(rep, auth, newUser, newRealmName);
	}

	@Override
	public void close() {

	}

	@Override
	public void onEvent(AdminEvent event, boolean includeRepresentation) {
		// Assume this implementation just ignores admin events
	}

	private void grantPermissionsToRealmCreator(RealmModel newRealm, AdminAuth auth, UserModel newUser) {
		if (auth.hasRealmRole(AdminRoles.ADMIN)) {
			return;
		}

		ClientModel realmAdminApp = newRealm.getMasterAdminClient();
		
		// Grant master admin to new realm resources
		for (String r : AdminRoles.ALL_REALM_ROLES) {
			RoleModel role = realmAdminApp.getRole(r);
			auth.getUser().grantRole(role);
		}
		
		// Grant new user to new realm resources
		for (String r : AdminRoles.ALL_REALM_ROLES) {
			RoleModel role = realmAdminApp.getRole(r);
			newUser.grantRole(role);
		}
	}

	public static <T> T loadJson(InputStream is, Class<T> type) {
		try {
			return JsonSerialization.readValue(is, type);
		} catch (IOException e) {
			throw new RuntimeException("Failed to parse json", e);
		}
	}

	public String toRealmName(UserModel user) {
		return AccountNameMatcher.matches(user.getUsername());
	}

	private static final class AccountNameMatcher {
		private static final Pattern pattern = Pattern.compile("[^a-zA-Z0-9]");

		public static String matches(String toMatch) {
			Matcher matcher = pattern.matcher(toMatch);

			return matcher.replaceAll("-").toLowerCase();
		}
	}

	public void importRealm(RealmRepresentation rep, AdminAuth auth, UserModel newUser, String from) {
		boolean exists = false;
		try {
			// session.getTransactionManager().begin();

			try {
				RealmManager manager = new RealmManager(session);
				manager.setContextPath(session.getContext().getContextPath());

				if (rep.getId() != null && manager.getRealm(rep.getId()) != null) {
					ServicesLogger.LOGGER.realmExists(rep.getRealm(), from);
					exists = true;
				}

				if (manager.getRealmByName(rep.getRealm()) != null) {
					ServicesLogger.LOGGER.realmExists(rep.getRealm(), from);
					exists = true;
				}
				if (!exists) {
					RealmModel realm = manager.importRealm(rep);
					grantPermissionsToRealmCreator(realm, auth, newUser);
					ServicesLogger.LOGGER.importedRealm(realm.getName(), from);
				}
			} catch (Throwable t) {
				if (!exists) {
					ServicesLogger.LOGGER.unableToImportRealm(t, rep.getRealm(), from);
				}
			}
		} catch (Throwable t) {
			ServicesLogger.LOGGER.unableToImportRealm(t, rep.getRealm(), from);
		}
	}
}
