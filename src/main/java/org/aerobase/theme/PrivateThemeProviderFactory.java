package org.aerobase.theme;

import java.io.File;

import org.keycloak.Config;
import org.keycloak.Config.SystemPropertiesConfigProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.theme.ThemeProvider;
import org.keycloak.theme.ThemeProviderFactory;

public class PrivateThemeProviderFactory implements ThemeProviderFactory {
	private File rootDir;
	private Boolean active;
	
	@Override
	public ThemeProvider create(KeycloakSession session) {
		return new PrivateThemeProvider(session, rootDir, active);
	}

	@Override
	public void init(Config.Scope config) {
        String d = config.get("dir");
        rootDir = null;
        if (d != null) {
            rootDir = new File(d);
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
		return "private";
	}
}
