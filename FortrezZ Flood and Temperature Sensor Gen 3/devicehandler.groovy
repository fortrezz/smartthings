/**
 *  Copyright 2015 SmartThings
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
metadata {
	definition (name: "Wireless Flood / Temperature Sensor", namespace: "fortrezz", author: "FortrezZ, LLC") {
		capability "Water Sensor"
		capability "Sensor"
		capability "Battery"
        capability "Temperature Measurement"
        capability "Polling"

        fingerprint mfr: "0084", prod: "0073"
        fingerprint mfr: "0072", prod: "0500"
        //zw:S type:0701 mfr:0084 prod:0073 model:0005 ver:0.05 zwv:4.38 lib:06 cc:5E,86,72,5A,73,20,80,71,85,59,84,31,70 role:06 ff:8C05 ui:8C05
	}
    preferences {
        input ("version", "text", title: "Plugin Version 1.5", description:"", required: false, displayDuringSetup: true)
       }

	simulator {
		status "dry": "command: 7105, payload: 00 00 00 FF 05 FE 00 00"
		status "wet": "command: 7105, payload: 00 FF 00 FF 05 02 00 00"
		status "overheated": "command: 7105, payload: 00 00 00 FF 04 02 00 00"
		status "freezing": "command: 7105, payload: 00 00 00 FF 04 05 00 00"
		status "normal": "command: 7105, payload: 00 00 00 FF 04 FE 00 00"
		for (int i = 0; i <= 100; i += 20) {
			status "battery ${i}%": new physicalgraph.zwave.Zwave().batteryV1.batteryReport(batteryLevel: i).incomingMessage()
		}
	}
	
	tiles(scale: 2) {
		multiAttributeTile(name:"water", type: "generic", width: 6, height: 4){
			tileAttribute ("device.water", key: "PRIMARY_CONTROL") {
				attributeState "dry", label: "Dry", icon:"st.alarm.water.dry", backgroundColor:"#ffffff"
				attributeState "wet", label: "Wet", icon:"st.alarm.water.wet", backgroundColor:"#53a7c0"
			}
		}
		standardTile("temperatureState", "device.temperature", width: 2, height: 2) {
			state "normal", icon:"st.alarm.temperature.normal", backgroundColor:"#ffffff"
			state "freezing", icon:"st.alarm.temperature.freeze", backgroundColor:"#53a7c0"
			state "overheated", icon:"st.alarm.temperature.overheat", backgroundColor:"#F80000"
		}
		valueTile("temperature", "device.temperature", width: 2, height: 2) {
            state("temperature", label:'${currentValue}°',
                backgroundColors:[
                    [value: 31, color: "#153591"],
                    [value: 44, color: "#1e9cbb"],
                    [value: 59, color: "#90d2a7"],
                    [value: 74, color: "#44b621"],
                    [value: 84, color: "#f1d801"],
                    [value: 95, color: "#d04e00"],
                    [value: 96, color: "#bc2323"]
                ]
            )
        }
		valueTile("battery", "device.battery", decoration: "flat", inactiveLabel: false, width: 2, height: 2) {
			state "battery", label:'${currentValue}% battery', unit:""
		}
        standardTile("powered", "device.powered", width: 2, height: 2, inactiveLabel: false) {
        	// state blank for non-mains
			state "powerOn", label: "Power On", icon: "st.switches.switch.on", backgroundColor: "#79b821"
			state "powerOff", label: "Power Off", icon: "st.switches.switch.off", backgroundColor: "#ffa81e"
		}
		valueTile("poll", "device.poll", width: 2, height: 2, canChangeIcon: false, canChangeBackground: false, decoration: "flat") {
			state "blank", label:''
//			state "zero", label:'Poll', action: 'poll'
		}
		valueTile("systemStatus", "device.systemStatus", width: 2, height: 2, canChangeIcon: false, canChangeBackground: false, decoration: "flat") {
			state "blank", label:''
		}
		main (["water", "temperatureState"])
		details(["water", "temperature", "temperatureState", "battery", "systemStatus", "poll", "powered"])
	}
}

def poll() {
	// Get Temperature
    // Get Wet Status
    return delayBetween([
        zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType:1).format(),
        zwave.notificationV3.notificationGet().format(),
        zwave.batteryV1.batteryGet().format()
    ], 200)
}

def installed() {
    log.debug "Device Installed..."
    return response(configure())
}

def updated() { // neat built-in smartThings function which automatically runs whenever any setting inputs are changed in the preferences menu of the device handler
    
    log.debug "Settings Updated..."
    return response(delayBetween([
        secure(configure()), // the response() function is used for sending commands in reponse to an event, without it, no zWave commands will work for contained function
        secure(zwave.associationV2.associationGet(groupingIdentifier:8))
    ], 200))

}

def parse(String description) {
	def result = []
	def parsedZwEvent = zwave.parse(description, [0x30: 1, 0x71: 3, 0x84: 1])

	log.debug("Raw Event: ${parsedZwEvent}")
	if(parsedZwEvent) {
		if(parsedZwEvent.CMD == "8407") {
			def lastStatus = device.currentState("battery")
			def ageInMinutes = lastStatus ? (new Date().time - lastStatus.date.time)/60000 : 600
			log.debug "Battery status was last checked ${ageInMinutes} minutes ago"

			if (ageInMinutes >= 600) {
				log.debug "Battery status is outdated, requesting battery report"
				result << new physicalgraph.device.HubAction(zwave.batteryV1.batteryGet().format())
			}
			result << new physicalgraph.device.HubAction(zwave.wakeUpV1.wakeUpNoMoreInformation().format())
		}
    result << createEvent( zwaveEvent(parsedZwEvent) )
	}
	if(!result) result = [ descriptionText: parsedZwEvent, displayed: false ]
	log.debug "Parse returned ${result}"
	return result
}

def zwaveEvent(physicalgraph.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) { //standard security encapsulation event code (should be the same on all device handlers)
    def encapsulatedCommand = cmd.encapsulatedCommand()
    // can specify command class versions here like in zwave.parse
    if (encapsulatedCommand) {
        return zwaveEvent(encapsulatedCommand)
    }
}

def zwaveEvent(physicalgraph.zwave.commands.wakeupv1.WakeUpNotification cmd)
{
	[descriptionText: "${device.displayName} woke up", isStateChange: false]
}

def zwaveEvent(physicalgraph.zwave.commands.sensorbinaryv1.SensorBinaryReport cmd)
{
	def map = [:]
	map.name = "water"
	map.value = cmd.sensorValue ? "wet" : "dry"
	map.descriptionText = "${device.displayName} is ${map.value}"
	map
}

def zwaveEvent(physicalgraph.zwave.commands.batteryv1.BatteryReport cmd) {
	def map = [:]
	if(cmd.batteryLevel == 0xFF) {
		map.name = "battery"
		map.value = 1
		map.descriptionText = "${device.displayName} has a low battery"
		map.displayed = true
	} else {
		map.name = "battery"
		map.value = cmd.batteryLevel > 0 ? cmd.batteryLevel.toString() : 1
		map.unit = "%"
		map.displayed = false
	}
	map
}

def zwaveEvent(physicalgraph.zwave.commands.notificationv3.NotificationReport cmd)
{
	def map = [:]
	if (cmd.notificationType == physicalgraph.zwave.commands.notificationv3.NotificationReport.NOTIFICATION_TYPE_WATER) {
		map.name = "water"
        if(cmd.event == 1 || cmd.event == 2) {
        	map.value = "wet"
        }
        else if (cmd.event == 0) {
        	map.value = "dry"
        }
		map.descriptionText = "${device.displayName} is ${map.value}"
	}
	if(cmd.notificationType ==  physicalgraph.zwave.commands.notificationv3.NotificationReport.NOTIFICATION_TYPE_HEAT) {
		map.name = "temperatureState"
		if(cmd.event == 1 || cmd.event == 2) { map.value = "overheated"}
		if(cmd.event == 3 || cmd.event == 4) { map.value = "changing temperature rapidly"}
		if(cmd.event == 5 || cmd.event == 5) { map.value = "freezing"}
		if(cmd.event == 0 || cmd.event == 254) { map.value = "normal"}
		map.descriptionText = "${device.displayName} is ${map.value}"
	}
	if (cmd.notificationType == physicalgraph.zwave.commands.notificationv3.NotificationReport.NOTIFICATION_TYPE_POWER_MANAGEMENT) {
		map.name = "powered"
        if(cmd.event == 2 || cmd.event == 0x0B) {
        	map.value = "powerOff"
        }
        else if (cmd.event == 3 || cmd.event == 0x0D) {
        	map.value = "powerOn"
        }
		map.descriptionText = "${device.displayName} is ${map.value}"
	}

	map
}

def zwaveEvent(physicalgraph.zwave.commands.sensormultilevelv5.SensorMultilevelReport cmd)
{
	def map = [:]
	if(cmd.sensorType == 1) {
		map.name = "temperature"
        if(cmd.scale == 0) {
        	map.value = getTemperature(cmd.scaledSensorValue)
        } else {
	        map.value = cmd.scaledSensorValue
        }
        map.unit = location.temperatureScale
	}
	map
}

def configure() {
	log.debug "Configuring...." 
    return zwave.associationV2.associationSet(groupingIdentifier:8, nodeId:[zwaveHubNodeId])
}

private secure(physicalgraph.zwave.Command cmd) { //take multiChannel message and securely encrypts the message so the device can read it
	return zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
}

def getTemperature(value) {
	if(location.temperatureScale == "C"){
		return value
    } else {
        return Math.round(celsiusToFahrenheit(value))
    }
}

def zwaveEvent(physicalgraph.zwave.Command cmd)
{
	log.debug "COMMAND CLASS: $cmd"
}