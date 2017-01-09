/**
 *  Laundry Monitor
 *
 *  Copyright 2017 Eric Dalquist based on code by Brandon Miller
 *    See: https://github.com/bmmiller/SmartThings/blob/master/smartapps/bmmiller/laundry-monitor.src/laundry-monitor.groovy
 * 
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
 
import groovy.time.* 
 
definition(
    name: "Laundry Monitor",
    namespace: "edalquist",
    author: "Brandon Miller & Eric Dalquist",
    description: "This application is a modification of the SmartThings Laundry Monitor SmartApp.  Instead of using a vibration sensor, this utilizes Power (Wattage) draw from an Aeon Smart Energy Meter.",
    category: "Convenience",
    iconUrl: "http://www.vivevita.com/wp-content/uploads/2009/10/recreation_sign_laundry.png",
    iconX2Url: "http://www.vivevita.com/wp-content/uploads/2009/10/recreation_sign_laundry.png")


preferences {
	section("Tell me when this washer/dryer has stopped..."){
		input "sensor1", "capability.powerMeter"
	}
    
    section("Notifications") {
		input "sendPushMessage", "bool", title: "Push Notifications?"
		input "phone", "phone", title: "Send a text message?", required: false
            paragraph "For multiple SMS recipients, separate phone numbers with a semicolon(;)"      
	}

	section("System Variables"){
    	input "minimumWattage", "decimal", title: "Minimum running wattage", required: false, defaultValue: 50
        input "minimumOffTime", "decimal", title: "Minimum amount of below wattage time to trigger off (secs)", required: false, defaultValue: 60
        input "message", "text", title: "Notification message", description: "Laundry is done!", required: true
	}
	
	section ("Additionally", hidden: hideOptionsSection(), hideable: true) {
        input "switches", "capability.switch", title: "Turn on these switches?", required:false, multiple:true
	    input "speech", "capability.speechSynthesis", title:"Speak message via: ", multiple: true, required: false
	}
}

def installed() {
	log.debug "Installed with settings: ${settings}"

	initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"

	unsubscribe()
	initialize()
}

def initialize() {
	subscribe(sensor1, "power", powerInputHandler)
}

def powerInputHandler(evt) {
	def latestPower = sensor1.currentValue("power")
    log.trace "Power: ${latestPower}W, State: ${atomicState}"
    
    if (atomicState.startedAt == null && latestPower >= minimumWattage) {
	    // If not running and current power usage is greater than the minimum
		atomicState.startedAt = now()
        atomicState.lastLowTime = null
        log.info "Starting Cycle: ${message}"
        log.trace "Starting Cycle: ${atomicState}"
    } else if (atomicState.startedAt != null) {
    	if (latestPower < minimumWattage) {
        	if (atomicState.lastLowTime == null) {
        		// If power is below minimum for the first time in "minimumOffTime" record the current time
                atomicState.lastLowTime = now()
                // Schedule callback to check if the power is still low
                runIn(minimumOffTime, checkIfDone);

	            log.trace "Hit low-power for first time after high-power: ${atomicState}"
            }
        } else {
        	if (atomicState.lastLowTime != null) {
            	log.trace "Hit high-power for first time after low-power: ${atomicState}"
            }

        	// Power usage is above minimum, clear lastLowTime
        	atomicState.lastLowTime = null;
        }
    }
}

// Called after minimumOffTime has passed to see if lastLowTime is still set AND if it is old enough
def checkIfDone() {
	log.trace "CheckIfDone: ${atomicState}"

	if (atomicState.lastLowTime != null && (now() - atomicState.lastLowTime) > (minimumOffTime * 1000)) {
		log.trace "Still running and low time is is longer than minimumOffTime"  
        log.info "Ending Cycle: ${message}"
        
        atomicState.startedAt = null;
        atomicState.lastLowTime = null;
        
        if (phone) {
            if ( phone.indexOf(";") > 1){
                def phones = phone.split(";")
                for ( def i = 0; i < phones.size(); i++) {
                    sendSms(phones[i], message)
                }
            } else {
                sendSms(phone, message)
            }
        }

        if (sendPushMessage) {
            sendPush message
        }

        if (switches) {
            switches*.on()
        }               
        if (speech) { 
            speech.speak(message) 
        }  
    }
}

private hideOptionsSection() {
  (phone || switches) ? false : true
}