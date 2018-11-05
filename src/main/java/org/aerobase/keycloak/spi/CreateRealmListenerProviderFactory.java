package org.aerobase.keycloak.spi;

import org.keycloak.Config;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

public class CreateRealmListenerProviderFactory implements EventListenerProviderFactory {
	@Override
	public String getId() {
		return "aerobase-post-registration";
	}

	@Override
	public void init(Config.Scope config) {
		
	}

	@Override
	public void postInit(KeycloakSessionFactory factory) {
	}

	@Override
	public EventListenerProvider create(KeycloakSession session) {
		return new CreateRealmListenerProvider(session);
	}

	@Override
	public void close() {
	}
}
