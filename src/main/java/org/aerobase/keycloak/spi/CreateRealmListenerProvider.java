package org.aerobase.keycloak.spi;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.jboss.logging.Logger;
import org.keycloak.Config;
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

import com.google.common.net.InternetDomainName;

public class CreateRealmListenerProvider implements EventListenerProvider {
	protected static final Logger logger = Logger.getLogger(AdminRoot.class);

	private static final String REALM_TEMPLATE = "realm.json";
	private static final String CLIENT_SUFFIX = "client";
	private static final String CLIENT_SEPARATOR = "-";
	private static final String DEFAULT_THEMES_DIR_NAME = "themes";
	private static final String DEFAULT_THEME_LOGIN_NAME = "login";
	private static final String DEFAULT_AEROBASE_THEME_NAME = "aerobase";

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
			logger.debugv("Only master realm is pormited for this event: {0}", realmName);
			return;
		}

		// Prepare AdminAuth
		AdminAuth auth = new AdminAuth(masterRealm, null, session.users().getUserByUsername("admin", masterRealm),
				session.getContext().getClient());

		// Import realm first
		RealmRepresentation rep = loadJson(getClass().getResourceAsStream("/" + REALM_TEMPLATE),
				RealmRepresentation.class);

		// Replace strings if exists
		UserModel newUser = session.userStorageManager().getUserById(event.getUserId(), masterRealm);
		String newRealmName = toRealmName(newUser);
		rep.setRealm(newRealmName);
		rep.setLoginTheme(newRealmName);
		rep.setDisplayName(newRealmName);
		rep.setDisplayNameHtml(newRealmName);
		rep.setRegistrationAllowed(true);
		rep.setRegistrationEmailAsUsername(true);
		rep.setResetPasswordAllowed(true);
		rep.setRememberMe(true);
		rep.setVerifyEmail(true);

		RealmModel realm = session.realms().getRealmByName(newRealmName);
		// Double check realm doesn't exists
		if (realm == null) {
			logger.debugv("importRealm: {0}", rep.getRealm());
			importRealm(rep, auth, newUser, newRealmName);

			// Create default realm login theme and directory
			createDefaultRealm(rep);
		}
	}

	private void createClientIfAbsent(RealmModel realm) {
		String clientName = realm.getName() + CLIENT_SEPARATOR + CLIENT_SUFFIX;

		// Create default client for realm
		ClientModel client = session.realms().getClientByClientId(clientName, realm);
		if (client == null) {
			InternetDomainName domainName = InternetDomainName
					.from(session.getContext().getUri().getBaseUri().getHost());
			String topDomain = domainName.topPrivateDomain().toString();
			client = realm.addClient(clientName, clientName);

			client.setEnabled(true);

			client.setRootUrl(
					session.getContext().getUri().getBaseUri().getScheme() + "://" + realm.getName() + "." + topDomain);
			client.setRedirectUris(Stream.of("/*").collect(Collectors.toSet()));
			client.setBaseUrl("/");
			client.setManagementUrl("/");

			client.setStandardFlowEnabled(true);
			client.setPublicClient(true);
			client.setWebOrigins(Stream.of("*").collect(Collectors.toSet()));
		}
	}

	private void createDefaultRealm(RealmRepresentation rep) {
		String themesPath = Config.scope("theme").get("dir",
				System.getProperty("jboss.home.dir") + File.separator + DEFAULT_THEMES_DIR_NAME);

		File aerobaseLoginThemePath = new File(
				themesPath + File.separator + DEFAULT_AEROBASE_THEME_NAME + File.separator + DEFAULT_THEME_LOGIN_NAME);

		if (!Files.exists(new File(themesPath).toPath())) {
			logger.warnv("unable to file system path : {0}", themesPath);
		}

		String themePath = themesPath + File.separator + rep.getRealm();
		try {
			// Create parent theme directory
			Path newTheme = Files.createDirectory(new File(themePath).toPath());
			// Copy login theme from aerobase to new realm
			FileUtils.copyDirectory(aerobaseLoginThemePath,
					new File(newTheme.toString() + File.separator + DEFAULT_THEME_LOGIN_NAME), false);
		} catch (IOException e) {
			logger.warnv("unable to create system path : {0}", themePath);
		}
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
		return toRealmName(user.getUsername());
	}

	public static String toRealmName(String user) {
		return AccountNameMatcher.matches(user);
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
					
					// Reload realm from storage.
					RealmModel refresedRealm = session.realmLocalStorage().getRealmByName(realm.getName());
					grantPermissionsToRealmCreator(refresedRealm, auth, newUser);
					createClientIfAbsent(refresedRealm);
					ServicesLogger.LOGGER.importedRealm(refresedRealm.getName(), from);
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
