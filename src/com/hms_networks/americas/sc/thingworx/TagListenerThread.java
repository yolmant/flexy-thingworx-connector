package com.hms_networks.americas.sc.thingworx;

import com.ewon.ewonitf.DefaultEventHandler;

public class TagListenerThread  extends Thread {
    
    public TagListenerThread() {}
    
    public void run() {
	 TagValueListener tagListenerTester = new TagValueListener();
	    tagListenerTester.setTagName("RecipeUpdateTrigger");
	    DefaultEventHandler.addTagValueListener(tagListenerTester);
	    try {
		DefaultEventHandler.runEventManager();
	    } catch (Exception e1){
	  e1.printStackTrace();
	  }
	    
	    try {
        DefaultEventHandler.runEventManager();
      } catch (Exception e) {

        e.printStackTrace();
      }
    }
}
