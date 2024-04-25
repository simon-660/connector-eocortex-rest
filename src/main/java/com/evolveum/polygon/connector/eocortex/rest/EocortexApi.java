package com.evolveum.polygon.connector.eocortex.rest;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class EocortexApi {
    //APi integration layer for eocortex system, version with grouping by external_owner_id

    private String apiUrl;
    private String username;
    private String password;

    public EocortexApi(String apiUrl, String username, String password) {
        this.apiUrl = apiUrl;
        this.username = username;
        this.password = DigestUtils.md5Hex(password).toUpperCase(); //MD5 hash of password needed for eocortex
    }

    private String getEncodedAuthHeader() {
        String auth = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
    }

    public boolean hasError(String jsonResponse) {
        try {
            JSONObject responseObj = new JSONObject(jsonResponse);
            return responseObj.has("ErrorMessage") && !responseObj.getString("ErrorMessage").isEmpty();
        } catch (JSONException e) {
            //e.printStackTrace();
            return false;
        }
    }

    public String extractErrorMessage(String jsonResponse) {
        try {
            JSONObject responseObj = new JSONObject(jsonResponse);
            if (responseObj.has("ErrorMessage") && !responseObj.getString("ErrorMessage").isEmpty()) {
                return responseObj.getString("ErrorMessage");
            }
        } catch (JSONException e) {
            return "The result error message was not present in the response";
        }
        return "";
    }

    public boolean isSearchResultEmpty(String jsonResponse) {
        try {
            JSONObject responseObj = new JSONObject(jsonResponse);
            return responseObj.getInt("total_count") == 0;
        } catch (JSONException e) {
            //e.printStackTrace();
            return true; //return false this is error handling
        }
    }

    public boolean testConnection() {
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            HttpGet request = new HttpGet(apiUrl + "/cars"); //This api works with cars module so this checks this part of api if avail.
            request.setHeader("Authorization", getEncodedAuthHeader());

            HttpResponse response = httpClient.execute(request);
            int statusCode = response.getStatusLine().getStatusCode();

            if(hasError(EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8))) return false;
            return statusCode == HttpStatus.SC_OK; //return false if not 200
        } catch (Exception e) {
            //e.printStackTrace();
            return false;
        }
    }

    public List<PlateQueryData> getAllCars() {
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            HttpGet request = new HttpGet(apiUrl + "/cars");
            request.setHeader("Authorization", getEncodedAuthHeader());

            HttpResponse response = httpClient.execute(request);
            String responseString = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

            JSONObject responseObject = new JSONObject(responseString);
            if (responseObject.getInt("total_count") == 0) {
                return Collections.emptyList(); // Return an empty list if no cars are found
            }
            JSONArray platesArray = responseObject.getJSONArray("plates");

            // Deserialize JSON directly to List<Plate>
            return new Gson().fromJson(platesArray.toString(), new TypeToken<List<PlateQueryData>>(){}.getType());
        } catch (IOException | JSONException e) {
            e.printStackTrace();
            return Collections.emptyList(); // Return an empty list in case of error
        }
    }

    public List<PlateQueryData> listByOwner(String externalOwnerId, String externalSysId) {
        List<PlateQueryData> platesList = new ArrayList<>();
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            URIBuilder builder = new URIBuilder(apiUrl + "/cars");

            if (externalOwnerId != null && !externalOwnerId.isEmpty() && externalSysId != null && !externalSysId.isEmpty()) {
                String combinedFilter = "external_owner_id='" + externalOwnerId + "' AND external_sys_id='" + externalSysId + "'";
                builder.addParameter("filter", combinedFilter);
            }
            else if (externalOwnerId != null && !externalOwnerId.isEmpty()) {
                builder.addParameter("filter", "external_owner_id='" + externalOwnerId + "'");
            }

            HttpGet request = new HttpGet(builder.build());
            request.setHeader("Authorization", getEncodedAuthHeader());

            HttpResponse response = httpClient.execute(request);
            String responseString = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);


            JSONObject responseObject = new JSONObject(responseString);
            JSONArray platesArray = responseObject.getJSONArray("plates");

            Type listType = new TypeToken<List<PlateQueryData>>(){}.getType();
            platesList = new Gson().fromJson(platesArray.toString(), listType);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return platesList;
    }

    public String deleteCar(String carId) {
        String responseString = null;
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            HttpDelete request = new HttpDelete(apiUrl + "/cars/" + carId);
            request.setHeader("Authorization", getEncodedAuthHeader());

            HttpResponse response = httpClient.execute(request);
            responseString = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            String jsonCustomErrorString = "{\"ErrorMessage\": \"" + e.toString() + "\"}";
            return jsonCustomErrorString;
        }
        return responseString;
    }

    public String addCar(String jsonStringParam) {
        String responseString = null;
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            HttpPost request = new HttpPost(apiUrl + "/cars");
            request.setHeader("Authorization", getEncodedAuthHeader());
            request.setHeader("Content-Type", "application/json");

            StringEntity requestEntity = new StringEntity(jsonStringParam, StandardCharsets.UTF_8);
            request.setEntity(requestEntity);

            HttpResponse response = httpClient.execute(request);
            responseString = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            String jsonCustomErrorString = "{\"ErrorMessage\": \"" + e.toString() + "\"}";
            return jsonCustomErrorString;
        }
        return responseString;
    }

    public String updateCar(String carId, String jsonBody) {
        String responseString = null;
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            HttpPut request = new HttpPut(apiUrl + "/cars/" + carId);
            request.setHeader("Authorization", getEncodedAuthHeader());
            request.setHeader("Content-Type", "application/json");

            StringEntity requestEntity = new StringEntity(jsonBody, StandardCharsets.UTF_8);
            request.setEntity(requestEntity);

            HttpResponse response = httpClient.execute(request);
            responseString = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            String jsonCustomErrorString = "{\"ErrorMessage\": \"" + e.toString() + "\"}";
            return jsonCustomErrorString;
        }
        return responseString;
    }


    public PlateDetails fetchPlateDetails(String carId) {
        PlateDetails plateDetails = null;
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            URIBuilder uri_builder = new URIBuilder(apiUrl + "/cars/" + carId);
            HttpGet request = new HttpGet(uri_builder.build());
            request.setHeader("Authorization", getEncodedAuthHeader());

            HttpResponse response = httpClient.execute(request);
            String responseString = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

            plateDetails = new Gson().fromJson(responseString, PlateDetails.class);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return plateDetails;
    }

    public List<PersonPlates> createAllPersonPlatesList(String sys_id) {
        List<PlateQueryData> allPlates = this.getAllCars();
        Map<String, List<PlateQueryData>> groupedPlates = this.groupPlatesByOwner(allPlates, sys_id);

        List<PersonPlates> personPlatesList = new ArrayList<>();

        groupedPlates.forEach((ownerId, plates) -> {
            if (!plates.isEmpty()) {
                PlateDetails firstPlateDetails = this.fetchPlateDetails(plates.get(0).getId());

                List<PersonPlates.PlateDetails> plateDetailsList = new ArrayList<>();
                for (PlateQueryData plate : plates) {
                    PersonPlates.PlateDetails pd = new PersonPlates.PlateDetails();
                    pd.setLicense_plate_number(plate.getLicense_plate_number());
                    pd.setId(plate.getId());
                    plateDetailsList.add(pd);
                }

                List<PersonPlates.Group> groupList = new ArrayList<>();
                for (PlateQueryData plate : plates) {
                    PersonPlates.Group gd = new PersonPlates.Group();
                    gd.setId(plate.getId());
                    groupList.add(gd);
                }

                PersonPlates personPlate = new PersonPlates();
                personPlate.setFirst_name(firstPlateDetails.getOwner().firstName);
                personPlate.setSecond_name(firstPlateDetails.getOwner().secondName);
                personPlate.setThird_name(firstPlateDetails.getOwner().thirdName);

                personPlate.setExternal_owner_id(ownerId);
                personPlate.setAdditional_info(firstPlateDetails.getAdditional_info());
                personPlate.setExternal_id(firstPlateDetails.getExternal_id());
                personPlate.setExternal_sys_id(firstPlateDetails.getExternal_sys_id());

                personPlate.setModel(firstPlateDetails.getModel());
                personPlate.setColor(firstPlateDetails.getColor());

                personPlate.setPlates(plateDetailsList);
                personPlate.setGroups(groupList);

                personPlatesList.add(personPlate);
            }
        });

        return personPlatesList;
    }

    public PersonPlates convertQueryPersonPlate(List<PlateQueryData> plates) {

        //TODO edit nad žiadnou žnačkou
        PlateDetails firstPlateDetails = this.fetchPlateDetails(plates.get(0).getId());
        List<PersonPlates.PlateDetails> plateDetailsList = new ArrayList<>();
        for (PlateQueryData plate : plates) {
            PersonPlates.PlateDetails pd = new PersonPlates.PlateDetails();
            pd.setLicense_plate_number(plate.getLicense_plate_number());
            pd.setId(plate.getId());
            plateDetailsList.add(pd);
        }

        PersonPlates personPlate = new PersonPlates();
        personPlate.setFirst_name(firstPlateDetails.getOwner().firstName);
        personPlate.setSecond_name(firstPlateDetails.getOwner().secondName);
        personPlate.setThird_name(firstPlateDetails.getOwner().thirdName);

        List<PlateDetails.Group> plateDetailsGroups = firstPlateDetails.getGroups();
        List<PersonPlates.Group> personPlatesGroups = new ArrayList<>();

        for (PlateDetails.Group personGroup : plateDetailsGroups) {
            PersonPlates.Group personPlatesGroup = new PersonPlates.Group();
            personPlatesGroup.setId(personGroup.getId());
            personPlatesGroups.add(personPlatesGroup);
        }

        personPlate.setGroups(personPlatesGroups);

        personPlate.setExternal_owner_id(firstPlateDetails.getExternal_owner_id());
        personPlate.setModel(firstPlateDetails.getModel());
        personPlate.setColor(firstPlateDetails.getColor());
        personPlate.setAdditional_info(firstPlateDetails.getAdditional_info());
        personPlate.setExternal_id(firstPlateDetails.getExternal_id());
        personPlate.setExternal_sys_id(firstPlateDetails.getExternal_sys_id());
        personPlate.setPlates(plateDetailsList);

        return personPlate;
    }

    public List<String> createPersonsAndPlates(PersonPlates personPlates) {
        Gson gson = new Gson();
        List<String> outputJsons = new ArrayList<>();

        for (PersonPlates.PlateDetails personPlateDetail : personPlates.getPlates()) {
            PlateDetails eocortexPlateDetails = new PlateDetails();

            eocortexPlateDetails.getOwner().setFirstName(personPlates.getFirst_name());
            eocortexPlateDetails.getOwner().setSecondName(personPlates.getSecond_name());
            eocortexPlateDetails.getOwner().setThirdName(personPlates.getThird_name());

            List<PersonPlates.Group> personGroups = personPlates.getGroups();
            List<PlateDetails.Group> eocortexGroups = new ArrayList<>();

            for (PersonPlates.Group personGroup : personGroups) {
                PlateDetails.Group eocortexGroup = new PlateDetails.Group();
                eocortexGroup.setId(personGroup.getId());
                eocortexGroups.add(eocortexGroup);
            }

            eocortexPlateDetails.setGroups(eocortexGroups);

            eocortexPlateDetails.setModel(personPlates.getModel());
            eocortexPlateDetails.setColor(personPlates.getColor());
            eocortexPlateDetails.setAdditional_info(personPlates.getAdditional_info());
            eocortexPlateDetails.setExternal_id(personPlates.getExternal_id());
            eocortexPlateDetails.setExternal_sys_id(personPlates.getExternal_sys_id());
            eocortexPlateDetails.setExternal_owner_id(personPlates.getExternal_owner_id());

            //Specific numberplate + id is null for create operation
            eocortexPlateDetails.setLicense_plate_number(personPlateDetail.getLicense_plate_number());

            String json = gson.toJson(eocortexPlateDetails);
            outputJsons.add(json);
            //System.out.println("createPersonsAndPlates : "+ json); //Debug
        }
        return outputJsons;
    }

    public Map<String, List<PlateQueryData>> groupPlatesByOwner(List<PlateQueryData> plates, String specifiedSysId) {
        return plates.stream()
                .filter(plate -> (specifiedSysId == null || specifiedSysId.isEmpty()) ||
                        (plate.getExternal_sys_id() != null && plate.getExternal_sys_id().equals(specifiedSysId))) // Filter by specified external_sys_id, if null or empty bypass .filter
                .filter(plate -> plate.getExternal_owner_id() != null && !plate.getExternal_owner_id().isEmpty()) // Filter non-empty external_owner_id
                .collect(Collectors.groupingBy(PlateQueryData::getExternal_owner_id));
    }


}

