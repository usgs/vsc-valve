// $Id: time.js,v 1.3 2006/04/09 21:54:14 dcervelli Exp $

/** @fileoverview deals with Start Time and End Time entry fields 
 * @author Dan Cervelli
 */

/**
 *  grab the start and end times from the entry fields, and do some checking on them
 *  Make sure start time is before end time, make sure that difference in time is 
 *  greater than parameter maxDiff. return start time and end time variables in 'this'
 *
 *  @param {integer} maxDiff Maximum difference in seconds allowed.
 *  @return object with et and st components: start time and end time
 *  @type object
 */
function getTimes(maxDiff)
{
	this.et = getEndTime();
	this.st = getStartTime(this.et);
	
	if (this.st == null || this.et == null)
		return null;

	if (timeDiff(this.st, this.et) > maxDiff)
	{
		alert("Maximum amount of time select exceeded.");
		return null;
	}
				
	var ste = "" + this.et;
	if (ste.toUpperCase() == "N")
		return this;
	if (this.st < this.et)
		return this;

	alert("Start time must be before end time.");
	
	return null;
}

/**
 *  grab the start time form field from the document.
 *  Do checks and conversions if this is a "shortcut" time like -1d (the previous 
 *  one day) or -1h *  (previous hour) -10m (previous 10 min) or whatever.
 *  
 *  Confirm that it's a valid date/time, and then return it.
 *
 *  @return the start time as an integer
 *  @type int
 */
