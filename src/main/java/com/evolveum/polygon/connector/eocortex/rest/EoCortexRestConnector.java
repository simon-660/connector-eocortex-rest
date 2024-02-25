
// Simplified version of GitlabRestConnector.java

package com.evolveum.polygon.connector.eocortex.rest;


import java.util.*;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.http.impl.client.CloseableHttpClient;
import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.FilterTranslator;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.Connector;
import org.identityconnectors.framework.spi.ConnectorClass;
import org.identityconnectors.framework.spi.operations.CreateOp;
import org.identityconnectors.framework.spi.operations.SchemaOp;
import org.identityconnectors.framework.spi.operations.SearchOp;
import org.identityconnectors.framework.spi.operations.TestOp;
import org.identityconnectors.framework.spi.operations.UpdateDeltaOp;
import org.identityconnectors.framework.spi.operations.DeleteOp;

@ConnectorClass(displayNameKey = "connector.gitlab.rest.display", configurationClass = EoCortexRestConfiguration.class)
public class EoCortexRestConnector
		implements TestOp, SchemaOp, Connector, CreateOp, DeleteOp, UpdateDeltaOp, SearchOp<Filter>  {

    private static final Log LOGGER = Log.getLog(EoCortexRestConnector.class);
    private EoCortexRestConfiguration configuration;
    private CloseableHttpClient httpClient;
    private EocortexApi api;

    @Override
	public Configuration getConfiguration() {
		return configuration;
	}

    @Override
    public void init(Configuration cfg) {
        LOGGER.info("eocortex : init operation invoked");

        this.configuration = (EoCortexRestConfiguration) cfg;

        //TODO this.configuration.validate();

        String apiUrl = this.configuration.getConnectionUrl();
        String username = this.configuration.getUsername();
        String password = this.configuration.getPassword();

        this.api = new EocortexApi(apiUrl, username, password);
        LOGGER.info("eocortex : init -> "+ apiUrl +" "+ username +" "+ password);
    }

    @Override
    public void dispose() {
        LOGGER.info("Dispose");
    }

    @Override
    public void test() {
        LOGGER.info("eocortex : Test operation invoked");

        LOGGER.info("eocortex : Test opeation result -> "+ this.api.testConnection());
    }

    @Override
    public Schema schema() {
        SchemaBuilder builder = new SchemaBuilder(EoCortexRestConnector.class);

        ObjectClassInfoBuilder objClassBuilder = new ObjectClassInfoBuilder();
        objClassBuilder.setType(ObjectClass.ACCOUNT_NAME); // or a custom object class name
        //objClassBuilder.setType("VehicleNumberPlate");

        AttributeInfoBuilder uidAib = new AttributeInfoBuilder(Uid.NAME);
        uidAib.setNativeName("entryUUID");
        uidAib.setType(String.class);
        uidAib.setRequired(false); // Must be optional. It is not present for create operations
        uidAib.setCreateable(false);
        uidAib.setUpdateable(false);
        uidAib.setReadable(true);
        objClassBuilder.addAttributeInfo(uidAib.build());

        AttributeInfoBuilder nameAib = new AttributeInfoBuilder(Name.NAME);
        nameAib.setType(String.class);
        nameAib.setNativeName("eoName");
        nameAib.setRequired(true);
        objClassBuilder.addAttributeInfo(nameAib.build());

        //objClassBuilder.addAttributeInfo(AttributeInfoBuilder.build(Name.NAME, String.class));
        objClassBuilder.addAttributeInfo(AttributeInfoBuilder.build("first_name", String.class));
        objClassBuilder.addAttributeInfo(AttributeInfoBuilder.build("second_name", String.class));
        objClassBuilder.addAttributeInfo(AttributeInfoBuilder.build("third_name", String.class));
        objClassBuilder.addAttributeInfo(AttributeInfoBuilder.build("external_id", String.class));
        objClassBuilder.addAttributeInfo(AttributeInfoBuilder.build("external_sys_id", String.class));
        objClassBuilder.addAttributeInfo(AttributeInfoBuilder.build("external_owner_id", String.class));
        objClassBuilder.addAttributeInfo(AttributeInfoBuilder.build("additional_info", String.class));
        objClassBuilder.addAttributeInfo(AttributeInfoBuilder.build("model", String.class));
        objClassBuilder.addAttributeInfo(AttributeInfoBuilder.build("color", String.class));

        AttributeInfoBuilder licensePlateNumberAib = new AttributeInfoBuilder("license_plate_number");
        licensePlateNumberAib.setType(String.class);
        licensePlateNumberAib.setMultiValued(true);
        objClassBuilder.addAttributeInfo(licensePlateNumberAib.build());

        AttributeInfoBuilder groupsIdAib = new AttributeInfoBuilder("groups_id");
        groupsIdAib.setType(String.class);
        groupsIdAib.setMultiValued(true);
        objClassBuilder.addAttributeInfo(groupsIdAib.build());

        builder.defineObjectClass(objClassBuilder.build());

        return builder.build();
    }

    @Override
    public Uid create(ObjectClass objClass, Set<Attribute> attributes, OperationOptions options) {
        LOGGER.info("eocortex : Create operation invoked");

        PersonPlates personPlates = new PersonPlates();
        List<PersonPlates.PlateDetails> plateDetailsList = new ArrayList<>();
        List<PersonPlates.Group> groupList = new ArrayList<>();

        // Extracting attributes
        String name = getAndValidateAttribute(attributes, Name.NAME);

        String firstName = getAndValidateAttribute(attributes, "first_name");
        String secondName = getAndValidateAttribute(attributes, "second_name");
        String thirdName = getAndValidateAttribute(attributes, "third_name");

        String externalId = getAndValidateAttribute(attributes, "external_id");
        String externalSysId = getAndValidateAttribute(attributes, "external_sys_id");
        String externalOwnerId = getAndValidateAttribute(attributes, "external_owner_id");
        String additionalInfo = getAndValidateAttribute(attributes, "additional_info");

        String model = getAndValidateAttribute(attributes, "model");
        String color = getAndValidateAttribute(attributes, "color");

        for (Attribute attr : attributes) {
            switch (attr.getName()) {
                case "license_plate_number":
                    List<Object> licensePlateNumbers = attr.getValue();
                    if (licensePlateNumbers != null) {
                        licensePlateNumbers.forEach(number -> {
                            PersonPlates.PlateDetails detail = new PersonPlates.PlateDetails();
                            detail.setLicense_plate_number(number.toString());
                            plateDetailsList.add(detail);
                        });
                    }
                    break;
                case "groups_id":
                    List<Object> groupsIds = attr.getValue();
                    if (groupsIds != null) {
                        groupsIds.forEach(id -> {
                            PersonPlates.Group group = new PersonPlates.Group();
                            group.setId(id.toString());
                            groupList.add(group);
                        });
                    }
                    break;
            }
        }

        //setting appropriate personPlates
        personPlates.setFirst_name(firstName);
        personPlates.setSecond_name(secondName);
        personPlates.setThird_name(thirdName);

        personPlates.setExternal_id(externalId);
        personPlates.setExternal_sys_id(externalSysId);
        personPlates.setExternal_owner_id(externalOwnerId);
        personPlates.setAdditional_info(additionalInfo);

        personPlates.setModel(model);
        personPlates.setColor(color);

        personPlates.setPlates(plateDetailsList);
        personPlates.setGroups(groupList);

        //generate PlateDetails for addCar
        List<String> uniqueCarJson = api.createPersonsAndPlates(personPlates);

        for(String json_car : uniqueCarJson){
            String result = api.addCar(json_car);
            LOGGER.info("EoCortex : Create car operation invoked result error -> " + api.hasError(result));
        }

        //return by unique ownerId
        //TODO extreme edge case to check if not in set
        return new Uid(externalOwnerId);
    }

    @Override
    public void delete(ObjectClass objClass, Uid uid, OperationOptions options) {
        LOGGER.info("eocortex : Delete operation invoked");

        //Get all plates query by uid
        List<PlateQueryData> plates_query = api.listByOwner(uid.getUidValue());

        //Iterate over and remove each plate belonging to the exact
        for(PlateQueryData plate_del : plates_query){
            String deleteResult = api.deleteCar(plate_del.getId()); //TODO turned off

            if(api.hasError(deleteResult)) {
                LOGGER.error("EoCortex : delete error :");
            } else {
                LOGGER.info("eocortex : delete ->"+ uid.getUidValue());
            }

        }
    }

    @Override
    public void executeQuery(ObjectClass objClass, Filter query, ResultsHandler handler, OperationOptions options) {
        LOGGER.info("Execute query operation invoked");
        List<PersonPlates> personPlatesList = new ArrayList<>();

        if (!objClass.is(ObjectClass.ACCOUNT_NAME)) {
            throw new IllegalArgumentException("Unsupported object class: " + objClass);
        }

        try {
            //TODO oštriť nenajdenu značku + thow special exception
            if (query instanceof EqualsFilter) { //One user Plate
                EqualsFilter equalsFilter = (EqualsFilter) query;
                String attributeName = equalsFilter.getAttribute().getName();
                Object attributeValue = equalsFilter.getAttribute().getValue().get(0);
                String plateId = (String) attributeValue;

                if (Uid.NAME.equals(attributeName)) {
                    //LOGGER.info("Query EqualsFilter - "+attributeName + " " + attributeValue + " : " +plate.getId());
                    try {
                        List<PlateQueryData> plates_query = api.listByOwner(plateId);
                        personPlatesList.add(api.convertQueryPersonPlate(plates_query));
                        LOGGER.info("EqualsFilter :"+ plateId);
                    }
                    catch(Exception e){
                        LOGGER.info("Error EqualsFilter for name: "+attributeName+" value: "+attributeValue);
                    }
                } else {
                    // Handle other attributes or throw an exception
                    throw new UnsupportedOperationException("Filter type not supported");
                }

            }
            else { //All plates
                personPlatesList = api.createAllPersonPlatesList();
                LOGGER.info("ALL Query got results : "+ personPlatesList.size());
            }

            ConnectorObjectBuilder cob = new ConnectorObjectBuilder();
            cob.setObjectClass(ObjectClass.ACCOUNT);

            for(PersonPlates personplate : personPlatesList){
                cob.setName(personplate.getExternal_owner_id());
                cob.setUid(personplate.getExternal_owner_id());

                addAttributeToBuilder(cob, "external_id", personplate.getExternal_id());
                addAttributeToBuilder(cob, "external_sys_id", personplate.getExternal_sys_id());
                addAttributeToBuilder(cob, "external_owner_id", personplate.getExternal_owner_id());
                addAttributeToBuilder(cob, "additional_info", personplate.getAdditional_info());

                addAttributeToBuilder(cob, "first_name", personplate.getFirst_name());
                addAttributeToBuilder(cob, "second_name", personplate.getSecond_name());
                addAttributeToBuilder(cob, "third_name", personplate.getThird_name());
                addAttributeToBuilder(cob, "model", personplate.getModel());
                addAttributeToBuilder(cob, "color", personplate.getColor());

                //Plates
                if(personplate.getPlates() != null) {
                    List<String> licensePlateNumbers = personplate.getPlates().stream()
                            .map(PersonPlates.PlateDetails::getLicense_plate_number)
                            .collect(Collectors.toList());

                    AttributeBuilder abPlates = new AttributeBuilder();
                    abPlates.setName("license_plate_number");
                    abPlates.addValue(licensePlateNumbers);
                    cob.addAttribute(abPlates.build());
                }

                //Groups
                if(personplate.getGroups() != null) {
                    List<String> groupIds = personplate.getGroups().stream()
                            .map(PersonPlates.Group::getId)
                            .collect(Collectors.toList());

                    AttributeBuilder abGroups = new AttributeBuilder();
                    abGroups.setName("groups_id");
                    abGroups.addValue(groupIds);
                    cob.addAttribute(abGroups.build());
                }

                LOGGER.info("Handling");
                if (!handler.handle(cob.build())) {
                    break;
                }
            }

        } catch (Exception e) {
            LOGGER.error("Error during query execution: {0}", e.getMessage());
        }
    }

    @Override
    public Set<AttributeDelta> updateDelta(ObjectClass objClass, Uid uid, Set<AttributeDelta> attrsDelta, OperationOptions options) {
        LOGGER.info("Update Delta operation invoked for UID: " + uid.getUidValue());

        if (!objClass.is(ObjectClass.ACCOUNT_NAME)) {
            throw new IllegalArgumentException("Unsupported object class: " + objClass);
        }

        //_.intersection(One, Two) -> not changed data
        //
        //_.difference(Two, One) -> new data
        //
        //_.difference(One, Two) -> removed data

        /*
        Gson gson = new Gson();
        try {
            String objectJson = api.getCarDetails(uid.getUidValue());
            LOGGER.info("Delta uid load "+ api.hasError(objectJson));

            JsonObject jsonCarObject = gson.fromJson(objectJson, JsonObject.class);

            for (AttributeDelta delta : attrsDelta) {
                String attributeName = delta.getName();
                JsonElement newValue = null;

                if (delta.getValuesToReplace() != null && !delta.getValuesToReplace().isEmpty()) {
                    newValue = gson.toJsonTree(delta.getValuesToReplace().get(0));

                    // Update top-level fields
                    if (jsonCarObject.has(attributeName)) {
                        LOGGER.info("Updating value top-level: "+ newValue);
                        jsonCarObject.add(attributeName, newValue);
                    }
                    // Update nested fields within "owner"
                    else if (jsonCarObject.has("owner") && jsonCarObject.getAsJsonObject("owner").has(attributeName)) {
                        jsonCarObject.getAsJsonObject("owner").add(attributeName, newValue);
                    }
                    // Handling the 'groups' attribute for a single value
                    // TODO prepare update for multiple values
                    else if ("group_id".equals(attributeName)) {
                        JsonArray groupsArray = jsonCarObject.getAsJsonArray("groups");
                        if (groupsArray == null) {
                            groupsArray = new JsonArray();
                        }
                        JsonObject groupObject = new JsonObject();
                        groupObject.add("id", newValue);
                        groupsArray.add(groupObject);
                        jsonCarObject.add("groups", groupsArray);
                    }

                    LOGGER.info("Processed delta for attribute: " + attributeName);
                }
            }

            String updatedJson = gson.toJson(jsonCarObject);
            String updateResponse = api.updateCar(uid.getUidValue(), updatedJson);
            if(api.hasError(updateResponse)) updateResponse = api.parseErrorMessage(updateResponse);
            else updateResponse = "OK";

            LOGGER.info("Size of updatedJson :" + updatedJson.length());

            LOGGER.info("Processed delta update ->" + updateResponse);

        } catch (Exception e) {
            LOGGER.error("Error during update delta operation: " + e.getMessage(), e);
            throw new ConnectorException("Error during update delta operation", e);
        }
         */
        return null;
    }
    
    @Override
	public FilterTranslator<Filter> createFilterTranslator(ObjectClass arg0, OperationOptions arg1) {
		return new FilterTranslator<Filter>() {
			@Override
			public List<Filter> translate(Filter filter) {
				return CollectionUtil.newList(filter);
			}
		};
	}

    private String getAndValidateAttribute(Set<Attribute> attributes, String attributeName) {
        if(attributes.isEmpty()) return null;

        for (Attribute attr:attributes) {
            String attrName = attr.getName();
            String value = null;
            //LOGGER.info("getAndValidateAttr : "+ attrName);
            //LOGGER.info("--->"+attributeName+" ,"+attrName);
            if(attrName.equals(attributeName)) {
                value = AttributeUtil.getStringValue(AttributeUtil.find(attributeName, attributes));
                LOGGER.info("getAndValidateAttr : value ->" + value);
                if (!Objects.equals(value, "")) return value;
                return null;
            }
        }
        return null;
    }

    private void addAttributeToBuilder(ConnectorObjectBuilder cob, String attributeName, Object attributeValue) {
        if (attributeValue != null) {
            cob.addAttribute(AttributeBuilder.build(attributeName, attributeValue));
        }
    }
}
