package com.hms_networks.americas.sc.thingworx;

import com.ewon.ewonitf.EWException;
import com.ewon.ewonitf.SysControlBlock;
import com.ewon.ewonitf.TagControl;
import com.hms_networks.americas.sc.json.JSONArray;
import com.hms_networks.americas.sc.json.JSONException;
import com.hms_networks.americas.sc.json.JSONObject;
import com.hms_networks.americas.sc.json.JSONTokener;
import com.hms_networks.americas.sc.logging.Logger;
import java.util.ArrayList;

public class TagUpdatePoller {
  ArrayList parsedInfo;
  static String responseFile = "/usr/response.json";
  static String jsonResponse;
  static final String Tag_UPDATE_TRIGGER_NAME = "tagUpdateTrigger"; 

  public static boolean pollForTagUpdate() {
    boolean needToParse = false;

    String addInfoEndpointFullUrl =
        "https://tw2.hmsamericas.com:8443/Thingworx/Things/ConnectorHost/Services/TestEndpoint";
    String addInfoRequestHeader = null;
    try {
      addInfoRequestHeader =
          "Accept=application/json&Content-Type=application/json&appKey="
              + TWConnectorMain.getConnectorConfig().getThingworxAppKey();
    } catch (JSONException e1) {
      e1.printStackTrace();
    }
    String json = "";

    try {
      jsonResponse = TWApiManager.httpPost(addInfoEndpointFullUrl, addInfoRequestHeader, json);
    } catch (Exception e) {
      Logger.LOG_CRITICAL(
          "An error occurred while performing an HTTP POST to Thingworx. Data may have"
              + " been lost!");
      Logger.LOG_EXCEPTION(e);
    }

    // set need to parse to true only if we need to parse
    needToParse = true;

    return needToParse;
  }

  // need better name, also has push in it
  private static boolean parseTagUpdate() {
    // {"api-version":"1.4","tags":[{"name":"tagname1","type":"float","value":23.1},{"name":"tagname2","type":"bool","value":false},{"name":"tagname3","type":"integer","value":3}]}
    JSONTokener JsonT = new JSONTokener(jsonResponse);
    try {
      JSONObject allJSON = new JSONObject(JsonT);
      JSONArray tags = allJSON.getJSONArray("tags");

      // for each tag that needs to be updated
      for (int i = 0; i < tags.length(); i++) {
        Logger.LOG_INFO("name is " + ((JSONObject) tags.get(i)).getString("name"));
        String name = ((JSONObject) tags.get(i)).getString("name");
        String type = ((JSONObject) tags.get(i)).getString("type");
        String value = ((JSONObject) tags.get(i)).getString("value");
 
        Logger.LOG_INFO("pushing update: "+name +", " +type+", "+value);
        pushUpdates(name, type, value);
      }

    } catch (JSONException e) {
      e.printStackTrace();
    }

    // saves information into class array list.
    // returns true if update is required
    return true;
  }

  private static void pushUpdates(String name, String type, String value) {

      TagControl tagCtrl = null;
      try {
	  tagCtrl = new TagControl(name);
      if(type.equalsIgnoreCase("integer")) {
	  tagCtrl.setTagValueAsInt(Integer.parseInt(value));
      } else if(type.equalsIgnoreCase("float")) {
	  tagCtrl.setTagValueAsDouble(Double.parseDouble(value)); 
      } else  if(type.equalsIgnoreCase("boolean")) {
	  tagCtrl.setTagValueAsInt(Integer.parseInt(value));
      }
      
      // if hits catch, tag might not exist. assume it exists for now
    } catch (EWException e1) {
      System.out.println("Exception: TagControl obj");
      e1.printStackTrace();
    }
/*
    try {
      SysControlBlock SCB = new SysControlBlock(com.ewon.ewonitf.SysControlBlock.TAG, name);
      SCB.setItem("TagValue", value);
      SCB.setItem("Type", type);
      SCB.saveBlock();
    } catch (Exception e) {
      System.out.println("Exception: SCB obj");
      e.printStackTrace();
    }
    */
  }
  
  private static void ackknowledgeUpdate() {
      
      try {
	  TagControl tagControl = new TagControl("RecipeUpdateTriggerAck");
	  tagControl.setTagValueAsInt(1);
      } catch (EWException e){
  e.printStackTrace();}
  }

  public static void managUpdates() {
    if (pollForTagUpdate()) {
      parseTagUpdate();
    }
    ackknowledgeUpdate();
  }
}
