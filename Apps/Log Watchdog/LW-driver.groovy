/**
 *  ****************  Log Watchdog Driver  ****************
 *
 *  Design Usage:
 *  This driver opens a webSocket to capture Log info.
 *
 *  Copyright 2019-2020 Bryan Turcotte (@bptworld)
 *  
 *  This App is free.  If you like and use this app, please be sure to mention it on the Hubitat forums!  Thanks.
 *
 *  Remember...I am not a programmer, everything I do takes a lot of time and research (then MORE research)!
 *  Donations are never necessary but always appreciated.  Donations to support development efforts are accepted via: 
 *
 *  Paypal at: https://paypal.me/bptworld
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 * ------------------------------------------------------------------------------------------------------------------------------
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 * ------------------------------------------------------------------------------------------------------------------------------
 *
 *  If modifying this project, please keep the above header intact and add your comments/credits below - Thank you! -  @BPTWorld
 *
 *  App and Driver updates can be found at https://github.com/bptworld/Hubitat
 *
 * ------------------------------------------------------------------------------------------------------------------------------
 *
 *  Special thanks to @dan.t and his sample code for making the websocket connection.
 *
 *  Changes:
 *
 *  1.1.8 - 07/20/20 - Changed up how it connects and reconnects 
 *  1.1.7 - 07/09/20 - Chasing a bug
 *  1.1.6 - 07/09/20 - Error trapping
 *  1.1.5 - 07/04/20 - Code improvements
 *  1.1.4 - 06/29/20 - Learned some new things
 *  1.1.3 - 06/27/20 - Massive overhaul of the logic
 *  1.1.2 - 06/27/20 - Improvements to connection and to the dashboard list
 *  1.1.1 - 06/26/20 - Added code to reconnect the websocket if something goes wrong
 *  1.1.0 - 06/26/20 - Tighting up the code
 *  1.0.9 - 06/24/20 - More changes
 *  1.0.8 - 06/24/20 - Tons of little changes
 *  ---
 *  1.0.0 - 08/31/19 - Initial release
 *
 */

import groovy.json.*

metadata {
	definition (name: "Log Watchdog Driver", namespace: "BPTWorld", author: "Bryan Turcotte", importUrl: "https://raw.githubusercontent.com/bptworld/Hubitat/master/Apps/Log%20Watchdog/LW-driver.groovy") {
   		capability "Actuator"
        capability "Initialize"
       
        command "close"
        command "clearData"
        command "keywordInfo"
        command "appStatus"
        
        attribute "status", "string"
        attribute "bpt-lastLogMessage", "string"       
        attribute "bpt-logData", "string"        
        attribute "numOfCharacters", "number"
        attribute "keywordInfo", "string"
        attribute "appStatus", "string"
    }
    preferences() {    	
        section(){
            input name: "about", type: "paragraph", element: "paragraph", title: "<b>Log Watchdog Driver</b>", description: "ONLY click 'Clear Data' to clear the message data."
            input("disableConnection", "bool", title: "Disable Connection", required: true, defaultValue: false)
            input("fontSize", "text", title: "Font Size", required: true, defaultValue: "15")
			input("hourType", "bool", title: "Time Selection (Off for 24h, On for 12h)", required: false, defaultValue: false)
            input("logEnable", "bool", title: "Enable logging", required: true, defaultValue: false)
            input("traceEnable", "bool", title: "Enable Trace", required: true, defaultValue: false)
        }
    }
}

def installed(){
    log.info "Log Watchdog Driver has been Installed"
    clearData()
    initialize()
}

def updated() {
    log.info "Log Watchdog Driver has been Updated"
    initialize()
}

def initialize() {
    log.info "In initialize"
    if(disableConnection) {
        log.info "Log Watchdog Driver - webSocket Connection is Disabled in the Device"
    } else {
        log.info "Log Watchdog Driver - Connecting webSocket"
        interfaces.webSocket.connect("ws://localhost:8080/logsocket")
    }
}

void uninstalled() {
	interfaces.webSocket.close()
}

def close() {
    interfaces.webSocket.close()
    log.warn "Log Watchdog Driver - Closing webSocket"
}

def webSocketStatus(String socketStatus) {
    if(logEnabled) log.debug "In webSocketStatus - socketStatus: ${socketStatus}"
	if(socketStatus.startsWith("status: open")) {
		log.warn "Log Watchdog Driver - Connected"
        sendEvent(name: "status", value: "connected", displayed: true)
        pauseExecution(500)
        state.delay = null
        return
	} else if(socketStatus.startsWith("status: closing")) {
		log.warn "Log Watchdog Driver - Closing connection"
        sendEvent(name: "status", value: "disconnected")
        return
	} else if(socketStatus.startsWith("failure:")) {
		log.warn "Log Watchdog Driver - Connection has failed with error [${socketStatus}]."
        sendEvent(name: "status", value: "disconnected", displayed: true)
        autoReconnectWebSocket()
	} else {
        log.warn "WebSocket error, reconnecting."
        autoReconnectWebSocket()
	}
}

