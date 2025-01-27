package com.hms_networks.americas.sc.thingworx.data;

import com.ewon.ewonitf.EWException;
import com.ewon.ewonitf.ScheduledActionManager;
import com.hms_networks.americas.sc.fileutils.FileAccessManager;
import com.hms_networks.americas.sc.logging.Logger;
import com.hms_networks.americas.sc.thingworx.TWConnectorConsts;
import com.hms_networks.americas.sc.thingworx.TWConnectorMain;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Class for managing HTTP API calls to the Thingworx API.
 *
 * @since 1.0
 * @version 1.1
 * @author HMS Networks, MU Americas Solution Center
 */
public class TWApiManager {

  /**
   * String constant returned by the {@link #httpPost(String, String, String)} method to indicate an
   * Ewon error occurred.
   */
  private static final String EWON_ERROR_STRING_RESPONSE = "EwonError";

  /**
   * String constant returned by the {@link #httpPost(String, String, String)} method to indicate an
   * authentication error occurred.
   */
  private static final String AUTH_ERROR_STRING_RESPONSE = "AuthError";

  /**
   * String constant returned by the {@link #httpPost(String, String, String)} method to indicate a
   * connection error occurred.
   */
  private static final String CONNECTION_ERROR_STRING_RESPONSE = "ConnectionError";

  /** The interval at which pending data payloads are sent to Thingworx. */
  private static final long DATA_SEND_THREAD_INTERVAL_MILLIS = 5000;

  /** Boolean indicating if the data send thread should run. */
  private static boolean runDataThread = true;

  /** Sets the run data thread flag to false to indicate that the tread should stop running. */
  public static void setDataThreadStopFlag() {
    synchronized (TWApiManager.class) {
      runDataThread = false;
    }
  }

  /**
   * Gets the name of the Ewon Flexy as it appears/should appear in Thingworx.
   *
   * @return Thingworx device name
   * @since 1.0
   */
  public static String getApiDeviceName() {
    return "FLEXY-" + TWConnectorConsts.EWON_SERIAL_NUMBER;
  }

  /** Starts the thread which sends pending data payloads to Thingworx. */
  public static void startDataSendThread() {
    // Build runnable to send pending payloads to Thingworx
    Runnable dataSendThreadRunnable =
        new Runnable() {
          public void run() {
            // Loop until stopped
            boolean stayInLoop = true;
            while (stayInLoop) {
              // Copy pending payloads from data manager
              List pendingPayloads = new ArrayList(TWDataManager.getPayloadsToSend());

              // Iterate through each pending payload and send
              Iterator pendingPayloadsIterator = pendingPayloads.iterator();
              while (pendingPayloadsIterator.hasNext()) {
                TWDataPayload dataPayload = (TWDataPayload) pendingPayloadsIterator.next();

                // Send to Thingworx
                boolean isSuccessful = sendJsonToThingworx(dataPayload.getPayloadString());

                // If successful, remove from pending payloads
                if (isSuccessful) {
                  Logger.LOG_DEBUG(
                      "Successfully sent a payload to Thingworx with "
                          + dataPayload.getDataPointCount()
                          + " data points.");
                  TWDataManager.removedPendingPayload(dataPayload);
                } else {
                  Logger.LOG_SERIOUS(
                      "A payload containing "
                          + dataPayload.getDataPointCount()
                          + " data points failed to send to Thingworx.");
                }
              }

              // Update keepRunning
              synchronized (TWApiManager.class) {
                stayInLoop = runDataThread;
              }

              // Delay until next interval
              try {
                Thread.sleep(DATA_SEND_THREAD_INTERVAL_MILLIS);
              } catch (Exception e) {
                Logger.LOG_WARN(
                    "An error occurred while sleeping the data send thread until its next run"
                        + " interval!");
                Logger.LOG_EXCEPTION(e);
              }
            }
          }
        };

    // Create new thread object and run
    Thread dataSendThread = new Thread(dataSendThreadRunnable);
    dataSendThread.start();
  }

