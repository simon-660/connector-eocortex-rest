package com.evolveum.polygon.connector.eocortex.rest;

import java.util.List;
import java.util.stream.Collectors;

public class PersonPlates {
    private String first_name;
    private String second_name;
    private String third_name;
    private String external_owner_id;
    private String model;
    private String color;
    private String additional_info;
    private String external_id;
    private String external_sys_id;
    private List<Group> groups;
    private List<PlateDetails> plates;

    // Constructor
    public PersonPlates() {
    }

    public static class PlateDetails {
        private String id;
        private String license_plate_number;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getLicense_plate_number() {
            return license_plate_number;
        }

        public void setLicense_plate_number(String license_plate_number) {
            this.license_plate_number = license_plate_number;
        }

        @Override
        public String toString() {
            return "{" +
                    "id='" + id + '\'' +
                    ", license_plate_number='" + license_plate_number + '\'' +
                    '}';
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

    public String getFirst_name() {
        return first_name;
    }

    public void setFirst_name(String first_name) {
        this.first_name = first_name;
    }

    public String getSecond_name() {
        return second_name;
    }

    public void setSecond_name(String second_name) {
        this.second_name = second_name;
    }

    public String getThird_name() {
        return third_name;
    }

    public void setThird_name(String third_name) {
        this.third_name = third_name;
    }

    public List<Group> getGroups() {
        return groups;
    }

    public void setGroups(List<Group> groups) {
        this.groups = groups;
    }

    public String getExternal_owner_id() {
        return external_owner_id;
    }

    public void setExternal_owner_id(String external_owner_id) {
        this.external_owner_id = external_owner_id;
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

    public String getAdditional_info() {
        return additional_info;
    }

    public void setAdditional_info(String additional_info) {
        this.additional_info = additional_info;
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

    public List<PlateDetails> getPlates() {
        return plates;
    }

    public void setPlates(List<PlateDetails> plates) {
        this.plates = plates;
    }

    @Override
    public String toString() {
        String platesString = plates.stream()
                .map(PlateDetails::toString)
                .collect(Collectors.joining(", "));

        String groupIds = groups.stream()
                .map(group -> group.id)
                .collect(Collectors.joining(", "));

        return "PersonPlates{" +
                "first_name='" + first_name + '\'' +
                ", second_name='" + second_name + '\'' +
                ", third_name='" + third_name + '\'' +
                ", external_owner_id='" + external_owner_id + '\'' +
                ", model='" + model + '\'' +
                ", color='" + color + '\'' +
                ", additional_info='" + additional_info + '\'' +
                ", external_id='" + external_id + '\'' +
                ", external_sys_id='" + external_sys_id + '\'' +
                ", groups=" + groupIds + + '\'' +
                ", plates=" + platesString +
                '}';
    }

}


