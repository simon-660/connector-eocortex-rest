//TODO
// - prekompilovať javou 17 (nova java)
// - group id pridať -> nove znaky nech maju konfigurovatelny groups_id !! jedno staticky pri schema configu je problem
// - poriešiť update a pridanie len na create operaciu

// - Pridať feature na system_idečko do settingov - DONE
// - additional-info -> aby šlo zo settings DONE

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

//TODO issue when the user has no plates do nothing, (consult with mann about this)
//Možno nastaviť ako mandatory aspoň jeden plate by pomohlo, lebo ak nie tak musim riešiť edgecase v create a edit do nula značiek

//Nepovolenie editu no nuly ? na strane MP (reconciliation to uprace ?), breakne sa link v eo neostane ziadny detail
//Ako poriešiť usera ktory zrazu značku ma lebo si ju pridal(čo s tým spraví mippoint, zavola sam create na konektore ?) - ANO

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

        this.configuration.validate(); //TODO check

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
            else{ //TODO hard to reach test
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

        //Iterate over and remove each plate belonging to the exact id
        for(PlateQueryData plate_del : plates_query){
            String deleteResult = api.deleteCar(plate_del.getId());

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
                        List<PlateQueryData> plates_query = api.listByOwner(plateId); //TODO add by system_id, možno netreba
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

    //remove when remove is bigger than add (only the difference in size)
    //edit when same number in add and remove
    //add when add is bigger than remove (only the diffenence in size)
    @Override
    public Set<AttributeDelta> updateDelta(ObjectClass objClass, Uid uid, Set<AttributeDelta> attrsDelta, OperationOptions options) {
        //THIS DOES NOT UPDATE GROUPS

        LOGGER.info("Update Delta operation invoked for UID: " + uid.getUidValue());

        if (!objClass.is(ObjectClass.ACCOUNT_NAME)) {
            throw new IllegalArgumentException("Unsupported object class: " + objClass);
        }

        //fetch plates state from eocortex
        List<PlateQueryData> plates_query = api.listByOwner(uid.getUidValue());
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

        // Process true removals
        PersonPlates finalEoPersonPlates = eoPersonPlates;
        uniqueToRemove.forEach(plateToRemove -> {
            // Find and remove the plate from eoPersonPlates, then call API to delete
            finalEoPersonPlates.getPlates().stream()
                    .filter(plateData -> plateData.getLicense_plate_number().equals(plateToRemove))
                    .findFirst()
                    .ifPresent(plateData -> {

                        String remove_result = api.deleteCar(plateData.getId());
                        LOGGER.info("edit : removing plate -> "+ plateData.getId()+" error -> "+ api.hasError(remove_result));
                        //finalEoPersonPlates.getPlates().remove(plateData); //TODO test
                    });
        });


        //---------------add

        PersonPlates newPlatesToAdd = eoPersonPlates;
        List<PersonPlates.PlateDetails> plateDetailsListAdd = new ArrayList<>();

        // Process true additions
        for(String plate : uniqueToAdd) {
            PersonPlates.PlateDetails pd = new PersonPlates.PlateDetails();
            pd.setLicense_plate_number(plate);
            plateDetailsListAdd.add(pd);

            LOGGER.info("Adding new plate: " + plate);
        }
        newPlatesToAdd.setPlates(plateDetailsListAdd);

        List<String> plateAddJson = api.createPersonsAndPlates(newPlatesToAdd); //TODO optimize call

        for(String plate : plateAddJson){
            String add_result = api.addCar(plate);
            LOGGER.info("edit : adding plate -> "+" error -> "+ api.hasError(add_result));
        }


        //----------------edit

        //fetch update
        plates_query = api.listByOwner(uid.getUidValue());
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
                    System.out.println("edit : editing plate error ->"+ api.hasError(result_edit));
                }
            }
        }


        //_.intersection(One, Two) -> not changed data
        //
        //_.difference(Two, One) -> new data
        //
        //_.difference(One, Two) -> removed data

        //UPDATE ALL OTHERS + GROUPS (move)
        //UPDATE ADD NEW
        //UPDATE REMOVE OLD

        //EXECUTE

        //if same number
        //identify additions
        //identify deletions

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
