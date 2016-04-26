/**
 *  Copyright 2016 Daniel Kurin
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
 *  Flow Meter Alert
 *
 *  Author: Daniel Kurin
 */
definition(
    name: "Flow Meter Alert!",
    namespace: "fortrezz",
    author: "FortrezZ, LLC",
    description: "Get a push notification or text message when your Flow Meter notices a problem.",
    category: "Green Living",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/App-PipesLeaksAndFloods.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/App-PipesLeaksAndFloods@2x.png"
)

preferences {
	section("Choose your Flow Meter Interface...") {
		input "alarm", "capability.energyMeter", title: "Where?"
	}
	section("Send notifications to...") {
		input("recipients", "contact", title: "Recipients", description: "Send notifications to") {
			input "phone", "phone", title: "Phone number?", required: false
		}
	}
}

def installed() {
	subscribe(alarm, "alarmState", alarmHandler)
}

def updated() {
	unsubscribe()
	subscribe(alarm, "alarmState", alarmHandler)
}

def alarmHandler(evt) {
	def deltaSeconds = 60

	def timeAgo = new Date(now() - (1000 * deltaSeconds))
	def recentEvents = alarm.eventsSince(timeAgo)
	log.debug "Found ${recentEvents?.size() ?: 0} events in the last $deltaSeconds seconds"
    log.debug evt.value

	def alreadySentSms = recentEvents.count { it.value && it.value == "wet" } > 1

	if (alreadySentSms) {
		log.debug "SMS already sent to $phone within the last $deltaSeconds seconds"
	} else {
		def msg = "$alarm: ${evt.value}."
		log.debug "$alarm is ${evt.value}, texting $phone"
        
        if(evt.value == "waterOverflow")
        {
        	msg = "$alarm: Flow Threshhold Exceeded."
        }
        else if(evt.value == "acMainsDisconnected")
        {
        	msg = "$alarm: AC Power Disconnected."
        }
        else if(evt.value == "acMainsReconnected")
        {
        	msg = "$alarm: AC Power Reconnected."
        }
        else if(evt.value == "replaceBatteryNow")
        {
        	msg = "$alarm: Replace AA Batteries Immediately."
        }
        else if(evt.value == "batteryReplaced")
        {
        	msg = "$alarm: AA Batteries Replaced."
        }
        else if(evt.value == "tempOverheated")
        {
        	msg = "$alarm: High Temperature Threshhold Exceeded."
        }
        else if(evt.value == "tempFreezing")
        {
        	msg = "$alarm: Low Temperature Threshhold Exceeded."
        }

		if (location.contactBookEnabled) {
			sendNotificationToContacts(msg, recipients)
		}
		else {
			sendPush(msg)
			if (phone) {
				sendSms(phone, msg)
			}
		}
	}
}