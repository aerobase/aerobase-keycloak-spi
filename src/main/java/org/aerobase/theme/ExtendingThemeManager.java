package org.aerobase.theme;

import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import javax.ws.rs.core.HttpHeaders;

import org.aerobase.keycloak.spi.CreateRealmListenerProvider;
import org.jboss.logging.Logger;
import org.keycloak.jose.jws.JWSInput;
import org.keycloak.jose.jws.JWSInputException;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.representations.AccessToken;
import org.keycloak.services.managers.RealmManager;
import org.keycloak.theme.ExtendingThemeManagerFactory.ThemeKey;
import org.keycloak.theme.Theme;

import com.google.common.base.Optional;

public class ExtendingThemeManager extends org.keycloak.theme.ExtendingThemeManager {
	protected static final Logger logger = Logger.getLogger(ExtendingThemeManager.class);

	private static final String BEARER_SCHEME = "Bearer";

	private final KeycloakSession session;
	private final boolean active;
	private RealmModel masterRealm;
	private static final Set<String> globalThemes = new HashSet<String>(
			Arrays.asList(new String[] { "aerobase", "aerobase-bootstrap" }));

	public ExtendingThemeManager(KeycloakSession session, ConcurrentHashMap<ThemeKey, Theme> themeCache,
			boolean active) {
		super(session, themeCache);
		this.session = session;
		this.active = active;
		this.masterRealm = new RealmManager(session).getKeycloakAdminstrationRealm();
	}

	@Override
	public int getProviderPriority() {
		return 0;
	}

	@Override
	public Set<String> nameSet(Theme.Type type) {
		Set<String> themes = super.nameSet(type);
		String realmName = session.getContext().getRealm().getName();
		String realmForUser = null;

		if (!active) {
			return themes;
		}

		AccessToken token = getTokenDataFromBearer(session.getContext().getRequestHeaders()).orNull();
		if (token != null && token.getPreferredUsername() != null) {
			realmForUser = CreateRealmListenerProvider.toRealmName(token.getPreferredUsername());
		}

		// Admin for master realm has access to all themes
		// TODO - check if user is master realm admin instead of using hardcoded 'admin'
		if (realmForUser == null || (realmForUser.equals("admin") && masterRealm.getName().equals(realmName))) {
			return themes;
		}

		// Prepare realm specific theme list
		// Exclude theme (based on type) if missing from original list
		Set<String> pThemes = globalThemes.stream().filter(theme -> themes.contains(theme)).collect(Collectors.toSet());

		// Realm for user is relevant when login from master realm using realm user.
		// Realm name is relevant when login directly from realm
		if (themes.contains(realmForUser)) {
			pThemes.add(realmForUser);
		} else if (themes.contains(realmName)) {
			pThemes.add(realmName);
		}

		return pThemes;
	}

	public Optional<AccessToken> getTokenDataFromBearer(HttpHeaders headers) {
		return getTokenDataFromBearer(getBarearToken(headers).orNull());
	}

	private Optional<AccessToken> getTokenDataFromBearer(String tokenString) {
		if (tokenString != null) {
			try {
				JWSInput input = new JWSInput(tokenString);
				return Optional.of(input.readJsonContent(AccessToken.class));
			} catch (JWSInputException e) {
				logger.debugf("could not parse token: ", e);
			}
		}

		return Optional.absent();
	}

	// Barear authentication allowed only using keycloack context
	public Optional<String> getBarearToken(HttpHeaders headers) {
		return getBarearToken(new Vector<String>(headers.getRequestHeader("Authorization")).elements());
	}

	// Barear authentication allowed only using keycloack context
	private static Optional<String> getBarearToken(Enumeration<String> headers) {
		if (headers == null || !headers.hasMoreElements()) {
			return Optional.absent();
		}

		String tokenString = null;
		while (headers.hasMoreElements()) {
			String[] split = headers.nextElement().trim().split("\\s+");
			if (split == null || split.length != 2)
				continue;
			if (!split[0].equalsIgnoreCase(BEARER_SCHEME))
				continue;
			tokenString = split[1];
		}

		if (tokenString == null) {
			return Optional.absent();
		}
		return Optional.of(tokenString);
	}
}
