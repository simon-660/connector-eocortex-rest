
package com.evolveum.polygon.connector.eocortex.rest;


import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.framework.spi.AbstractConfiguration;
import org.identityconnectors.framework.spi.ConfigurationProperty;
import org.identityconnectors.framework.spi.StatefulConfiguration;

public class EoCortexRestConfiguration extends AbstractConfiguration {

    private String connectionUrl;
    private String username;
    private String password;
    private static final Log LOGGER = Log.getLog(EoCortexRestConnector.class);

    //TODO add a required to all of those
    @ConfigurationProperty(displayMessageKey = "connectionUrl.display", helpMessageKey = "connectionUrl.help")
    public String getConnectionUrl() {
        return connectionUrl;
    }

    public void setConnectionUrl(String connectionUrl) {
        this.connectionUrl = connectionUrl;
    }

    @ConfigurationProperty(displayMessageKey = "username.display", helpMessageKey = "username.help")
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    @ConfigurationProperty(displayMessageKey = "password.display", helpMessageKey = "password.help", confidential = true)
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public void validate() {
        if (connectionUrl == null || connectionUrl.isEmpty()) {
            throw new IllegalArgumentException("Connection URL is required.");
        }
        if (username == null || username.isEmpty()) {
            throw new IllegalArgumentException("Username is required.");
        }
        // Password might be optional based on your use case
    }
    

}