  /**
   * Sends the specified JSON string to the configured Thingworx Full URL endpoint as an HTTP POST
   * request
   *
   * @param json JSON body
   * @since 1.1
   */
  private static boolean sendJsonToThingworx(String json) {
    // Send to Thingworx
    // Build full POST request URL
    String addInfoEndpointFullUrl = "";
    String addInfoRequestHeader = "";
    try {
      addInfoEndpointFullUrl = TWConnectorMain.getConnectorConfig().getThingworxFullUrl();
      addInfoRequestHeader =
          "Content-Type=application/json&appKey="
              + TWConnectorMain.getConnectorConfig().getThingworxAppKey();
    } catch (Exception e) {
      Logger.LOG_CRITICAL(
          "Unable to get configuration information for sending data to Thingworx. Data"
              + " may be lost!");
      Logger.LOG_EXCEPTION(e);
    }

    String response = null;
    boolean isSuccessful = true;
    try {
      response = httpPost(addInfoEndpointFullUrl, addInfoRequestHeader, json);
      if (response != null
          && (response.equals(EWON_ERROR_STRING_RESPONSE)
              || response.equals(AUTH_ERROR_STRING_RESPONSE)
              || response.equals(CONNECTION_ERROR_STRING_RESPONSE))) {
        isSuccessful = false;
      }
    } catch (Exception e) {
      Logger.LOG_CRITICAL(
          "An error occurred while performing an HTTP POST to Thingworx. Data may have"
              + " been lost!");
      Logger.LOG_EXCEPTION(e);
      isSuccessful = false;
    }
    Logger.LOG_DEBUG("Thingworx HTTP POST response: " + response);
    return isSuccessful;
  }

  /**
   * Performs an HTTP POST requests to the specified URL using the specified request header and
   * body.
   *
   * @param url URL to make request
   * @param header request header
   * @param body request body
   * @throws EWException if unable to make POST request
   * @since 1.1
   */
  public static String httpPost(String url, String header, String body)
      throws EWException, IOException {
    // Create file for storing response
    final File responseFile = new File("/usr/http/response.post");
    responseFile.getParentFile().mkdirs();
    responseFile.delete();

    // Perform POST request to specified URL
    int httpStatus =
        ScheduledActionManager.RequestHttpX(
            url,
            TWConnectorConsts.HTTP_POST_STRING,
            header,
            body,
            "",
            responseFile.getAbsolutePath());

    // Read response contents and return
    String responseFileString = "";
    if (httpStatus == TWConnectorConsts.HTTPX_CODE_NO_ERROR) {
      responseFileString = FileAccessManager.readFileToString(responseFile.getAbsolutePath());
    } else if (httpStatus == TWConnectorConsts.HTTPX_CODE_EWON_ERROR) {
      Logger.LOG_SERIOUS(
          "An Ewon error was encountered while performing an HTTP POST request to "
              + url
              + "! Data loss may result.");
      responseFileString = EWON_ERROR_STRING_RESPONSE;
    } else if (httpStatus == TWConnectorConsts.HTTPX_CODE_AUTH_ERROR) {
      Logger.LOG_SERIOUS(
          "An authentication error was encountered while performing an HTTP POST request to "
              + url
              + "! Data loss may result.");
      responseFileString = AUTH_ERROR_STRING_RESPONSE;
    } else if (httpStatus == TWConnectorConsts.HTTPX_CODE_CONNECTION_ERROR) {
      Logger.LOG_SERIOUS(
          "A connection error was encountered while performing an HTTP POST request to "
              + url
              + "! Data loss may result.");
      responseFileString = CONNECTION_ERROR_STRING_RESPONSE;
    } else {
      Logger.LOG_SERIOUS(
          "An unknown error ("
              + httpStatus
              + ") was encountered while performing an HTTP POST request to "
              + url
              + "! Data loss may result.");
      responseFileString = String.valueOf(httpStatus);
    }
    return responseFileString;
  }
}
