package com.evolveum.polygon.connector.eocortex.rest;

public class PlateQueryData {

    private String id;
    private String external_id;
    private String external_sys_id;
    private String external_owner_id;
    private String license_plate_number;
    private String additional_info;
    private String modification_time;
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getExternal_id() {
        return external_id;
    }

    public void setExternal_id(String external_id) {
        this.external_id = external_id;
    }

    public String getExternal_sys_id() {
        return external_sys_id;
    }

    public void setExternal_sys_id(String external_sys_id) {
        this.external_sys_id = external_sys_id;
    }

    public String getExternal_owner_id() {
        return external_owner_id;
    }

    public void setExternal_owner_id(String external_owner_id) {
        this.external_owner_id = external_owner_id;
    }

    public String getLicense_plate_number() {
        return license_plate_number;
    }

    public void setLicense_plate_number(String license_plate_number) {
        this.license_plate_number = license_plate_number;
    }

    public String getAdditional_info() {
        return additional_info;
    }

    public void setAdditional_info(String additional_info) {
        this.additional_info = additional_info;
    }

    public String getModification_time() {
        return modification_time;
    }

    public void setModification_time(String modification_time) {
        this.modification_time = modification_time;
    }


}
