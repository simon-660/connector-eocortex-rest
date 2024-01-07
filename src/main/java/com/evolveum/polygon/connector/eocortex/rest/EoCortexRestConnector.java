
// Simplified version of GitlabRestConnector.java

package com.evolveum.polygon.connector.eocortex.rest;


import java.io.IOException;
import java.util.*;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.ConnectorIOException;
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException;
import org.identityconnectors.framework.common.objects.*;
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
        objClassBuilder.addAttributeInfo(AttributeInfoBuilder.build("firstName", String.class));
        objClassBuilder.addAttributeInfo(AttributeInfoBuilder.build("secondName", String.class));
        objClassBuilder.addAttributeInfo(AttributeInfoBuilder.build("thirdName", String.class));
        objClassBuilder.addAttributeInfo(AttributeInfoBuilder.build("externalId", String.class));
        objClassBuilder.addAttributeInfo(AttributeInfoBuilder.build("external_sys_id", String.class));
        objClassBuilder.addAttributeInfo(AttributeInfoBuilder.build("external_owner_id", String.class));
        objClassBuilder.addAttributeInfo(AttributeInfoBuilder.build("licensePlateNumber", String.class));
        objClassBuilder.addAttributeInfo(AttributeInfoBuilder.build("additionalInfo", String.class));
        objClassBuilder.addAttributeInfo(AttributeInfoBuilder.build("model", String.class));
        objClassBuilder.addAttributeInfo(AttributeInfoBuilder.build("color", String.class));
        objClassBuilder.addAttributeInfo(AttributeInfoBuilder.build("groupIds", String.class));

        builder.defineObjectClass(objClassBuilder.build());

        return builder.build();
    }

    @Override
    public Uid create(ObjectClass objClass, Set<Attribute> attributes, OperationOptions options) {
        LOGGER.info("eocortex : Create operation invoked");

        // Extracting attributes
        String name = getAndValidateAttribute(attributes, Name.NAME);
        String firstName = getAndValidateAttribute(attributes, "firstName");
        String secondName = getAndValidateAttribute(attributes, "secondName");
        String thirdName = getAndValidateAttribute(attributes, "thirdName");
        String externalId = getAndValidateAttribute(attributes, "externalId");
        String externalSysId = getAndValidateAttribute(attributes, "external_sys_id");
        String externalOwnerId = getAndValidateAttribute(attributes, "external_owner_id");
        String licensePlateNumber = getAndValidateAttribute(attributes, "licensePlateNumber");
        String additionalInfo = getAndValidateAttribute(attributes, "additionalInfo");
        String model = getAndValidateAttribute(attributes, "model");
        String color = getAndValidateAttribute(attributes, "color");
        String groupIds = getAndValidateAttribute(attributes, "groupIds");

        //TODO debug this
        //List<String> groupList = new ArrayList<>();
        //groupList.add(groupIds);

        String addCarJson = api.createJsonString(firstName, secondName, thirdName, externalId, externalSysId, externalOwnerId, licensePlateNumber, additionalInfo, model, color, null);

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
        String deleteResult = api.deleteCar(uid.getUidValue());

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

            // Process each car plate and convert it to a ConnectorObject
            for (PlateQueryData plate : plates) {
                ConnectorObjectBuilder cob = new ConnectorObjectBuilder();
                cob.setObjectClass(ObjectClass.ACCOUNT);

                //if name is empty the car may not be added by midpoint so for listing purposes return uid
                if (plate.getExternal_owner_id().isEmpty()) {
                    cob.setName(plate.getId());
                } else {
                    cob.setName(plate.getExternal_owner_id());
                }
                cob.setUid(plate.getId()); // 'id' should always be present for a UID

                // Add attributes, checking for null values
                addAttributeToBuilder(cob, "licensePlateNumber", plate.getLicense_plate_number());
                addAttributeToBuilder(cob, "externalId", plate.getExternal_id());
                addAttributeToBuilder(cob, "external_sys_id", plate.getExternal_sys_id());
                addAttributeToBuilder(cob, "external_owner_id", plate.getExternal_owner_id());
                addAttributeToBuilder(cob, "additionalInfo", plate.getAdditional_info());
                //addAttributeToBuilder(cob, "firstName", plate.get);
                //addAttributeToBuilder(cob, "secondName", plate.getSecondName());
                //addAttributeToBuilder(cob, "thirdName", plate.getThirdName());
                //addAttributeToBuilder(cob, "model", plate.getModel());
                //addAttributeToBuilder(cob, "color", plate.getColor());
                //addAttributeToBuilder(cob, "groupIds", plate.getGroupIds());

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
        LOGGER.info("Update Delta operation invoked");
        // Update Delta operation logic
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
