package org.keycloak.theme;

import java.util.concurrent.ConcurrentHashMap;

import org.keycloak.models.KeycloakSession;
import org.keycloak.theme.ExtendingThemeManagerFactory.ThemeKey;

public class AerobaseExtendingThemeManager extends ExtendingThemeManager{

	public AerobaseExtendingThemeManager(KeycloakSession session, ConcurrentHashMap<ThemeKey, Theme> themeCache) {
		super(session, themeCache);
	}
	
}
