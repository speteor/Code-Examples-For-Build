/**
 *  TV Time + IR
 *
 *  Author: Zach Naimon @ Smart Things
 *
 *  Date: 2013-06-18
 */
preferences {
	section("Please set a main allowable time window") {
		input "startTime1", "time", title: "Start of main time window"
		input "endTime1", "time", title: "End of main time window"
	}
	section("Please set a secondary allowable time window (optional)") {
		input "startTime2", "time", title: "Start of secondary time window", required: false
		input "endTime2", "time", title: "End of secondary time window", required: false
	}
	section("Maximum TV Time"){
		input "maxTimeWk", "decimal", title: "Weekdays (hrs)"
		input "maxTimeWe", "decimal", title: "Weekends (hrs)"
	}
	section("TV(s)"){
		input "tv", "device.zwaveMeteringSwitch", title: "Select your television(s)"
		input "threshold", "decimal", title: "Voltage Threshold for TV" 
	}
	section("Contacting You"){
		input "phone", "phone", title: "Enter a phone number (optional)", required: false
	}
    section("IR Bridge(s)")
    {
    	input "ir","device.IrBridge",title:"Select your IR Receiver (s)"
    }
}

def installed() {
	resetTimer()
	initialize()
}

def updated() {
	unsubscribe()
	unschedule()
	initialize()
}

def initialize() {
	subscribe(tv, "power", tvHandler)
	subscribe(app, appTouch)
    subscribe(ir,"button.B", handleB)

	schedule("30 * * * * ?", runJob)
	def midnight = timeTodayAfter(startTime1, "00:00", timeZone)
	log.trace "$midnight"
	runDaily(midnight, newDay)

	runJob()
}

def runJob() {
	log.trace elapsedTime()
	log.trace timeAllowance()
	def debug = ", switch: ${tv.currentSwitch}, power: ${tv.currentPower}, elapsed: ${elapsedTime()/60000}, allowance: ${timeAllowance()/60000}"
	if (inTimeWindow()) {
		if (withinTimeAllowance()) {
			if (tv.currentSwitch != "on") {
				
				tv.on()
			}
			else {
				log.trace "Leaving TV on, within time window and allowance $debug"
			}
		}
		else {
			if (tv.currentSwitch == "on") {
            	def timeAllowHrs = timeAllowance() / 3600000
                log.debug "in Hours, Allowance Time is $timeAllowHrs"
				log.info "Turning TV off, exceeded allowable hours for the day ($timeAllowHrs hours) $debug"
				sendPush("Turning TV off, exceeded allowable hours for the day ($timeAllowHrs hours)")
                if (phone) {
					sendSms(phone, "Turning TV off, exceeded allowable hours for the day ($timeAllowHrs hours)")
				}
                tv.off()
                
			}
			else {
				log.trace "Turning TV off, exceeded allowable hours for the day (${timeAllowance()} hours) $debug"
			}
		}
	}
	else {
		if (tv.currentSwitch == "on") {
			log.info "Turning TV off, outside of allowable time window $debug"
			tv.off()
		}
		else {
			log.trace "Leaving TV off, outside of allowable time window $debug"
		}
	}
	tv.poll()
}

/** def appTouch(evt) {
	log.trace "appTouch"
	runJob()
} */

def newDay() {
	log.trace "newDay, power: ${tv.currentPower}"
	resetTimer()
	if (tv.currentPower > 0) {
		startTimer()
	}
}

def tvHandler(evt) {
	log.trace "tvHandler($evt.name: $evt.value)"
	if (evt.numericValue > threshold) {
		startTimer()
		sendPush("TV has been turned on")
	}
	else {
		stopTimer()
		def remTimeMS = timeAllowance() - elapsedTime()
		log.debug "remTime in MS is $remTimeMS"
		def remTime = remTimeMS / 3600000
        remTime = Math.round(remTime * 100) / 100
		log.debug "remTime in Hours is $remTime"
		sendPush("TV has been turned off.  You have $remTime hours remaining")
	}
}
def appTouch(evt) {
	
    runJob()
}
private getTimeZone() {
	location.timeZone ?: timeZone(startTime1)
}

private inTimeWindow() {
	//log.trace "'$startTime2'"
	//log.trace "'$endTime2'"
	def now = now()
	def tz = timeZone
	def window1 = now >= timeToday(startTime1, tz).time && now <= timeTodayAfter(startTime1, endTime1, tz).time
	def window2 = startTime2?.size()>24 && endTime2?.size()>24 ? now >= timeToday(startTime2, tz).time && now <= timeTodayAfter(startTime2, endTime2, tz).time : false
	def result = window1 || window2
	result
}

private withinTimeAllowance() {
	def result = elapsedTime() < timeAllowance()
	result
}


private timeAllowance() {
	def result
	def calendar = Calendar.getInstance(location.timeZone ?: timeZone(startTime1))
	if (calendar.get(Calendar.DAY_OF_WEEK) in [Calendar.SATURDAY, Calendar.SUNDAY]) {
		return maxTimeWe * 3600000
	}
	else {
		return maxTimeWk * 3600000
	}
	result
}

private void startTimer() {
	state.startedAt = now()
}

private void stopTimer() {
	if (state.startedAt) {
		state.totalTime = state.totalTime + now() - state.startedAt
	}
	state.startedAt = null
}

private void resetTimer() {
	state.totalTime = 0
	state.startedAt = null
}

private elapsedTime() {
	def t = state.totalTime
	if (state.startedAt) {
		t += now() - state.startedAt
	}
	return t
}


def handleB(evt) {
	log.debug "received button B"
	//if over time, send push notification
    
    if (!inTimeWindow())
    {
    	log.info "outside viewing window"
    	sendPush("Outside of allowable TV viewing time window")
        if (phone) {
            sendSms(phone, "Outside of allowable TV viewing time window")
        }           
    }
    else if(!withinTimeAllowance()) 
    {
        log.info "over allowed time"
        sendPush("Over allowable TV hours for the day")
        if (phone) {
            sendSms(phone, "Over allowable TV hours for the day")
        }        
    }

}