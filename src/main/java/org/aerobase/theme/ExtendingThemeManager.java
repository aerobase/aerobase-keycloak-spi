package org.aerobase.theme;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.services.managers.RealmManager;
import org.keycloak.theme.ExtendingThemeManagerFactory.ThemeKey;
import org.keycloak.theme.Theme;

public class ExtendingThemeManager extends org.keycloak.theme.ExtendingThemeManager {
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

		// Master realm has access to all themes
		if (masterRealm.getName().equals(realmName) || !active) {
			return themes;
		}

		// Prepare realm specific theme list
		// Exclude theme (based on type) if missing from original list
		Set<String> pThemes = globalThemes.stream().filter(theme -> themes.contains(theme)).collect(Collectors.toSet());

		if (themes.contains(realmName)) {
			pThemes.add(realmName);
		}

		return pThemes;
	}

}
