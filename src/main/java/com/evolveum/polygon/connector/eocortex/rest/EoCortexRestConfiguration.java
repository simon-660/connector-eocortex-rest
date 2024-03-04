
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
    private String external_sys_id;
    private String additional_info;
    private String default_group;

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

    public String getExternal_sys_id() {return external_sys_id;}

    public void setExternal_sys_id(String external_sys_id) {this.external_sys_id = external_sys_id;}

    public String getAdditional_info() {return additional_info;}

    public void setAdditional_info(String additional_info) {this.additional_info = additional_info;}

    public String getDefault_group() {return default_group;}

    public void setDefault_group(String default_group) {this.default_group = default_group;}

    @Override
    public void validate() {
        if (connectionUrl == null || connectionUrl.isEmpty()) {
            throw new IllegalArgumentException("Connection URL is required.");
        }
        if (username == null || username.isEmpty()) {
            throw new IllegalArgumentException("Username is required.");
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password is required.");
        }
        //external_sys_id is required for delivering specific groups of plates
        if (external_sys_id == null || external_sys_id.isEmpty()) {
            throw new IllegalArgumentException("external_sys_id is required.");
        }
        //Additional info can be empty;
        //Additional group can be empty;
    }
    

}
