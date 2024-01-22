
// Simplified version of GitlabRestConnector.java

package com.evolveum.polygon.connector.eocortex.rest;


import java.io.IOException;
import java.util.*;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.ConnectorIOException;
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException;
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
        //this.httpClient = HttpClientBuilder.create().build();

        String apiUrl = this.configuration.getConnectionUrl();
        String username = this.configuration.getUsername();
        String password = this.configuration.getPassword();

        this.api = new EocortexApi(apiUrl, username, password);
        LOGGER.info("eocortex : init -> "+ apiUrl +" "+ username +" "+ password);
    }

    @Override
    public void dispose() {
        LOGGER.info("Dispose");
        // Simplified dispose logic
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
        objClassBuilder.addAttributeInfo(AttributeInfoBuilder.build("license_plate_number", String.class));
        objClassBuilder.addAttributeInfo(AttributeInfoBuilder.build("additional_info", String.class));
        objClassBuilder.addAttributeInfo(AttributeInfoBuilder.build("model", String.class));
        objClassBuilder.addAttributeInfo(AttributeInfoBuilder.build("color", String.class));
        objClassBuilder.addAttributeInfo(AttributeInfoBuilder.build("groups", String.class));

        builder.defineObjectClass(objClassBuilder.build());

        return builder.build();
    }

    @Override
    public Uid create(ObjectClass objClass, Set<Attribute> attributes, OperationOptions options) {
        LOGGER.info("eocortex : Create operation invoked");

        // Extracting attributes
        String name = getAndValidateAttribute(attributes, Name.NAME);
        String firstName = getAndValidateAttribute(attributes, "first_name");
        String secondName = getAndValidateAttribute(attributes, "second_name");
        String thirdName = getAndValidateAttribute(attributes, "third_name");
        String externalId = getAndValidateAttribute(attributes, "external_id");
        String externalSysId = getAndValidateAttribute(attributes, "external_sys_id");
        String externalOwnerId = getAndValidateAttribute(attributes, "external_owner_id");
        String licensePlateNumber = getAndValidateAttribute(attributes, "license_plate_number");
        String additionalInfo = getAndValidateAttribute(attributes, "additional_info");
        String model = getAndValidateAttribute(attributes, "model");
        String color = getAndValidateAttribute(attributes, "color");
        String groupIds = getAndValidateAttribute(attributes, "groups");

        List<String> groupList = null; // Initialize groupList as null

        if (groupIds != null && !groupIds.isEmpty()) {
            groupList = new ArrayList<>();
            groupList.add(groupIds);
        }

        String addCarJson = api.createJsonString(firstName, secondName, thirdName, externalId, externalSysId, externalOwnerId, licensePlateNumber, additionalInfo, model, color, groupList);

        //LOGGER.info("eocortex : Create op info ->"+ externalSysId + "," + name + " ");

        //TODO nemôžem posielať json do logu lebo sa ho snaži spracovať !!
        //LOGGER.info("eocortex : Create op info ->" + addCarJson);

        String addCarResult = api.addCar(addCarJson);

        String uidNewCar = null;
        if(!api.hasError(addCarResult)) uidNewCar = api.parseUidFromResponse(addCarResult);

        LOGGER.info("EoCortex : Create car id ->" + uidNewCar);

        return new Uid(uidNewCar);
    }

    @Override
    public void delete(ObjectClass objClass, Uid uid, OperationOptions options) {
        LOGGER.info("eocortex : Delete operation invoked");

        //String carFindUid = api.parseUidFromResponse(api.findCars(uid.getUidValue(),null));
        //TODO TURNED OFF
        String deleteResult = "{\"ErrorMessage\":\"TURNED OFF - funkcia zakomentovana\"}";
        //api.deleteCar(uid.getUidValue());

        if(api.hasError(deleteResult)) {
            LOGGER.error("EoCortex : delete error :"+ api.parseErrorMessage(deleteResult));
        } else {
            LOGGER.info("eocortex : delete ->"+ uid.getUidValue());
        }
    }

    @Override
    public void executeQuery(ObjectClass objClass, Filter query, ResultsHandler handler, OperationOptions options) {
        LOGGER.info("Execute query operation invoked");

        //TODO learn how to filters work
        if (!objClass.is(ObjectClass.ACCOUNT_NAME)) {
            throw new IllegalArgumentException("Unsupported object class: " + objClass);
        }

        // Call the method to get all car plates
        try {
            List<PlateQueryData> plates = api.getAllCars();

            LOGGER.info("Query got results : "+ plates.size());

            // Process each car plate and convert it to a ConnectorObject
            for (PlateQueryData plate : plates) {

                if (query instanceof EqualsFilter) {
                    EqualsFilter equalsFilter = (EqualsFilter) query;
                    String attributeName = equalsFilter.getAttribute().getName();
                    Object attributeValue = equalsFilter.getAttribute().getValue().get(0);
                    LOGGER.info(Uid.NAME + " " + attributeName);
                    if (Uid.NAME.equals(attributeName)) {
                        LOGGER.info("Query EqualsFilter - "+attributeName + " " + attributeValue + " : " +plate.getId());
                        if(plate.getId().equals(attributeValue)) LOGGER.info("Query EqualsFilter found id");
                        else continue;
                    } else {
                        // Handle other attributes or throw an exception
                        throw new UnsupportedOperationException("Filter type not supported");
                    }
                }

                ConnectorObjectBuilder cob = new ConnectorObjectBuilder();
                cob.setObjectClass(ObjectClass.ACCOUNT);

                //if name is empty the car may not be added by midpoint so for listing purposes return uid
                if (plate.getExternal_owner_id().isEmpty()) {
                    //cob.setName(plate.getId());
                    cob.setName(plate.getLicense_plate_number());
                } else {
                    cob.setName(plate.getExternal_owner_id());
                }
                cob.setUid(plate.getId()); // 'id' should always be present for a UID

                addAttributeToBuilder(cob, "license_plate_number", plate.getLicense_plate_number());
                addAttributeToBuilder(cob, "external_id", plate.getExternal_id());
                addAttributeToBuilder(cob, "external_sys_id", plate.getExternal_sys_id());
                addAttributeToBuilder(cob, "external_owner_id", plate.getExternal_owner_id());
                addAttributeToBuilder(cob, "additional_info", plate.getAdditional_info());

                Gson gson = new Gson();
                String objectJson = api.getCarDetails(plate.getId());
                if(!api.hasError(objectJson)){
                    LOGGER.info("Query details");
                    JsonObject jsonCarObject = gson.fromJson(objectJson, JsonObject.class);

                    // Accessing nested fields within "owner"
                    if (jsonCarObject.has("owner")) {
                        JsonObject ownerObject = jsonCarObject.getAsJsonObject("owner");
                        if (ownerObject.has("first_name")) addAttributeToBuilder(cob, "first_name", ownerObject.get("first_name").getAsString());
                        if (ownerObject.has("second_name")) addAttributeToBuilder(cob, "second_name", ownerObject.get("second_name").getAsString());
                        if (ownerObject.has("third_name")) addAttributeToBuilder(cob, "third_name", ownerObject.get("third_name").getAsString());
                        if (ownerObject.has("first_name")) LOGGER.info("Query details"+ ownerObject.get("first_name").getAsString());
                    }

                    // Accessing top-level fields
                    if (jsonCarObject.has("model")) addAttributeToBuilder(cob, "model", jsonCarObject.get("model").getAsString());
                    if (jsonCarObject.has("color")) addAttributeToBuilder(cob, "color", jsonCarObject.get("color").getAsString());
                    //TODO//if (jsonCarObject.has("groups")) addAttributeToBuilder(cob, "groups", jsonCarObject.get("groups").getAsString());
                }

                // Pass the ConnectorObject to the handler
                if (!handler.handle(cob.build())) {
                    break;
                }
            }
        } catch (Exception e) {
            //TODO investigate logger parameters if this is ok ?
            LOGGER.error("Error during query execution: {0}", e.getMessage());
        }
    }

    @Override
    public Set<AttributeDelta> updateDelta(ObjectClass objClass, Uid uid, Set<AttributeDelta> attrsDelta, OperationOptions options) {
        LOGGER.info("Update Delta operation invoked for UID: " + uid.getUidValue());

        if (!objClass.is(ObjectClass.ACCOUNT_NAME)) {
            throw new IllegalArgumentException("Unsupported object class: " + objClass);
        }

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
                        jsonCarObject.add(attributeName, newValue);
                    }
                    // Update nested fields within "owner"
                    else if (jsonCarObject.has("owner") && jsonCarObject.getAsJsonObject("owner").has(attributeName)) {
                        jsonCarObject.getAsJsonObject("owner").add(attributeName, newValue);
                    }

                    LOGGER.info("Processed delta for attribute: " + attributeName);
                }
            }

            String updatedJson = gson.toJson(jsonCarObject);
            String updateResponse = api.updateCar(uid.getUidValue(), updatedJson);
            if(api.hasError(updateResponse)) updateResponse = api.parseErrorMessage(updateResponse);
            else updateResponse = "OK";

            LOGGER.info("Processed delta update ->" + updateResponse);

        } catch (Exception e) {
            LOGGER.error("Error during update delta operation: " + e.getMessage(), e);
            throw new ConnectorException("Error during update delta operation", e);
        }
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
