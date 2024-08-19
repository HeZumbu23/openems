package io.openems.edge.pvinverter.growatt;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.net.CookieManager;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.HashMap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

public class GrowattApi {

	private enum ConnectionStatus { 
		LoggedIn, 
		//GotPlantId, 
		NotLoggedIn };
	
    private static final String GROWATT_API_BASEURL = "https://openapi.growatt.com";
    // important because the Growatt API does not accept generic agents.
    private static final String DEFAULT_USER_AGENT = "Mozilla/5.0 (platform; rv:geckoversion) Gecko/geckotrail Firefox/firefoxversion";

    private final HttpClient client = HttpClient.newBuilder()
            .cookieHandler(new CookieManager())
            .version(HttpClient.Version.HTTP_2)
            .build();

    private final String email;
    private final String password;
    private ConnectionStatus status;
        
    public GrowattApi(String email, String password) {
    	this.email = email;
    	this.password = password;
    	status = ConnectionStatus.NotLoggedIn;
    }

    public double getPowerOfPlant(String plantId) throws GrowattApiException {
    	if(status == ConnectionStatus.NotLoggedIn ) {
    		login();    	
    	} 

    	if(status == ConnectionStatus.LoggedIn) {

    		return getPowerOfPlantInternal(plantId);
    	}
    	else throw new GrowattApiException("Login to Growatt API was not successfull");
    }

    private double getPowerOfPlantInternal(String plantId) throws GrowattApiException {
    	try {               
    		// Retrieve plant information as JSON string
    		JsonObject plantInfoJson = getPlantInfo(plantId);

    		// Extract the power value from the deviceList array
    		JsonElement deviceListElement = plantInfoJson.get("invList");
    		if (deviceListElement != null && deviceListElement.isJsonArray()) {
    			JsonObject device = deviceListElement.getAsJsonArray().get(0).getAsJsonObject();
    			return device.get("power").getAsDouble();
    		} 

    		throw new JsonParseException("Could not read 'power' property of 'invList'");

    	} catch (Exception e) {
    		throw new GrowattApiException("Could not receive power of plant", e);
    	}
    }
       
    private void login() throws GrowattApiException {
        String encryptedPassword = hashPassword(password);
        String url = GROWATT_API_BASEURL + "/newTwoLoginAPI.do";
        status = ConnectionStatus.NotLoggedIn;
        
        Map<Object, Object> data = new HashMap<>();
        data.put("userName", this.email);
        data.put("password", encryptedPassword);
        
        HttpRequest request = HttpRequest.newBuilder()
                .header("Content-Type",  "application/x-www-form-urlencoded")
                .header("User-Agent",  DEFAULT_USER_AGENT)
                .uri(URI.create(url))
                .POST(ofForm(data))
                .build();
        
        HttpResponse<String> response = null;
        
        try {
        	response = client.send(request, HttpResponse.BodyHandlers.ofString());
        }
        catch(InterruptedException ex)            
        {
        	throw new GrowattApiException("Could not login to Growatt API", ex);
        }
        catch(IOException ex)            
        {
        	throw new GrowattApiException("Could not login to Growatt API", ex);
        }
                    
        var statusCode = response.statusCode();
        
        
        // Read the response
        if (statusCode == HttpURLConnection.HTTP_OK) {
            
            // Parse the response JSON
        	var jsonString = response.body();
            JsonElement jsonElement = JsonParser.parseString(jsonString);
            JsonObject jsonObject = jsonElement.getAsJsonObject();
            JsonObject backObject = jsonObject.getAsJsonObject("back");
            var success = backObject.get("success").getAsBoolean();
            if( !success ) {
            	var errormessage = backObject.get("error").getAsString();
            	throw new GrowattApiException("Could not login to Growatt API. JSON response contains an error: " + errormessage);
            }    
            status = ConnectionStatus.LoggedIn;
        } 
    }
          
    private static HttpRequest.BodyPublisher ofForm(Map<Object, Object> data) {

        StringBuilder body = new StringBuilder();

        for (Object dataKey : data.keySet()) {

            if (body.length() > 0) {
                body.append("&");
            }

            body.append(encode(dataKey))
                    .append("=")
                    .append(encode(data.get(dataKey)));
        }

        return HttpRequest.BodyPublishers.ofString(body.toString());
    }
    
    private static String encode(Object obj) {
        return URLEncoder.encode(obj.toString(), StandardCharsets.UTF_8);
    }

    private JsonObject getPlantInfo(String plantId) throws GrowattApiException {
        try {
            // The specific endpoint for the plant info request
        	String op = "getAllDeviceListTwo";
        	int pageNum = 1;
        	int pageSize = 1;
            // Construct the full URL with parameters
            String queryParams = String.format("op=%s&plantId=%s&pageNum=%d&pageSize=%d",
                    op,
                    plantId,
                    pageNum,
                    pageSize);
            String url = GROWATT_API_BASEURL + "/newTwoPlantAPI.do?" + queryParams;

            // Send the GET request and return the response
            return sendGetRequest(url);
        } catch (Exception e) {
            throw new GrowattApiException("Could not receive power of plant", e);
        }
    }

    private JsonObject sendGetRequest(String url) throws GrowattApiException {

    	HttpRequest req = HttpRequest.newBuilder()
    			.uri(URI.create(url))
    			.header("Content-Type",  "application/json")
    			.header("User-Agent",  DEFAULT_USER_AGENT)
    			.GET()
    			.build();

    	HttpResponse<String> response = null;
    	try {
    		response = this.client.send(req, HttpResponse.BodyHandlers.ofString());
    	}
    	catch(IOException ex) {
    		throw new GrowattApiException("Could not send HTTP Get reqeust", ex);
    	}
    	catch(InterruptedException ex) {
    		throw new GrowattApiException("Could not send HTTP Get reqeust", ex);
    	}

    	var statusCode = response.statusCode();

    	// Read the response
    	if (statusCode == HttpURLConnection.HTTP_OK) {

    		// Parse the response JSON
    		var jsonString = response.body();
    		JsonElement jsonElement = JsonParser.parseString(jsonString);

    		JsonObject jsonObject = jsonElement.getAsJsonObject();                
    		return jsonObject;
    	} 

   		throw new GrowattApiException("HTTP response code " + statusCode);
    }
    
    private static String hashPassword(String password) {
        try {
            // Create MD5 Hash
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(password.getBytes());
            byte[] digest = md.digest();
            
            // Convert the byte array to hexadecimal string
            StringBuilder passwordMd5 = new StringBuilder();
            for (byte b : digest) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hex = "0" + hex;
                }
                passwordMd5.append(hex);
            }

            // Modify the string according to the condition
            for (int i = 0; i < passwordMd5.length(); i += 2) {
                if (passwordMd5.charAt(i) == '0') {
                    passwordMd5.setCharAt(i, 'c');
                }
            }

            return passwordMd5.toString();

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
