/**
 *  Xiaomi Aqara Motion Sensor
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
 *  Based on original DH by Eric Maycock 2015
 *
 *  Change log:
 *  modified 29/12/2016 a4refillpad
 *  Added fingerprinting
 *  Added heartbeat/lastcheckin for monitoring
 *  Added battery and refresh
 *  Motion background colours consistent with latest DH
 *  Fixed max battery percentage to be 100%
 *  Added Last update to main tile
 *  Added last motion tile
 *  Heartdeat icon plus improved localisation of date
 *  Removed non working tiles and changed layout and incorporated latest colours
 *  Added experimental health check as worked out by rolled54.Why
 *  bspranger - renamed to bspranger to remove confusion of a4refillpad
 */

metadata {
    definition (name: "Xiaomi Aqara Motion Sensor", namespace: "bspranger", author: "bspranger") {
        capability "Motion Sensor"
        capability "Illuminance Measurement"
        capability "Configuration"
        capability "Battery"
        capability "Sensor"
        capability "Refresh"
        capability "Health Check"

        attribute "lastCheckin", "String"
        attribute "lastMotion", "String"
        attribute "light", "number"
        attribute "batteryRuntime", "String"

        fingerprint profileId: "0104", deviceId: "0104", inClusters: "0000, 0003, FFFF, 0019", outClusters: "0000, 0004, 0003, 0006, 0008, 0005, 0019", manufacturer: "LUMI", model: "lumi.sensor_motion", deviceJoinName: "Xiaomi Motion"
        fingerprint endpointId: "01", profileId: "0104", deviceId: "0107", inClusters: "0000,FFFF,0406,0400,0500,0001,0003", outClusters: "0000,0019", manufacturer: "LUMI", model: "lumi.sensor_motion.aq2", deviceJoinName: "Xiaomi Aqara Motion Sensor"

        command "resetBatteryRuntime"
        command "reset"
        command "Refresh"
    }

    simulator {
    }

    preferences {
        input "motionReset", "number", title: "Number of seconds after the last reported activity to report that motion is inactive (in seconds). \n\n(The device will always remain blind to motion for 60seconds following first detected motion. This value just clears the 'active' status after the number of seconds you set here but the device will still remain blind for 60seconds in normal operation.)", description: "", value:120, displayDuringSetup: false
    }

    tiles(scale: 2) {
        multiAttributeTile(name:"motion", type: "generic", width: 6, height: 4) {
            tileAttribute ("device.motion", key: "PRIMARY_CONTROL") {
                attributeState "active", label:'motion', icon:"st.motion.motion.active", backgroundColor:"#00a0dc"
                attributeState "inactive", label:'no motion', icon:"st.motion.motion.inactive", backgroundColor:"#ffffff"
            }
            tileAttribute("device.lastMotion", key: "SECONDARY_CONTROL") {
                attributeState("default", label:'Last Motion: ${currentValue}')
            }
        }
        valueTile("battery", "device.battery", decoration: "flat", inactiveLabel: false, width: 2, height: 2) {
            state "default", label:'${currentValue}%', unit:"",
            backgroundColors: [
                [value: 10, color: "#bc2323"],
                [value: 26, color: "#f1d801"],
                [value: 51, color: "#44b621"]
            ]
        }
        valueTile("light", "device.Light", decoration: "flat", inactiveLabel: false, width: 2, height: 2) {
            state "light", label:'Light\n${currentValue}\nlux', unit: ""
        }
        standardTile("reset", "device.reset", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
            state "default", action:"reset", label: "Reset Motion", icon:"st.motion.motion.active"
        }
        valueTile("lastcheckin", "device.lastCheckin", decoration: "flat", inactiveLabel: false, width: 5, height: 1) {
            state "default", label:'Last Update:\n ${currentValue}'
        }
        standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width: 1, height: 1) {
            state "default", action:"refresh.refresh", icon:"st.secondary.refresh"
        }
        standardTile("refresh", "command.refresh", inactiveLabel: false) {
            state "default", label:'refresh', action:"refresh.refresh", icon:"st.secondary.refresh-icon"
        }
		standardTile("batteryRuntime", "device.batteryRuntime", inactiveLabel: false, decoration: "flat", width: 6, height: 2) {
			state "batteryRuntime", label:'Battery Changed: ${currentValue} - Tap To Reset Date', unit:"", action:"resetBatteryRuntime"
		}

        main(["motion"])
        details(["motion", "battery", "light", "reset", "lastcheckin", "refresh"])
    }
}

