package com.evolveum.polygon.connector.eocortex.rest;

import java.util.*;
import java.util.stream.Collectors;

import org.apache.http.impl.client.CloseableHttpClient;
import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.AlreadyExistsException;
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
import org.json.JSONException;
import org.json.JSONObject;

@ConnectorClass(displayNameKey = "connector.gitlab.rest.display", configurationClass = EoCortexRestConfiguration.class)
public class EoCortexRestConnector
		implements TestOp, SchemaOp, Connector, CreateOp, DeleteOp, UpdateDeltaOp, SearchOp<Filter>  {

    private static final Log LOGGER = Log.getLog(EoCortexRestConnector.class);
    private EoCortexRestConfiguration configuration;
    private CloseableHttpClient httpClient;
    private EocortexApi api;

    //Rudamentary for getting the configuration from another class
    @Override
	public Configuration getConfiguration() {
		return configuration;
	}

    //Initialisation occures when the connector starts interacting with connector, when doint in bursts it initialises once,
    //but ehen the dispose is called there will be another initialisation for another workload
    @Override
    public void init(Configuration cfg) {
        LOGGER.info("eocortex : init operation invoked");

        this.configuration = (EoCortexRestConfiguration) cfg;

        this.configuration.validate(); //TODO check

        String apiUrl = this.configuration.getConnectionUrl();
        String username = this.configuration.getUsername();
        String password = this.configuration.getPassword();


        this.api = new EocortexApi(apiUrl, username, password, 20000);
        //LOGGER.info("eocortex : init -> "+ apiUrl +" "+ username +" "+ password);
        LOGGER.info("eocortex : init -> "+ apiUrl +" "+ username); //less verbose version for secret free log !
    }

    //This method is here for the connectors that need to have special type of connection to the external system,
    //In this case this does not apply for Rest Api call to MidPoint as they are created and closed per request
    @Override
    public void dispose() {
        //All the tasks with connector has been done
        LOGGER.info("Dispose");
    }

    //This is invoked with the other stuff when test is invoked in MidPoint connector setting. Based on external API type this calls
    //the api to get the simple response and loggs the result.
    @Override
    public void test() {
        LOGGER.info("eocortex : Test operation invoked");

        LOGGER.info("eocortex : Test opeation result -> "+ this.api.testConnection());
    }

    //This method is the definition of schema (of the external system EoCortex) and is being presented to the midPoint
    //First two elements are internal identicators for the cross system identification MidPoint -> EoCortex
    @Override
    public Schema schema() {
        SchemaBuilder builder = new SchemaBuilder(EoCortexRestConnector.class);

        ObjectClassInfoBuilder objClassBuilder = new ObjectClassInfoBuilder();
        objClassBuilder.setType(ObjectClass.ACCOUNT_NAME); // or a custom object class name
        //objClassBuilder.setType("VehicleNumberPlate");

        AttributeInfoBuilder uidAib = new AttributeInfoBuilder(Uid.NAME);
        uidAib.setNativeName("entryUUID");
        uidAib.setType(String.class);
        uidAib.setRequired(false); // Must be optional. It is not present for create operations (before it)
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


    //The most important method used when identity provides link with external system and there are no numberplates for unique UID,
    //system know the state and calls this instead of updateDelta based on information fetched from calling of executeQuery.
    @Override
    public Uid create(ObjectClass objClass, Set<Attribute> attributes, OperationOptions options) {
        //Random UUID for identification of logs from unique call
        String processIdentifier = UUID.randomUUID().toString();

        LOGGER.info("EoCortex : " + processIdentifier + " : Create operation invoked");

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

        //Creation of new identity with system id based on settings when empty
        if(externalSysId == null){
            externalSysId = this.configuration.getExternal_sys_id();
        }

        //Additional info if empty, use configured variable
        if(additionalInfo == null){
            additionalInfo = this.configuration.getAdditional_info();
        }

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

        //empty groups id add default from settings
        if(!this.configuration.getDefault_group().isEmpty() || this.configuration.getDefault_group() != null) {
            if (groupList.isEmpty()) {
                PersonPlates.Group group = new PersonPlates.Group();
                group.setId(this.configuration.getDefault_group());
                groupList.add(group);
            }
            else{
                boolean groupExists = groupList.stream() //if do not exists in current group_id schema add it from settings
                        .anyMatch(group -> group.getId().equals(this.configuration.getDefault_group()));
                if (!groupExists) {
                    PersonPlates.Group group = new PersonPlates.Group();
                    group.setId(this.configuration.getDefault_group());
                    groupList.add(group);
                }
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

            String licensePlateNumber = null;
            String uid_login = null;

            try{
                JSONObject carJson = new JSONObject(json_car);
                licensePlateNumber = carJson.getString("license_plate_number");
                uid_login = carJson.getString("external_owner_id");

                String result = api.addCar(json_car);

                if (api.hasError(result)) {
                    String errorMessage = api.extractErrorMessage(result);
                    LOGGER.error("EoCortex : " + processIdentifier + " : Create operation for license_plate : "+ licensePlateNumber +" : owner_id : "+ uid_login +" : operation error -> " + errorMessage);
                    throw new AlreadyExistsException("EoCortex : Create car for license_plate : "+ licensePlateNumber +" : owner_id : "+ uid_login +" : operation error -> " + errorMessage);
                } else {
                    LOGGER.info("EoCortex : " + processIdentifier + " : Create car operation for license_plate : "+ licensePlateNumber +", successfull");
                }

            } catch (JSONException e) {
                LOGGER.error("EoCortex : " + processIdentifier + " : Create car operation for license_plate : "+ licensePlateNumber +" : owner_id : "+ uid_login +" : operation error -> Cannot parse license_plate_number for logs");
            }

        }

        return new Uid(externalOwnerId);
    }

    //This method makes sure that when conditions are met and MidPoint call this, all the necessary numberplate records,
    //are removed from external EoCortex API to reach consistent state between both systems.
    @Override
    public void delete(ObjectClass objClass, Uid uid, OperationOptions options) {
        //Random UUID for identification of logs from unique call
        String processIdentifier = UUID.randomUUID().toString();

        LOGGER.info("EoCortex : " + processIdentifier + " : Delete operation invoked");

        //Get all plates query by uid
        List<PlateQueryData> plates_query = api.listByOwner(uid.getUidValue(), this.configuration.getExternal_sys_id());

        //Iterate over and remove each plate belonging to the exact id
        for(PlateQueryData plate_del : plates_query){
            String deleteResult = api.deleteCar(plate_del.getId());

            if(api.hasError(deleteResult)) {
                LOGGER.error("EoCortex : " + processIdentifier + " : Remove operation for license_plate : "+ plate_del.getLicense_plate_number() +" : owner_id : "+ plate_del.getExternal_owner_id() +" : operation error");
            } else {
                LOGGER.info("EoCortex : " + processIdentifier + " : Remove operation for license_plate : "+ plate_del.getLicense_plate_number() +" : owner_id : "+ plate_del.getExternal_owner_id() +" successfull");
            }

        }
    }

    //This method is envoked when MidPoint wants update on the state of external system repository elements (numberplates elements)
    //Main distinguishing element is query parameter. This method has two modes of operation, fetching one specific plate
    //Based the content of query, method fetches the group of all numberplates for reference or finds the exact match with uid
    // (1:N implementation, so fetches multiples and creates one element with schema structure for MidPoint
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
                        List<PlateQueryData> plates_query = api.listByOwner(plateId, this.configuration.getExternal_sys_id()); //TODO add by system_id,
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
                personPlatesList = api.createAllPersonPlatesList(this.configuration.getExternal_sys_id()); //TODO add by system_id
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

    //This is the complex method for updating the information for every numberplate in system. In case of this implementation the complexity comes
    //with the need of special implementation of 1:N. The code is divided into three parts based on operation fetch, identify, remove, add, edit
    @Override
    public Set<AttributeDelta> updateDelta(ObjectClass objClass, Uid uid, Set<AttributeDelta> attrsDelta, OperationOptions options) {
        //Random UUID for identification of logs from unique call
        String processIdentifier = UUID.randomUUID().toString();

        LOGGER.info("EoCortex : " + processIdentifier + " : Update Delta operation invoked for UID: " + uid.getUidValue());

        if (!objClass.is(ObjectClass.ACCOUNT_NAME)) {
            throw new IllegalArgumentException("Unsupported object class: " + objClass);
        }

        //fetch plates state from eocortex
        List<PlateQueryData> plates_query = api.listByOwner(uid.getUidValue(), this.configuration.getExternal_sys_id());
        PersonPlates eoPersonPlates = api.convertQueryPersonPlate(plates_query);

        List<String> platesToAdd = new ArrayList<>();
        List<String> platesToRemove = new ArrayList<>();

        //plate add and remove fetch
        for (AttributeDelta delta : attrsDelta){
            if (delta.getName().equals("license_plate_number")) {
                LOGGER.info("Processing license_plate_number removals");
                //compare attrsDelta valuesToRemove and valuesToAdd apply logic for removal (find id in eoPersonPlates) for api.deleteCar(id)
                if (delta.getValuesToRemove() != null) {
                    platesToRemove.addAll(delta.getValuesToRemove().stream().map(Object::toString).collect(Collectors.toList()));
                }
                if (delta.getValuesToAdd() != null) {
                    platesToAdd.addAll(delta.getValuesToAdd().stream().map(Object::toString).collect(Collectors.toList()));
                }
            }
        }

        // Identify true additions and removals by finding unique entries
        List<String> uniqueToAdd = new ArrayList<>(platesToAdd);
        uniqueToAdd.removeAll(platesToRemove); // True additions not matched for removal

        List<String> uniqueToRemove = new ArrayList<>(platesToRemove);
        uniqueToRemove.removeAll(platesToAdd); // True removals not matched for addition

        //---------------REMOVALS Process true removals

        PersonPlates finalEoPersonPlates = eoPersonPlates;
        uniqueToRemove.forEach(plateToRemove -> {
            // Find and remove the plate from eoPersonPlates, then call API to delete
            finalEoPersonPlates.getPlates().stream()
                    .filter(plateData -> plateData.getLicense_plate_number().equals(plateToRemove))
                    .findFirst()
                    .ifPresent(plateData -> {

                        String remove_result = api.deleteCar(plateData.getId());
                        LOGGER.info("EoCortex : " + processIdentifier + " : edit : removing license_plate : "+ plateData.getLicense_plate_number() + " : owner_id : "+ uid.getUidValue() +" : error state -> "+ api.hasError(remove_result));

                        //finalEoPersonPlates.getPlates().remove(plateData); //TODO test
                    });
        });

        //---------------ADDITION

        PersonPlates newPlatesToAdd = eoPersonPlates;
        List<PersonPlates.PlateDetails> plateDetailsListAdd = new ArrayList<>();

        // Process true additions
        for(String plate : uniqueToAdd) {
            PersonPlates.PlateDetails pd = new PersonPlates.PlateDetails();
            pd.setLicense_plate_number(plate);
            plateDetailsListAdd.add(pd);

            //LOGGER.info("EoCortex : " + processIdentifier + " : edit : Adding license_plate "+ plate);
        }

        newPlatesToAdd.setPlates(plateDetailsListAdd);

        List<String> plateAddJson = api.createPersonsAndPlates(newPlatesToAdd); //TODO optimize call

        for(String plate : plateAddJson){

            String add_result = api.addCar(plate);

            if (api.hasError(add_result)) {
                String errorMessage = api.extractErrorMessage(add_result); // Extract the error message once, if needed.
                LOGGER.error("EoCortex : " + processIdentifier + " : edit : Adding license_plate : "+ plate +" : owner_id : "+ uid.getUidValue() +" : operation error -> " + errorMessage); // Use error level logging for actual errors.
                throw new AlreadyExistsException("EoCortex - edit : adding plate error -> " + errorMessage);
            }
            else {
                LOGGER.info("EoCortex : " + processIdentifier + " : edit : Adding license_plate : "+ plate +" : owner_id : "+ uid.getUidValue() +" : successfull");
            }

        }


        //----------------EDIT

        //fetch update
        plates_query = api.listByOwner(uid.getUidValue(), this.configuration.getExternal_sys_id());
        eoPersonPlates = api.convertQueryPersonPlate(plates_query);

        //flag for signaling change of parameter name from schema
        Boolean editFlag = false;


        for (AttributeDelta delta : attrsDelta) {
            String attributeName = delta.getName();
            List<Object> valuesToReplace = delta.getValuesToReplace();

            //process group_id
            List<Object> valuesToAdd = delta.getValuesToAdd();
            List<Object> valuesToRemove = delta.getValuesToRemove();

            if (attributeName.equals("groups_id")){

                //Check state of groupList already in MP
                List<PersonPlates.Group> groupList;
                if(eoPersonPlates.getGroups() != null){
                   groupList = new ArrayList<>();
                }
                else groupList = eoPersonPlates.getGroups();

                List<PersonPlates.Group> tempToRemove = new ArrayList<>();
                List<PersonPlates.Group> tempToAdd = new ArrayList<>();

                // Remove groups
                if (valuesToRemove != null) {
                    valuesToRemove.forEach(valueToRemove -> {
                        String idToRemove = valueToRemove.toString();

                        groupList.stream()
                                .filter(group -> group.getId().equals(idToRemove))
                                .findFirst()
                                .ifPresent(tempToRemove::add);
                    });
                }
                groupList.removeAll(tempToRemove);


                // Add groups, check duplicates
                if (valuesToAdd != null) {
                    valuesToAdd.forEach(valueToAdd -> {

                        //check
                        String idToAdd = valueToAdd.toString();
                        boolean exists = groupList.stream()
                                .anyMatch(group -> group.getId().equals(idToAdd));

                        if (!exists) {
                            PersonPlates.Group newGroup = new PersonPlates.Group();
                            newGroup.setId(idToAdd);
                            tempToAdd.add(newGroup);
                        }
                    });
                }
                groupList.addAll(tempToAdd);

                eoPersonPlates.setGroups(groupList);
                editFlag = true;
            }

            //process other attributes
            if (valuesToReplace != null && !valuesToReplace.isEmpty()) {
                Object newValue = valuesToReplace.get(0);

                switch (attributeName) {
                    case "first_name":
                        eoPersonPlates.setFirst_name(newValue.toString());
                        editFlag = true;
                        break;
                    case "second_name":
                        eoPersonPlates.setSecond_name(newValue.toString());
                        editFlag = true;
                        break;
                    case "third_name":
                        eoPersonPlates.setThird_name(newValue.toString());
                        editFlag = true;
                        break;
                    case "external_id":
                        eoPersonPlates.setExternal_id(newValue.toString());
                        editFlag = true;
                        break;
                    case "external_sys_id":
                        eoPersonPlates.setExternal_sys_id(newValue.toString());
                        editFlag = true;
                        break;
                    case "external_owner_id":
                        eoPersonPlates.setExternal_owner_id(newValue.toString());
                        editFlag = true;
                        break;
                    case "additional_info":
                        eoPersonPlates.setAdditional_info(newValue.toString());
                        editFlag = true;
                        break;
                    case "model":
                        eoPersonPlates.setModel(newValue.toString());
                        editFlag = true;
                        break;
                    case "color":
                        eoPersonPlates.setColor(newValue.toString());
                        editFlag = true;
                        break;
                    default:
                        LOGGER.warn("Unknown attribute for update: " + attributeName);
                }
            }
        }

        //edit only if flag for edit is set
        if(editFlag){
            List<String> plateEditJson = api.createPersonsAndPlates(eoPersonPlates);
            List<PersonPlates.PlateDetails> currentPlates = eoPersonPlates.getPlates();

            if(plateEditJson.size() == currentPlates.size()) {

                for (int i = 0; i <= plateEditJson.size()-1 ; i++) {
                    //get id from eoPersonPlates as the createPersonsAndPlates is by design omiting ids
                    //the order is the same when creating json

                    String result_edit = api.updateCar(currentPlates.get(i).getId(),plateEditJson.get(i));
                    //System.out.println("edit : editing plate error ->"+ api.hasError(result_edit));
                    if(api.hasError(result_edit)){
                        LOGGER.error("EoCortex : " + processIdentifier + " : edit : editing parameters license_plate : "+ currentPlates.get(i).getLicense_plate_number() +" : owner_id : "+ uid.getUidValue() +" : operation error -> " + api.extractErrorMessage(result_edit)); // Use error level logging for actual errors.}
                    }
                    else LOGGER.info("EoCortex : " + processIdentifier + " : edit : editing parameters license_plate : "+ currentPlates.get(i).getLicense_plate_number() +" : owner_id : "+ uid.getUidValue() +" : successfull"); // Use error level logging for actual errors.
                }
            }
        }

        //logic behind the given structure
        //
        //_.intersection(One, Two) -> not changed data
        //
        //_.difference(Two, One) -> new data
        //
        //_.difference(One, Two) -> removed data

        //UPDATE ADD NEW
        //UPDATE REMOVE OLD
        //UPDATE ALL OTHERS + GROUPS (move)
        //EXECUTE

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

    //Simple method to validate what values are being passed and if the value is present for the next step to happen
    private String getAndValidateAttribute(Set<Attribute> attributes, String attributeName) {
        if(attributes.isEmpty()) return null;

        for (Attribute attr:attributes) {

            String attrName = attr.getName();
            String value = null;

            if(attrName.equals(attributeName)) {
                value = AttributeUtil.getStringValue(AttributeUtil.find(attributeName, attributes));
                LOGGER.info("getAndValidateAttr : value ->" + value);
                if (!Objects.equals(value, "")) return value;
                return null;
            }
        }
        return null;
    }

    //Method to help use the builder pattern for attibutes (cleanliness of code)
    private void addAttributeToBuilder(ConnectorObjectBuilder cob, String attributeName, Object attributeValue) {
        if (attributeValue != null) {
            cob.addAttribute(AttributeBuilder.build(attributeName, attributeValue));
        }
    }
}