def autoReconnectWebSocket() {
    state.delay = (state.delay ?: 0) + 30    
    if(state.delay > 600) state.delay = 600

    log.warn "Log Watchdog Driver - Connection lost, will try to reconnect in ${state.delay} seconds"
    runIn(state.delay, initialize)
}

def keywordInfo(keys) {
    if(traceEnable) log.trace "In keywordInfo"
    
    def (keySet,keySetType,keyword1,sKeyword1,sKeyword2,sKeyword3,sKeyword4,nKeyword1,nKeyword2) = keys.split(";")
    
    state.keyValue = "${keySetType};${keyword1};${sKeyword1};${sKeyword2};${sKeyword3};${sKeyword4};${nKeyword1};${nKeyword2}"

    if(traceEnable) log.trace "In keywordInfo - Recieved ${keySet}"
    if(traceEnable) log.trace "In keywordInfo - keyValue: ${state.keyValue}"
}

def parse(String description) {
    def aStatus = device.currentValue('appStatus')
    if(aStatus == "active") {
        theData = "${description}"
        // This is what the incoming data looks like
        //{"name":"Log Watchdog","msg":"Log Watchdog Driver - Connected","id":365,"time":"2019-11-24 10:05:07.518","type":"dev","level":"warn"}

        def message =  new JsonSlurper().parseText(theData)
        
        // name, msg, id, time, type, level
        
        msgValue = message.msg
        nameValue = message.name
        msgCheck = msgValue.toLowerCase()
        state.msgV = msgValue

        try {
            lvlValue = message.level
            lvlCheck = lvlValue.toLowerCase()
        } catch (e) {
            lvlCheck = "-----"
        }

        if(state.keyValue) {
            def keyValue = state.keyValue.toLowerCase()
            def (keySetType,keyword1,sKeyword1,sKeyword2,sKeyword3,sKeyword4,nKeyword1,nKeyword2) = keyValue.split(";")
        }
        if(keyword1 == "-") keyword1 = ""
        if(sKeyword1 == "-") sKeyword1 = ""
        if(sKeyword2 == "-") sKeyword2 = ""
        if(sKeyword3 == "-") sKeyword3 = ""
        if(sKeyword4 == "-") sKeyword4 = ""
        if(nKeyword1 == "-") nKeyword1 = ""
        if(nKeyword2 == "-") nKeyword2 = ""
        
        if(keyword1) {
            String keyword = keyword1.replace("[","").replace("]","")
            if(logEnable) log.debug "msgCheck: ${msgCheck} - keyword: ${keyword}"

            state.kCheck1 = false
            state.kCheck2 = true
            state.match = false

            try {
                readyToGo = false
                if(keySetType == "l") {
                    if(lvlCheck.contains("${keyword}")) {
                        if(traceEnable) {
                            keyword1a = keyword.replace("a","@").replace("e","3").replace("i","1").replace("o","0",).replace("u","^")
                            log.trace "In keyword - Found msgCheck: ${keyword1a}"
                        }
                        readyToGo = true
                    }
                }


                if(keySetType == "k") {
                    if(msgCheck.contains("${keyword}")) {
                        if(traceEnable) {
                            keyword1a = keyword.replace("a","@").replace("e","3").replace("i","1").replace("o","0",).replace("u","^")
                            log.trace "In keyword - Found msgCheck: ${keyword1a}"
                        }
                        readyToGo = true
                    }
                }

                if(readyToGo) {
                    if(sKeyword1 || sKeyword2 || sKeyword3 || sKeyword4) {
                        if(msgCheck.contains("${sKeyword1}")) {
                            if(traceEnable) log.trace "In Secondary Keyword1: ${sKeyword1} Found! That's GOOD!"
                            state.kCheck1 = true
                        } else if(msgCheck.contains("${sKeyword2}")) {
                            if(traceEnable) log.trace "In Secondary Keyword2: ${sKeyword2} Found! That's GOOD!"
                            state.kCheck1 = true
                        } else if(msgCheck.contains("${sKeyword3}")) {
                            if(traceEnable) log.trace "In Secondary Keyword1: ${sKeyword3} Found! That's GOOD!"
                            state.kCheck1 = true
                        } else if(msgCheck.contains("${sKeyword4}")) {
                            if(traceEnable) log.trace "In Secondary Keyword4: ${sKeyword4} Found! That's GOOD!"
                            state.kCheck1 = true
                        }       
                    } else {
                        state.kCheck1 = true
                    }

                    if(nKeyword1 || nKeyword2) {  
                        if(msgCheck.contains("${nKeyword1}")) {
                            if(traceEnable) log.trace "In Not Keyword1: ${nKeyword1} found! That's BAD!"
                            state.kCheck2 = false
                        } else if(msgCheck.contains("${nKeyword2}")) {
                            if(traceEnable) log.trace "In Not Keyword2: ${nKeyword2} found! That's BAD!"
                            state.kCheck2 = false
                        }
                    }

                    if(traceEnable) log.trace "In keyword: ${keyword1a} - kCheck1: ${state.kCheck1} - kCheck2: ${state.kCheck2}"
                    if(state.kCheck1 && state.kCheck2) {
                        state.match = true
                    } else {
                        state.match = false
                    }
                }

                if(state.match) {
                    if(traceEnable) log.warn "In keyword: ${keyword1a} - Everything is GOOD!"
                    if(traceEnable) log.warn "In keyword: Sending: ${state.msgV}"
                    makeList(nameValue, state.msgV)
                    state.msgV = null
                }
            } catch (e) {
                if(traceEnable) log.trace "In parse - Error to follow!"
                log.error e
            }
        }
    }
}
 