def parse(String description) {
    def linkText = getLinkText(device)
    log.debug "${linkText} Parsing: $description"

    Map map = [:]
    if (description?.startsWith('catchall:')) {
        map = parseCatchAllMessage(description)
    }
    else if (description?.startsWith('read attr -')) {
        map = parseReportAttributeMessage(description)
    }
    else if (description?.startsWith('illuminance:')) {
        map = parseIlluminanceMessage(description)
    }

    log.debug "${linkText} Parse returned: $map"
    def result = map ? createEvent(map) : null
    // send event for heartbeat
    def now = new Date().format("yyyy MMM dd EEE h:mm:ss a", location.timeZone)
    sendEvent(name: "lastCheckin", value: now)

    if (description?.startsWith('enroll request')) {
        List cmds = enrollResponse()
        log.debug "${linkText} enroll response: ${cmds}"
        result = cmds?.collect { new physicalgraph.device.HubAction(it) }
    }
    return result
}

private Map parseIlluminanceMessage(String description) {
    def linkText = getLinkText(device)
    def result = [
        name: 'Light',
        value: '--'
    ]
    def value = ((description - "illuminance: ").trim()) as Float
    result.value = value
    result.descriptionText = "${linkText} Light was ${result.value}"
    return result;
}


private Map getBatteryResult(rawValue) {
    def rawVolts = rawValue / 1000

    def minVolts = 2.7
    def maxVolts = 3.3
    def pct = (rawVolts - minVolts) / (maxVolts - minVolts)
    def roundedPct = Math.min(100, Math.round(pct * 100))

    def result = [
        name: 'battery',
        value: roundedPct,
        unit: "%",
        isStateChange:true,
        descriptionText : "${device.displayName} raw battery is ${rawVolts}v"
    ]
    
    log.debug "${device.displayName}: ${result}"
    if (state.battery != result.value)
    {
    	state.battery = result.value
        resetBatteryRuntime()
    }
    return result
}

private Map parseCatchAllMessage(String description) {
    def linkText = getLinkText(device)

    Map resultMap = [:]
    def cluster = zigbee.parse(description)
    def i
    log.debug cluster
    if (shouldProcessMessage(cluster)) {
        switch(cluster.clusterId) {
            case 0x0000:
            	def MsgLength = cluster.data.size();
                for (i = 0; i < (MsgLength-3); i++)
                {
                    if ((cluster.data.get(i) == 0x01) && (cluster.data.get(i+1) == 0x21))  // check the data ID and data type
                    {
                        // next two bytes are the battery voltage.
                        resultMap = getBatteryResult((cluster.data.get(i+3)<<8) + cluster.data.get(i+2))
                    }
                }
            	break
        }
    }
    return resultMap
}

private boolean shouldProcessMessage(cluster) {
    // 0x0B is default response indicating message got through
    // 0x07 is bind message
    boolean ignoredMessage = cluster.profileId != 0x0104 ||
    cluster.command == 0x0B ||
    cluster.command == 0x07 ||
    (cluster.data.size() > 0 && cluster.data.first() == 0x3e)
    return !ignoredMessage
}


def configure() {
	state.battery = 0
    log.debug "${device.displayName}: configuring"
    return zigbee.readAttribute(0x0001, 0x0020) + zigbee.configureReporting(0x0001, 0x0020, 0x21, 600, 21600, 0x01)
}

def refresh(){
    log.debug "${device.displayName}: refreshing"
    return zigbee.readAttribute(0x0001, 0x0020) + zigbee.configureReporting(0x0001, 0x0020, 0x21, 600, 21600, 0x01)
}

