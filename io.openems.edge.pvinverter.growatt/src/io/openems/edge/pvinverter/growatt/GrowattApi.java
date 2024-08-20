package io.openems.edge.pvinverter.growatt;

import java.io.IOException;
import java.net.CookieManager;
import java.net.HttpURLConnection;
import java.net.URI;
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
    private final int plantIndex;
    private ConnectionStatus status;
    private String plantId;
        
    public GrowattApi(String email, String password, int plantIndex) {
    	this.email = email;
    	this.password = password;
    	this.plantIndex = plantIndex;
    	status = ConnectionStatus.NotLoggedIn;
    }

    public double getPowerOfPlant() throws GrowattApiException {
    	if(status == ConnectionStatus.NotLoggedIn ) {
    		login();    	
    	} 

    	if(status == ConnectionStatus.LoggedIn) {
    		return getPowerOfPlantInternal();   			
    	}

    	throw new GrowattApiException("Could not get power of plant");
    }

    private void login() throws GrowattApiException {
        
        status = ConnectionStatus.NotLoggedIn;
                
        String url = GROWATT_API_BASEURL + "/newTwoLoginAPI.do";
		String encryptedPassword = hashPassword(this.password);
        Map<Object, Object> postData = new HashMap<>();
        postData.put("userName", this.email);
        postData.put("password", encryptedPassword);
        
        HttpResponse<String> response = null;
        try {
        	response = sendPostRequest(url, postData);
        }
        catch(IOException | InterruptedException ex) {
        	throw new GrowattApiException("Could login to Growatt API", ex);
        }
                    
        var statusCode = response.statusCode();
              
        if (statusCode == HttpURLConnection.HTTP_OK) {
            
        	JsonObject body = this.getJsonBody(response);            
            JsonObject backObject = body.getAsJsonObject("back");
            
            readSuccessProperty(backObject);    
            this.readPlantId(backObject);

            status = ConnectionStatus.LoggedIn;
        } 
    }
    	
    private double getPowerOfPlantInternal() throws GrowattApiException {
    	// Retrieve plant information as JSON string
    	JsonObject body = sendPlantInfoRequest();

    	// Extract the power value from the deviceList array
    	JsonElement deviceListElement = body.get("invList");
    	if (deviceListElement != null && deviceListElement.isJsonArray()) {
    		JsonObject device = deviceListElement.getAsJsonArray().get(0).getAsJsonObject();
    		return device.get("power").getAsDouble();
    	} 

    	throw new GrowattApiException("Could not read 'power' property of 'invList'");
    }

	private HttpResponse<String> sendPostRequest(String url, Map<Object,Object> postData) throws IOException, InterruptedException {
       
        HttpRequest request = HttpRequest.newBuilder()
                .header("Content-Type",  "application/x-www-form-urlencoded")
                .header("User-Agent",  DEFAULT_USER_AGENT)
                .uri(URI.create(url))
                .POST(ofForm(postData))
                .build();
        
        return client.send(request, HttpResponse.BodyHandlers.ofString());        
	}
	
	private void readSuccessProperty(JsonObject backObject) throws GrowattApiException {
		var success = backObject.get("success").getAsBoolean();
		if( !success ) {
			var errormessage = backObject.get("error").getAsString();
			throw new GrowattApiException("Could not login to Growatt API. JSON response contains an error: " + errormessage);
		}
	}

	private void readPlantId(JsonObject backObject) throws GrowattApiException {
		try {
			var dataArray  = backObject.getAsJsonArray("data");
			JsonObject plantObject = dataArray.get(this.plantIndex).getAsJsonObject();
			this.plantId = plantObject.get("plantId").getAsString();
		}
		catch( IndexOutOfBoundsException ex) {
			throw new GrowattApiException("Plant Index " + this.plantIndex + " seems to be not valid.", ex);
		}
		catch( NumberFormatException ex) {
			throw new GrowattApiException("Could not parse plant for plant index " + this.plantIndex + ". Please try with plant index 0 in your configuration.", ex);
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

    private JsonObject sendPlantInfoRequest() throws GrowattApiException {
        	String op = "getAllDeviceListTwo";
        	int pageNum = 1;
        	int pageSize = 1;
            String queryParams = String.format("op=%s&plantId=%s&pageNum=%d&pageSize=%d",
                    op,
                    plantId,
                    pageNum,
                    pageSize);

            String url = GROWATT_API_BASEURL + "/newTwoPlantAPI.do?" + queryParams;

            HttpResponse<String> response = null;
            try {
            	response = sendGetRequest(url);
            }
            catch (IOException | InterruptedException ex) {
            	throw new GrowattApiException("Could not get plant info from Growatt API", ex);
            }
        	var statusCode = response.statusCode();

        	// Read the response
        	if (statusCode == HttpURLConnection.HTTP_OK) {

        		// Parse the response JSON
        		return getJsonBody(response);        	
        	} 
        	
        	throw new GrowattApiException("Not a successful HTTP response code: " + statusCode + " / body: " + response.body());
    }

	private JsonObject getJsonBody(HttpResponse<String> response) throws GrowattApiException {
		var jsonString = response.body();
		JsonObject jsonObject = null;
		try {
			JsonElement jsonElement = JsonParser.parseString((String) jsonString);
			jsonObject = jsonElement.getAsJsonObject();                
		} catch (JsonParseException | IllegalStateException ex) {
			throw new GrowattApiException("Response body is not valid JSON", ex);
		}
		return jsonObject;
	}

    private HttpResponse<String> sendGetRequest(String url) throws IOException, InterruptedException  {

    	HttpRequest req = HttpRequest.newBuilder()
    			.uri(URI.create(url))
    			.header("Content-Type",  "application/json")
    			.header("User-Agent",  DEFAULT_USER_AGENT)
    			.GET()
    			.build();

    	HttpResponse<String> response = this.client.send(req, HttpResponse.BodyHandlers.ofString());
    	
    	return response;
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

        } catch (NoSuchAlgorithmException ex) {
            throw new RuntimeException(ex);
        }
    }
}

