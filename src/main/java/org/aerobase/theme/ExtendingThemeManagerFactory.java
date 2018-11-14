package org.aerobase.theme;

import java.util.concurrent.ConcurrentHashMap;

import org.keycloak.Config;
import org.keycloak.Config.SystemPropertiesConfigProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.theme.ExtendingThemeManagerFactory.ThemeKey;
import org.keycloak.theme.Theme;
import org.keycloak.theme.ThemeProvider;
import org.keycloak.theme.ThemeProviderFactory;

public class ExtendingThemeManagerFactory implements ThemeProviderFactory {
	private ConcurrentHashMap<ThemeKey, Theme> themeCache;
	private boolean active;

	@Override
	public ThemeProvider create(KeycloakSession session) {
		return new ExtendingThemeManager(session, themeCache, active);
	}

	@Override
	public void init(Config.Scope config) {
		if (Config.scope("theme").getBoolean("cacheThemes", true)) {
			themeCache = new ConcurrentHashMap<>();
		}

		active = new SystemPropertiesConfigProvider().scope("theme").getBoolean("privateTemplates", false);
	}

	@Override
	public void postInit(KeycloakSessionFactory factory) {

	}

	@Override
	public void close() {
	}

	@Override
	public String getId() {
		return "extending";
	}
}
