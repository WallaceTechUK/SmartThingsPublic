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
	definition (name: "Z-Wave Switch Secure", namespace: "smartthings", author: "SmartThings", ocfDeviceType: "oic.d.switch") {
		capability "Switch"
		capability "Refresh"
		capability "Polling"
		capability "Actuator"
		capability "Sensor"

		fingerprint inClusters: "0x25,0x98"
		fingerprint deviceId: "0x10", inClusters: "0x98"
	}

	simulator {
		status "on":  "command: 9881, payload: 002503FF"
		status "off": "command: 9881, payload: 00250300"

		reply "9881002001FF,delay 200,9881002502": "command: 9881, payload: 002503FF"
		reply "988100200100,delay 200,9881002502": "command: 9881, payload: 00250300"
	}

	tiles {
		standardTile("switch", "device.switch", width: 2, height: 2, canChangeIcon: true) {
			state "on", label: '${name}', action: "switch.off", icon: "st.switches.switch.on", backgroundColor: "#00a0dc"
			state "off", label: '${name}', action: "switch.on", icon: "st.switches.switch.off", backgroundColor: "#ffffff"
		}
		standardTile("refresh", "device.switch", inactiveLabel: false, decoration: "flat") {
			state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
		}

		main "switch"
		details(["switch","refresh"])
	}
}

def updated() {
	response(refresh())
}

def parse(description) {
	def result = null
	if (description.startsWith("Err 106")) {
		state.sec = 0
		result = createEvent(descriptionText: description, isStateChange: true)
	} else if (description != "updated") {
		def cmd = zwave.parse(description, [0x20: 1, 0x25: 1, 0x70: 1, 0x98: 1])
		if (cmd) {
			result = zwaveEvent(cmd)
			log.debug("'$description' parsed to $result")
		} else {
			log.debug("Couldn't zwave.parse '$description'")
		}
	}
	result
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd) {
	createEvent(name: "switch", value: cmd.value ? "on" : "off", type: "physical")
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicSet cmd) {
	createEvent(name: "switch", value: cmd.value ? "on" : "off", type: "physical")
}

def zwaveEvent(physicalgraph.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
	createEvent(name: "switch", value: cmd.value ? "on" : "off", type: "digital")
}

def zwaveEvent(physicalgraph.zwave.commands.hailv1.Hail cmd) {
	createEvent(name: "hail", value: "hail", descriptionText: "Switch button was pressed", displayed: false)
}

def zwaveEvent(physicalgraph.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
	def encapsulatedCommand = cmd.encapsulatedCommand([0x20: 1, 0x25: 1])
	if (encapsulatedCommand) {
		state.sec = 1
		zwaveEvent(encapsulatedCommand)
	}
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
	log.debug "Unhandled: $cmd"
	null
}

def on() {
	commands([
		zwave.basicV1.basicSet(value: 0xFF),
		zwave.switchBinaryV1.switchBinaryGet()
	])
}

def off() {
	commands([
		zwave.basicV1.basicSet(value: 0x00),
		zwave.switchBinaryV1.switchBinaryGet()
	])
}

def poll() {
	refresh()
}

def refresh() {
	command(zwave.switchBinaryV1.switchBinaryGet())
}

private command(physicalgraph.zwave.Command cmd) {
	if (state.sec != 0) {
		zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
	} else {
		cmd.format()
	}
}

private commands(commands, delay=200) {
	delayBetween(commands.collect{ command(it) }, delay)
}
