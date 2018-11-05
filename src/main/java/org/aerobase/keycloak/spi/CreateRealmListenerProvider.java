package org.aerobase.keycloak.spi;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
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
import org.keycloak.models.ModelDuplicateException;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserModel;
import org.keycloak.policy.PasswordPolicyNotMetException;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.services.managers.RealmManager;
import org.keycloak.services.resources.admin.AdminAuth;
import org.keycloak.services.resources.admin.AdminRoot;
import org.keycloak.services.resources.admin.permissions.AdminPermissions;
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

		// Create New Realm
		RealmManager realmManager = new RealmManager(session);
		realmManager.setContextPath(session.getContext().getContextPath());

		// Prepare AdminAuth
		AdminAuth auth = new AdminAuth(masterRealm, null, session.users().getUserByUsername("admin", masterRealm),
				session.getContext().getClient());

		// Double check rules
		AdminPermissions.realms(session, auth).requireCreateRealm();

		// Import realm first
		RealmRepresentation rep = loadJson(getClass().getResourceAsStream("/" + REALM_TEMPLATE),
				RealmRepresentation.class);

		// Replace strings if exists
		rep.setRealm(toRealmName(session.users().getUserById(event.getUserId(), masterRealm)));

		logger.debugv("importRealm: {0}", rep.getRealm());

		try {
			RealmModel realm = realmManager.importRealm(rep);
			grantPermissionsToRealmCreator(realm, auth);

			URI location = AdminRoot.realmsUrl(session.getContext().getUri()).path(realm.getName()).build();
			logger.debugv("imported realm success, sending back: {0}", location.toString());
		} catch (ModelDuplicateException e) {
			logger.error("Conflict detected", e);
		} catch (PasswordPolicyNotMetException e) {
			logger.error("Password policy not met for user " + e.getUsername(), e);
			if (session.getTransactionManager().isActive())
				session.getTransactionManager().setRollbackOnly();
		}
	}

	@Override
	public void close() {

	}

	@Override
	public void onEvent(AdminEvent event, boolean includeRepresentation) {
		// Assume this implementation just ignores admin events
	}

	private void grantPermissionsToRealmCreator(RealmModel realm, AdminAuth auth) {
		if (auth.hasRealmRole(AdminRoles.ADMIN)) {
			return;
		}

		ClientModel realmAdminApp = realm.getMasterAdminClient();
		for (String r : AdminRoles.ALL_REALM_ROLES) {
			RoleModel role = realmAdminApp.getRole(r);
			auth.getUser().grantRole(role);
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
}