def makeList(nameValue,msgValue) {
    if(traceEnable) log.trace "In makeList - working on - nameValue: ${nameValue} - ${msgValue}"

    try {
        if(state.list == null) state.list = []

        getDateTime()
        last = "${nameValue}::${newDate}::${msgValue}"
        state.list.add(0,last)  

        if(traceEnable) log.trace "In makeList - added to list - last: ${last}"
        
        if(state.list) {
            listSize1 = state.list.size()
        } else {
            listSize1 = 0
        }

        if(traceEnable) log.trace "In makeList - listSize1: ${listSize1}"
        
        int intNumOfLines = 10
        if (listSize1 > intNumOfLines) state.list.removeAt(intNumOfLines)
        
        if(traceEnable) log.trace "In makeList - Passed Check 1"
        
        String result1 = state.list.join(",")
        def lines = result1.split(",")

        theData = "<div style='overflow:auto;height:90%'><table style='text-align:left;font-size:${fontSize}px'><tr><td width=20%><td width=1%><td width=10%><td width=1%><td width=68%>"

        if(traceEnable) log.trace "In makeList - Passed Check 2"
        
        for (i=0;i<intNumOfLines && i<listSize1;i++) {
            combined = theData.length() + lines[i].length() + 16
            if(combined < 1000) {
                def (theApp, theTime, theMsg) = lines[i].split("::") 
                theData += "<tr><td>${theApp} <td> - <td>${theTime}<td> - <td>${theMsg}"
            }
        }

        if(traceEnable) log.trace "In makeList - Passed Check 3"
        
        theData += "</table></div>"
        if(logEnable) log.debug "theData - ${theData.replace("<","!")}"       

        dataCharCount1 = theData.length()
        if(dataCharCount1 <= 1024) {
            if(logEnable) log.debug "Log Watchdog Attribute - theData - ${dataCharCount1} Characters"
        } else {
            theData = "Log Watchdog - Too many characters to display on Dashboard (${dataCharCount1})"
        }

        if(traceEnable) log.trace "In makeList - Passed Check 4"
        
        sendEvent(name: "bpt-logData", value: theData, displayed: true)
        sendEvent(name: "numOfCharacters", value: dataCharCount1, displayed: true)
        sendEvent(name: "bpt-lastLogMessage", value: msgValue, displayed: true)
    }
    catch(e) {
        log.error "Log Watchdog Driver - In makeList - Error to follow!"
        log.error e  
    }
}

def appStatus(data){
	if(logEnable) log.debug "Log Watchdog Driver - In appStatus"
    sendEvent(name: "appStatus", value: data, displayed: true)
}

def clearData(){
	if(logEnable) log.debug "Log Watchdog Driver - Clearing the data"
    msgValue = "-"
    logCharCount = "0"
    
    state.list = []
    sendEvent(name: "bpt-logData", value: state.list, displayed: true)
	
    sendEvent(name: "bpt-lastLogMessage", value: msgValue, displayed: true)
    sendEvent(name: "numOfCharacters", value: logCharCount, displayed: true)
}

def getDateTime() {
	def date = new Date()
	if(hourType == false) newDate=date.format("MM-d HH:mm")
	if(hourType == true) newDate=date.format("MM-d hh:mm")
    return newDate
}
