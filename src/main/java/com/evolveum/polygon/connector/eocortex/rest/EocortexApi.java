package com.evolveum.polygon.connector.eocortex.rest;

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
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

public class EocortexApi {
    private String apiUrl;
    private String username;
    private String password;

    public EocortexApi(String apiUrl, String username, String password) {
        this.apiUrl = apiUrl;
        this.username = username;
        this.password = DigestUtils.md5Hex(password).toUpperCase(); //MD5 hash of password needed for eocortex
    }

    //TODO getUidFromCar

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

    public String parseUidFromResponse(String jsonResponse) {
        try {
            JSONObject responseObj = new JSONObject(jsonResponse);

            if (responseObj.has("plates")) {
                JSONArray platesArray = responseObj.getJSONArray("plates");

                if (platesArray.length() > 0) {
                    JSONObject firstPlate = platesArray.getJSONObject(0);
                    return firstPlate.optString("id", "{\"ErrorMessage\": \"ID not found\"}");
                } else {
                    return "{\"ErrorMessage\": \"No plates data found\"}";
                }
            } else {
                return "{\"ErrorMessage\": \"Plates array not found\"}";
            }
        } catch (JSONException e) {
            String jsonCustomErrorString = "{\"ErrorMessage\": \"" + e.toString() + "\"}";
            return jsonCustomErrorString;
        }
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
        } catch (IOException e) {
            //e.printStackTrace();
            return false;
        }
    }

    public String createJsonString(
            String firstName, String secondName, String thirdName, String externalId,
            String external_sys_id, String external_owner_id, String licensePlateNumber,
            String additionalInfo, String model, String color, List<String> groupIds) {

        JSONObject owner = new JSONObject();
        if (firstName != null) owner.put("first_name", firstName);
        if (secondName != null) owner.put("second_name", secondName);
        if (thirdName != null) owner.put("third_name", thirdName);

        JSONArray groups = new JSONArray();
        if (groupIds != null) {
            for (String groupId : groupIds) {
                JSONObject group = new JSONObject();
                group.put("id", groupId);
                groups.put(group);
            }
        }

        JSONObject json = new JSONObject();
        json.put("owner", owner);
        //if (id != null) json.put("id", id);
        if (externalId != null) json.put("external_id", externalId);
        if (external_sys_id != null) json.put("external_sys_id", external_sys_id);
        if (external_owner_id != null) json.put("external_owner_id", external_owner_id);
        if (licensePlateNumber != null) json.put("license_plate_number", licensePlateNumber);
        if (additionalInfo != null) json.put("additional_info", additionalInfo);
        if (model != null) json.put("model", model);
        if (color != null) json.put("color", color);
        if (!groups.isEmpty()) json.put("groups", groups);

        return json.toString();
    }

    public String updateJsonString(
            String existingJsonString,
            String firstName, String secondName, String thirdName,
            String externalId, String external_sys_id, String external_owner_id,
            String licensePlateNumber, String additionalInfo, String model,
            String color, List<String> groupIds) throws JSONException {

        JSONObject json = new JSONObject(existingJsonString);

        if (json.has("owner")) {
            JSONObject owner = json.getJSONObject("owner");
            if (firstName != null) owner.put("first_name", firstName);
            if (secondName != null) owner.put("second_name", secondName);
            if (thirdName != null) owner.put("third_name", thirdName);
        }

        if (externalId != null) json.put("external_id", externalId);
        if (external_sys_id != null) json.put("external_sys_id", external_sys_id);
        if (external_owner_id != null) json.put("external_owner_id", external_owner_id);
        if (licensePlateNumber != null) json.put("license_plate_number", licensePlateNumber);
        if (additionalInfo != null) json.put("additional_info", additionalInfo);
        if (model != null) json.put("model", model);
        if (color != null) json.put("color", color);

        if (groupIds != null) {
            JSONArray groups = new JSONArray();
            for (String groupId : groupIds) {
                JSONObject group = new JSONObject();
                group.put("id", groupId);
                groups.put(group);
            }
            json.put("groups", groups);
        }

        return json.toString();
    }

    public String addCar(String jsonStringParam) {
        String responseString = null;
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            HttpPost request = new HttpPost(apiUrl + "/cars");
            request.setHeader("Authorization", getEncodedAuthHeader());
            request.setHeader("Content-Type", "application/json");

            //String jsonInputString = String.format(
            //        "{\"license_plate_number\": \"%s\", \"external_id\": \"%s\", \"additional_info\": \"%s\"}",
            //        licensePlateNumber, externalId, additionalInfo);
            StringEntity requestEntity = new StringEntity(jsonStringParam, StandardCharsets.UTF_8);
            request.setEntity(requestEntity);

            HttpResponse response = httpClient.execute(request);
            responseString = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            String jsonCustomErrorString = "{\"ErrorMessage\": \"" + e.toString() + "\"}";
            return jsonCustomErrorString;
        }
        return responseString;
    }

    //TODO license plate required in update
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
        } catch (IOException e) {
            String jsonCustomErrorString = "{\"ErrorMessage\": \"" + e.toString() + "\"}";
            return jsonCustomErrorString;
        }
        return responseString;
    }

    public String findCars(String externalOwnerId, String licensePlateNumber) {
        String responseString = null;
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            URIBuilder builder = new URIBuilder(apiUrl + "/cars");

            // Constructing the filter query
            StringBuilder filterQuery = new StringBuilder();
            if (externalOwnerId != null && !externalOwnerId.isEmpty()) {
                filterQuery.append("external_owner_id='").append(externalOwnerId).append("'");
            }
            if (licensePlateNumber != null && !licensePlateNumber.isEmpty()) {
                if (filterQuery.length() > 0) {
                    filterQuery.append(" AND ");
                }
                filterQuery.append("license_plate_number='").append(licensePlateNumber).append("'");
            }

            if (filterQuery.length() > 0) {
                builder.addParameter("filter", filterQuery.toString());
            }

            HttpGet request = new HttpGet(builder.build());
            request.setHeader("Authorization", getEncodedAuthHeader());

            HttpResponse response = httpClient.execute(request);
            responseString = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            String jsonCustomErrorString = "{\"ErrorMessage\": \"" + e.toString() + "\"}";
            return jsonCustomErrorString;
        }
        return responseString;
    }

    public String getCarDetails(String carId) {
        String responseString = null;
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            URIBuilder builder = new URIBuilder(apiUrl + "/cars/" + carId);

            HttpGet request = new HttpGet(builder.build());
            request.setHeader("Authorization", getEncodedAuthHeader());

            HttpResponse response = httpClient.execute(request);
            responseString = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            String jsonCustomErrorString = "{\"ErrorMessage\": \"" + e.toString() + "\"}";
            return jsonCustomErrorString;
        }
        return responseString;
    }

    public String deleteCar(String carId) {
        String responseString = null;
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            HttpDelete request = new HttpDelete(apiUrl + "/cars/" + carId);
            request.setHeader("Authorization", getEncodedAuthHeader());

            HttpResponse response = httpClient.execute(request);
            responseString = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            String jsonCustomErrorString = "{\"ErrorMessage\": \"" + e.toString() + "\"}";
            return jsonCustomErrorString;
        }
        return responseString;
    }

    public String listGroupsAndFindByName(String groupName) {
        String responseString = null;
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            HttpGet request = new HttpGet(apiUrl + "/cars-groups");
            request.setHeader("Authorization", getEncodedAuthHeader());

            HttpResponse response = httpClient.execute(request);
            responseString = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

            return findGroupByName(responseString, groupName);
        } catch (IOException e) {
            String jsonCustomErrorString = "{\"ErrorMessage\": \"" + e.toString() + "\"}";
            return jsonCustomErrorString;
        }
    }

    private String findGroupByName(String jsonResponse, String groupName) {
        try {
            JSONObject responseObject = new JSONObject(jsonResponse);
            JSONArray groups = responseObject.getJSONArray("groups");

            for (int i = 0; i < groups.length(); i++) {
                JSONObject group = groups.getJSONObject(i);
                if (group.getString("name").equals(groupName)) {
                    return group.getString("id");
                }
            }
            return "{\"ErrorMessage\":\"No group with specified name found\"}";
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"ErrorMessage\":\"Error parsing JSON response\"}";
        }
    }
}
