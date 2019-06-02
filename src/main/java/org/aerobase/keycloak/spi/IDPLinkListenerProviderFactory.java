package org.aerobase.keycloak.spi;

import org.keycloak.Config;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

public class IDPLinkListenerProviderFactory implements EventListenerProviderFactory {
	@Override
	public String getId() {
		return "aerobase-idp-link";
	}

	@Override
	public void init(Config.Scope config) {
		
	}

	@Override
	public void postInit(KeycloakSessionFactory factory) {
	}

	@Override
	public EventListenerProvider create(KeycloakSession session) {
		return new IDPLinkListenerProvider(session);
	}

	@Override
	public void close() {
	}
}
