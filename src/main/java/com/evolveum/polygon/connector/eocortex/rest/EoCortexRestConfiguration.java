
package com.evolveum.polygon.connector.eocortex.rest;

import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.spi.AbstractConfiguration;
import org.identityconnectors.framework.spi.ConfigurationProperty;

public class EoCortexRestConfiguration extends AbstractConfiguration {

    private String connectionUrl;
    private String username;
    private String password;
    private String external_sys_id;
    private String additional_info;
    private String default_group;
    private int response_portion;

    private static final Log LOGGER = Log.getLog(EoCortexRestConnector.class);

    @ConfigurationProperty(displayMessageKey = "connectionUrl.display", helpMessageKey = "connectionUrl.help", required = true)
    public String getConnectionUrl() {
        return connectionUrl;
    }

    public void setConnectionUrl(String connectionUrl) {
        this.connectionUrl = connectionUrl;
    }

    @ConfigurationProperty(displayMessageKey = "username.display", helpMessageKey = "username.help", required = true)
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    @ConfigurationProperty(displayMessageKey = "password.display", helpMessageKey = "password.help", confidential = true, required = true)
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @ConfigurationProperty(displayMessageKey = "externalSysId.display", helpMessageKey = "externalSysId.help")
    public String getExternal_sys_id() {return external_sys_id;}

    public void setExternal_sys_id(String external_sys_id) {this.external_sys_id = external_sys_id;}

    @ConfigurationProperty(displayMessageKey = "additionalInfo.display", helpMessageKey = "additionalInfo.help")
    public String getAdditional_info() {return additional_info;}

    public void setAdditional_info(String additional_info) {this.additional_info = additional_info;}

    @ConfigurationProperty(displayMessageKey = "defaultGroup.display", helpMessageKey = "defaultGroup.help")
    public String getDefault_group() {return default_group;}

    public void setDefault_group(String default_group) {this.default_group = default_group;}

    @ConfigurationProperty(displayMessageKey = "responsePortion.display", helpMessageKey = "responsePortion.help")
    public int getResponse_portion() {
        return response_portion;
    }

    public void setResponse_portion(int response_portion) {
        this.response_portion = response_portion;
    }

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

        //external_sys_id is not required as null or empty will turn off filter
        //Additional info can be empty;
        //Additional group can be empty;
    }
    

}
