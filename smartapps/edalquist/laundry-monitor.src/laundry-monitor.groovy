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
import java.util.concurrent.TimeUnit
 
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
        input "minimumOffTime", "decimal", title: "Minimum amount of below wattage time to trigger off (minutes)", required: false, defaultValue: 1
        input "maximumUpdateGap", "decimal", title: "Maximum time between power usage updates before ending cycle (minutes)", required: false, defaultValue: 10
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
    runEvery5Minutes(checkIfDone);
}

// Run in seems to actually run sooner than requested so we skew the delay by a few seconds
def checkDoneIn(delayInSeconds) {
	runIn(delayInSeconds + 2, checkIfDone);
}

def powerInputHandler(evt) {
    atomicState.latestPower = sensor1.currentValue("power")
    atomicState.latestUpdate = now()
    log.trace "State: ${atomicState}"

    if (atomicState.startedAt == null) { // Not currently Running
        if (atomicState.latestPower >= minimumWattage) { // Power is above minimum
            // Start the cycle!
            atomicState.startedAt = atomicState.latestUpdate
            atomicState.lastLowTime = null
            log.info "Starting Cycle: ${message}"
            log.trace "Starting Cycle: ${atomicState}"
        }
        // else, power is below minimum, we can just ignore it
    } else { // Currently Running!
        if (atomicState.latestPower < minimumWattage) { // Power is below minimum, maybe done?
            if (atomicState.lastLowTime == null) { // Haven't seen a low since the last high
                // Record the time the low power was observed
                atomicState.lastLowTime = atomicState.latestUpdate
                log.trace "Hit low-power for first time after high-power: ${atomicState}"

                // Schedule callback for minimumOffTime from now to check if power has been low for long enough
                checkDoneIn(TimeUnit.MINUTES.toSeconds(minimumOffTime));
            }
        } else if (atomicState.lastLowTime != null) { // Power is above minimum, reset any pending low atomicState
            log.trace "Hit high-power for first time after low-power: ${atomicState}"

            // Power usage is above minimum, clear lastLowTime
            atomicState.lastLowTime = null
        }
    }
}

// Called every 5min and after a min power event is detected
def checkIfDone() {
    log.trace "CheckIfDone: ${atomicState}"

    if (atomicState.startedAt != null) { // Currently Running
        if (atomicState.lastLowTime != null) {  // there is a low power event
            def lastLowDeltaMillis = (now() - atomicState.lastLowTime)
            def minOffTimeMillis = TimeUnit.MINUTES.toMillis(minimumOffTime)
            log.trace "LowLongEnoughCheck: ${lastLowDeltaMillis} >= ${minOffTimeMillis}"

            if (lastLowDeltaMillis >= minOffTimeMillis) { // Power has been low for long enough, end the cycle
                log.trace "Still running and low time is is longer than minimumOffTime"
                endCycle()
            } else { // Has a low time but isn't done yet, re-schedule another check for the future
                def reSchedTime = TimeUnit.MILLISECONDS.toSeconds(minOffTimeMillis - lastLowDeltaMillis)
                log.trace "Re-Scheduling checkIfDone in ${reSchedTime} seconds"
                checkDoneIn(reSchedTime);
            }
        } else { // No low power event
            def lastUpdateDeltaMillis = (now() - atomicState.latestUpdate)
            def maxGapMillis = TimeUnit.MINUTES.toMillis(maximumUpdateGap)
            log.trace "RecentUpdateCheck: ${lastUpdateDeltaMillis} >= ${maxGapMillis}"

        	if (lastUpdateDeltaMillis < maxGapMillis) { // it has been too long since an update
                log.trace "It has been too long since the last power update, assume cycle is done"
                endCycle()
            }
        }
    }
}

def endCycle() {
    log.info "Ending Cycle: ${message}"

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

    atomicState.latestPower = null
    atomicState.latestUpdate = null
    atomicState.startedAt = null
    atomicState.lastLowTime = null
}

private hideOptionsSection() {
  (phone || switches) ? false : true
}
