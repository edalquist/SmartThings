/**
 *  Fortrezz MIMOlite
 *
 *  Author: Based on SmartThings code
 *  Also based on MIMOlite GarageDoor code from JitJack
 *  Date: 2016-30-01
 *
 * MAKE SURE TO HIT "REFRESH BUTTON" (mobile app) to have hub join Group 3 (alarm report for power).
 *
 */

// for the UI
metadata {

	definition (name: "FortrezZ MIMOlite", author: "bsa1969@yahoo.com") {
		capability "Polling"
		capability "Refresh"
		capability "Switch"
		capability "Contact Sensor"
		attribute "powered", "string"

		command "on"
		command "off"
		fingerprint deviceId: "0x1000", inClusters: "0x72,0x86,0x71,0x30,0x31,0x35,0x70,0x85,0x25,0x03"
 
	}

	// simulator metadata
	simulator {
    	// status messages for Relay
        status "on":  "command: 2503, payload: 00"
		status "off": "command: 2503, payload: FF"

		// reply messages
		reply "2001FF,delay 100,2502": "command: 2503, payload: FF"
		reply "200100,delay 100,2502": "command: 2503, payload: 00"

		// status messages for contact
		status "open":  "command: 2001, payload: FF"
		status "closed": "command: 2001, payload: 00"
        
        //staus message for Power status
        status "powerOn": "command: 7105, payload: 00"
        status "powerOff": "command: 7105, payload: FF"
	}

	// UI tile definitions
	tiles {
        standardTile("contact", "device.contact") {
			state "open", label: "DO Contact Open", icon: "st.contact.contact.open", backgroundColor: "#79b821"
			state "closed", label: "DO Contact Closed", icon: "st.contact.contact.closed", backgroundColor: "#FF0000"
		}
        standardTile("switch", "device.switch", canChangeIcon: true) {
			state "on", label: "DI Switch On", action: "switch.off", icon: "st.switch.switch.on", backgroundColor: "#FF0000"
			state "off", label: "DI Switch Off", action: "switch.on", icon: "st.switch.switch.off", backgroundColor: "#79b821"
        }
        standardTile("refresh", "device.switch", decoration: "flat") {
			state "default", label: "Refresh", action:"refresh.refresh", icon:"st.secondary.refresh"
		}
        standardTile("powered", "device.powered", inactiveLabel: false) {
			state "powerOn", label: "Power >11 vdc", icon: "st.switches.switch.on", backgroundColor: "#79b821"
			state "powerOff", label: "Power <10.5 vdc", icon: "st.switches.switch.off", backgroundColor: "#ffa81e"
		}

        valueTile("alarm", "device.alarm") {
			state "alarm", label:'${currentValue}'
		}


		main (["switch", "contact"])
		details(["switch", "contact", "powered", "refresh", "alarm"])
	}
}
// This is the main entry point for interupts coming from the device.
def parse(String description) {
	log.debug "MIMO Device Handler - Parse. Description is: ${description}"
    def result = null
	def cmd = zwave.parse(description, [0x20: 1, 0x84: 1, 0x30: 1, 0x70: 1])
    if (cmd.CMD == "7105") {				//Mimo sent a power loss report
        if (cmd.v1AlarmLevel == 255) {
            sendEvent(name: "powered", value: "powerOff", descriptionText: "$device.displayName Lost Power")
       } else {
            sendEvent(name: "powered", value: "powerOn", descriptionText: "$device.displayName Regained Power")
       }
    }
    else if (cmd.CMD == "2001") {			// Contact Sensor Report
    	// Often the MIMO does not send a power restored event, so the following is a catchall.
        // This does not seem to be the case when it is senseing a supply battery low as opposed to a disconnect.
    	sendEvent(name: "powered", value: "powerOn", descriptionText: "$device.displayName Regained Power")
        if (cmd.value == 255) {
            sendEvent (name: "contact", value: "open", descriptionText: "$device.displayName Contact Open")
        } else {
            sendEvent (name: "contact", value: "closed", descriptionText: "$device.displayName Contact Closed")
         }
     }
    else if (cmd.CMD == "2503") {			// Switch Status Report
        if (cmd.value == 255) {
            sendEvent (name: "switch", value: "on", descriptionText: "$device.displayName Switch On")
        } else {
            sendEvent (name: "switch", value: "off", descriptionText: "$device.displayName Switch Off")
       }
      } 
	else if (cmd) {
		result = zwaveEvent(cmd)
        log.debug "Parsed ${cmd} to ${result.inspect()}"
    } else {
    log.debug "Non-parsed event: ${description}"
	}
	return result
}
// Note: Normal switch, contact, and power status are handled in the Parse handler. The Event handlers are typically not used.
// On, Off, Poll, and Refresh (below) are used.
def sensorValueEvent(Short value) {
	log.debug "sensorValueEvent - Parse returned ${result?.descriptionText}"
    if (value) {
		createEvent(name: "contact", value: "open", descriptionText: "$device.displayName No Camera Event")
	} else {
		createEvent(name: "contact", value: "closed", descriptionText: "$device.displayName Camera Event")
	}
}
def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd) {
	[name: "switch", value: cmd.value ? "on" : "off", type: "physical"]
    log.debug "zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd) "
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicSet cmd)
{
	sensorValueEvent(cmd.value)
}

def zwaveEvent(physicalgraph.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
	[name: "switch", value: cmd.value ? "on" : "off", type: "digital"]
    log.debug "zwaveEvent(physicalgraph.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) "
}

def zwaveEvent(physicalgraph.zwave.commands.sensorbinaryv1.SensorBinaryReport cmd)
{
	sensorValueEvent(cmd.sensorValue)
}

def zwaveEvent(physicalgraph.zwave.commands.alarmv1.AlarmReport cmd)
{ 
	log.debug "Alarm Event ${result?.descriptionText}"
	sensorValueEvent(cmd.sensorState)
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
	log.debug "zwaveEvent(physicalgraph.zwave.Command cmd)"
	// Handles all Z-Wave commands we aren't interested in
	[:]
}

def on() {
	delayBetween([
		zwave.basicV1.basicSet(value: 0xFF).format(),
		zwave.switchBinaryV1.switchBinaryGet().format()
        // log.debug "line 138"
	])
}

def off() {
	delayBetween([
		zwave.basicV1.basicSet(value: 0x00).format(),
		zwave.switchBinaryV1.switchBinaryGet().format()
        // log.debug "line 146"
	])
}

def poll() {
	zwave.switchBinaryV1.switchBinaryGet().format()
    log.debug "Polling"
}

def refresh() {
	zwave.switchBinaryV1.switchBinaryGet().format()
    log.debug "Refresh - This will also join Group 3 which allows for the Powered messages to come through"
    zwave.associationV1.associationSet(groupingIdentifier:3, nodeId:[zwaveHubNodeId]).format()
}
