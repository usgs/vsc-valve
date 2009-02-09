// $Id: time.js,v 1.3 2006-04-09 21:54:14 dcervelli Exp $

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
	
function buildTimeString(tIn)
{
	// this is where the time gets converted from local time to GMT time.  All within the system of processing, times are GMT.
	// it is only when the plot is returned that the x-axis displays the time as local time again.
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

function parseTimeString(tIn)
{
	var t = new Date(tIn.substring(0, 4), tIn.substring(4, 6) - 1, tIn.substring(6, 8), 
			tIn.substring(8, 10), tIn.substring(10, 12), tIn.substring(12, 14), tIn.substring(14, 17));
	return t;
}

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