function getStartTime()
{
	var st = document.getElementById('startTime'); 
	st.value = st.value.replace(/[\[\]\'\"]/g, "");
	var d = st.value;
	var errMsg = "Error on start time.";
	if (d.charAt(0) == '-')
	{
		var c = d.charAt(d.length - 1);
		var q = d.substring(1, d.length - 1);
		var re = new RegExp("[^0123456789]");
		if (re.test(q))
		{
			alert(errMsg);
			return null;
		}
		switch(c)
		{
			case 'i':
				return q * -1 * 60000;
			case 'h':
				return q * -60 * 60000;
			case 'd':
				return q * -24 * 60 * 60000;
			case 'w':
				return q * -24 * 60 * 7 * 60000;
			case 'm':
				return q * -24 * 60 * 30 * 60000;
			case 'y':
				return q * -24 * 60 * 365 * 60000;
		}
		alert(errMsg);
		return null;
	}
	else
	{
		if (!validateDate(st, true))
		{
			alert(errMsg);
			return null;
		}
		return st.value;
	}
}

/**
 *  grab the end time form field from the document.
 *  
 *  Confirm that it's a valid date/time, or that it's the value NOW, and then return it.
 *
 *  @return the end time as an integer
 *  @type int
 */
function getEndTime()
{
	var et = document.getElementById('endTime');
	et.value = et.value.replace(/[\[\]\'\"]/g, "");
	if (!validateDate(et, false))
	{
		alert("Error on end time.");
		et.focus();
		et.select();
		return null;
	}
	if (et.value.toUpperCase() == "NOW")
		return "N";
	else
		return et.value;
}

/**
 *  Looks at a text field data like 20090331125600000 or -5d or -1h or -1m or Now
 *  and checks each element of the number to make sure it's a valid date and 
 *  time. (Or if it's -5d or -1h or whatever, make sure it's a valid time shortcut
 *  value.)
 *
 * @param {string} textField for example a date like 20090331125600000
 * @param {boolean} start boolean to see if we're working on start or end date
 * @returns a boolean reporting if this the data passed to it is valid or not
 * @type boolean
 */
function validateDate(textField, start)
{
	var val = textField.value;
	if (start)
	{
		var re = new RegExp("[^ -0123456789]");
		if (re.test(val))
			return false;
	}
	else
	{
		if (val.toUpperCase() == "NOW")
			return true;
		
		var re = new RegExp("[^ -0123456789]");
		if (re.test(val))
			return false;
	}
	// check for -[NumMinutes], undocumented but allowed.
	if (start)
	{
		if (val.indexOf("-") == 0)
		{
			// - must be first and only digit
			if (val.lastIndexOf("-") != 0)
				return false;
				
			return true;
		}
	}
 
	// [YYYY], [YYYYMMDD], [YYYYMMDDHHmm] only  FIX
	if (val.length == 4)
	{
		if (start)
			val = val + "0101000000000";
		else
			val = val + "1231235959999";
	}	

	if (val.length == 8)
	{
		if (start)
			val = val + "000000000";
		else
			val = val + "235959999";
	}

	if (val.length == 12)
	{
		if (start)
			val = val + "00000";
		else
			val = val + "59999";
	}

	if (val.length == 14)
	{
		if (start)
			val = val + "000";
		else
			val = val + "599";
	}

	if (val.length == 15)
		val = val + "00";

	if (val.length == 16)
		val = val + "0";

	if (val.length == 17)
	{
		var year = val.substring(0, 4);
		var month = val.substring(4, 6);
		if (month < 1 || month > 12)
			return false;
		var day = val.substring(6, 8);
		if (day < 1 || day > 31)
			return false;
		var hour = val.substring(8, 10);
		if (hour < 0 || hour > 23)
			return false;
		var minute = val.substring(10, 12);
		if (minute < 0 || minute > 59)
			return false;
				var second = val.substring(12, 14);
		if (second < 0 || second > 59)
			return false;
				var ms = val.substring(14, 17);
		if (ms < 0 || ms > 999)
			return false;

		textField.value = val;
		return true;				
	}
	
	return false;
}	
	
/**
 *  Build a time string for a new time generated for example by a click on the middle of an image where
 *  the X axis is time. tIn is a second-based numerical time like 291737638.7858823 which is in Dan's epoch 
 *  that starts  at 2000 01 01 12:00 0.00 which is 946728000 in unix time. (See that number being added below...)
 *  
 *  This function then uses the built in time functions with an epoch of 1970 01 01 00:00 0.00 to separate 
 *  out each time element and create the string to drop back in the start or end time entry form html fields, 
 *  for example: 20090331012753845 for 2009 Mar 31, 1:27am...
 *  
 * @param {float} tIn second based numerical time in Dan's year 2000 based epoch
 * @returns date text string for entry field
 * @type string
 */
function buildTimeString(tIn)
{
	//var t = Math.round(1000*tIn) + 946728000000;// - (document.getElementById('timeZoneOffset') * 60 * 60 * 1000);//+ 36000000;	
	var t = Math.round(1000*tIn) + 946728000000 + (document.getElementById('timeZoneOffset').value * 60 * 60 * 1000);//+ 36000000;	
	
	var time = new Date(t);
	var mo = (time.getUTCMonth() + 1 < 10 ? "0" + (time.getUTCMonth() + 1) : (time.getUTCMonth() + 1));
	var da = (time.getUTCDate() < 10 ? "0" + time.getUTCDate() : time.getUTCDate());
	var hr = (time.getUTCHours() < 10 ? "0" + time.getUTCHours() : time.getUTCHours());
	var mi = (time.getUTCMinutes() < 10 ? "0" + time.getUTCMinutes() : time.getUTCMinutes());
	var sc = (time.getUTCSeconds() < 10 ? "0" + time.getUTCSeconds() : time.getUTCSeconds());
	var ms = time.getUTCMilliseconds();
	if (ms < 10)
		ms = "00" + ms;
	else if (ms < 100)
		ms = "0" + ms;
	return "" + time.getUTCFullYear() + mo + da + hr + mi + sc + ms;
}

/**
 *  Take a time string and create 
 *  and return a new standard Javascript date object for it.
 *
 * @param {string} tIn time string in the format of 20090331012753845 for 2009 Mar 31, 1:27am
 * @returns date object
 * @type date object
 */
function parseTimeString(tIn)
{
	var t = new Date(tIn.substring(0, 4), tIn.substring(4, 6) - 1, tIn.substring(6, 8), 
			tIn.substring(8, 10), tIn.substring(10, 12), tIn.substring(12, 14), tIn.substring(14, 17));
	return t;
}

/**
 *	Take two time strings, compare them and return the number of seconds difference between the two
 * 
 * @param {string} ts start time
 * @param {string} te end time
 * @returns seconds difference between params
 * @type int
 */
function timeDiff(ts, te)
{
	if (ts < 0)
		return -ts;
	else
	{
		var t1 = parseTimeString(ts);
		var t2 = parseTimeString(te);
		return t2.getTime() - t1.getTime();
	}
}