def enrollResponse() {
    def linkText = getLinkText(device)
    log.debug "${linkText}: Enrolling device into the IAS Zone"
    [
        // Enrolling device into the IAS Zone
        "raw 0x500 {01 23 00 00 00}", "delay 200",
        "send 0x${device.deviceNetworkId} 1 1"
    ]
}

private Map parseReportAttributeMessage(String description) {
    Map descMap = (description - "read attr - ").split(",").inject([:]) { map, param ->
        def nameAndValue = param.split(":")
        map += [(nameAndValue[0].trim()):nameAndValue[1].trim()]
    }
    //log.debug "Desc Map: $descMap"

    Map resultMap = [:]
    def now = new Date().format("yyyy MMM dd EEE h:mm:ss a", location.timeZone)

    if (descMap.cluster == "0001" && descMap.attrId == "0020") {
        resultMap = getBatteryResult(Integer.parseInt(descMap.value, 16))
    }
    else if (descMap.cluster == "0406" && descMap.attrId == "0000") {
        def value = descMap.value.endsWith("01") ? "active" : "inactive"
        sendEvent(name: "lastMotion", value: now)
        if (settings.motionReset == null || settings.motionReset == "" ) settings.motionReset = 120
        if (value == "active") runIn(settings.motionReset, stopMotion)
        resultMap = getMotionResult(value)
    }
    return resultMap
}

private Map parseCustomMessage(String description) {
    Map resultMap = [:]
    return resultMap
}

private Map parseIasMessage(String description) {
    def linkText = getLinkText(device)
    List parsedMsg = description.split(' ')
    String msgCode = parsedMsg[2]

    Map resultMap = [:]
    switch(msgCode) {
        case '0x0020': // Closed/No Motion/Dry
            resultMap = getMotionResult('inactive')
            break

        case '0x0021': // Open/Motion/Wet
            resultMap = getMotionResult('active')
            break

        case '0x0022': // Tamper Alarm
            log.debug '${linkText}: motion with tamper alarm'
            resultMap = getMotionResult('active')
            break

        case '0x0023': // Battery Alarm
            break

        case '0x0024': // Supervision Report
            log.debug '${linkText}: no motion with tamper alarm'
            resultMap = getMotionResult('inactive')
            break

        case '0x0025': // Restore Report
            break

        case '0x0026': // Trouble/Failure
            log.debug '${linkText}: motion with failure alarm'
            resultMap = getMotionResult('active')
            break

        case '0x0028': // Test Mode
            break
    }
    return resultMap
}


private Map getMotionResult(value) {
    def linkText = getLinkText(device)
    // log.debug "${linkText}: motion"
    String descriptionText = value == 'active' ? "${linkText} detected motion" : "${linkText} motion has stopped"
    def commands = [
        name: 'motion',
        value: value,
        descriptionText: descriptionText
    ]
    return commands
}

private byte[] reverseArray(byte[] array) {
    byte tmp;
    tmp = array[1];
    array[1] = array[0];
    array[0] = tmp;
    return array
}

private String swapEndianHex(String hex) {
    reverseArray(hex.decodeHex()).encodeHex()
}

def stopMotion() {
    sendEvent(name:"motion", value:"inactive")
}

def reset() {
    sendEvent(name:"motion", value:"inactive")
}

def resetBatteryRuntime() {
   	def now = new Date().format("EEE dd MMM yyyy h:mm:ss a", location.timeZone)
    sendEvent(name: "batteryRuntime", value: now)
}

def installed() {
    checkIntervalEvent("installed");
}

def updated() {
    checkIntervalEvent("updated");
}

private checkIntervalEvent(text) {
    // Device wakes up every 1 hours, this interval allows us to miss one wakeup notification before marking offline
    def linkText = getLinkText(device)
    log.debug "${linkText}: Configured health checkInterval when ${text}()"
    sendEvent(name: "checkInterval", value: 2 * 60 * 60 + 2 * 60, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID])
}
