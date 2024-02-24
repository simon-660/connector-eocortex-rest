package com.evolveum.polygon.connector.eocortex.rest;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class PlateDetails {
    static class Owner {
        @SerializedName("first_name")
        String firstName;
        @SerializedName("second_name")
        String secondName;
        @SerializedName("third_name")
        String thirdName;

        public String getFirstName() {
            return firstName;
        }

        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }

        public String getSecondName() {
            return secondName;
        }

        public void setSecondName(String secondName) {
            this.secondName = secondName;
        }

        public String getThirdName() {
            return thirdName;
        }

        public void setThirdName(String thirdName) {
            this.thirdName = thirdName;
        }

    }

    static class Group {
        String id;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }

    private Owner owner;
    private String id;
    private String external_id;
    private String external_sys_id;
    private String external_owner_id;
    private String license_plate_number;
    private String additional_info;
    private String model;
    private String color;
    private List<Group> groups;

    public PlateDetails() {
        this.owner = new Owner();
        this.groups = new ArrayList<>();
    }

    public Owner getOwner() {
        return owner;
    }

    public void setOwner(Owner owner) {
        this.owner = owner;
    }


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

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public List<Group> getGroups() {
        return groups;
    }

    public void setGroups(List<Group> groups) {
        this.groups = groups;
    }

    @Override
    public String toString() {
        String groupIds = groups.stream()
                .map(group -> group.id)
                .collect(Collectors.joining(", "));

        return "PlateDetails{" +
                "id='" + id + '\'' +
                ", external_id='" + external_id + '\'' +
                ", external_sys_id='" + external_sys_id + '\'' +
                ", external_owner_id='" + external_owner_id + '\'' +
                ", license_plate_number='" + license_plate_number + '\'' +
                ", additional_info='" + additional_info + '\'' +
                ", model='" + model + '\'' +
                ", color='" + color + '\'' +
                ", groups=" + groupIds +
                '}';
    }

}


