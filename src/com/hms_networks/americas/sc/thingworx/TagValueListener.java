package com.hms_networks.americas.sc.thingworx;

import com.ewon.ewonitf.EvtTagValueListener;
import com.hms_networks.americas.sc.logging.Logger;

public class TagValueListener extends EvtTagValueListener {

    int oldState = -1;
  public void callTagChanged() {
    int value = getTagValueAsInt();
    Logger.LOG_INFO("tag value changed! " + value);
    
    
    // if the value change to the correct state
    // call tag updater
    if(value == 1) {
	TagUpdatePoller.managUpdates();
    }
    oldState = value;
    
  }
    
}
