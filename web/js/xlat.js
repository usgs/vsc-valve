// $Id: xlat.js,v 1.4 2007/01/30 21:53:32 dcervelli Exp $

/** @fileoverview functions for translating x and y mouse coordinates to their corresponding
 * values on the element which they're hovering over.
 * @author Dan Cervelli
 */

var lastTimeClick = 0;

/** 
 * Convert a time variable (t) to a time string in the form "2009-03-27 11:45:23.003"
 *
 * @param {float} t year 2000 epoch based time number
 * @returns time string in the form "2009-03-27 11:45:23.003"
 * @type string
 */
function timeToString(t)
{
	t = (1000 * (t + 946728000));
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

	var ds = time.getUTCFullYear() + "-" + mo + "-" + da + " " + hr + ":" + mi + ":" + sc + "." + ms;	
	return ds;
}

/**
 *  Takes an event and the mouse position, and translates the positions to the relevant
 *  values in the image or plot. For example: x/y becomes time/counts.
 * 
 * @param {object} event event object such as mouseover
 */
function translate_ty(event)
{
	var ev = getEvent(event);
	var mxy = getMouseXY(ev);
	var gxy = getTranslation_ty(event);
	var target = getTarget(ev);
	currentMenu.acceptTYClick(target, mxy.x, mxy.y, gxy[0], gxy[1]);
}

function translateT2X( t, targ )
{
	var xy = getTopLeftOffset(targ);
	var tt = targ.translation;
	return (t - tt[1]*1.0)/(tt[0]*1.0);
}

/**
 *  Get event and target element x/y coordinates. For example mouse coordinates
 *  and png image coordinates which mouse is rolling over. Ty can mean latitude
 *  but doesn't necessarily in this case.
 *  
 *  Returns an array with two numeral based on the 'translation' property of the 
 *  target, as well as a time string. The numerals are related to x/y. In this case
 *  these are translated to the vertical counts scale, and the time is converted to 
 *  where you are on the timeline, left/right. This updates continuously.
 *
 *  @param {object} event event object such as mouseover
 *  @returns an array with x, y and time and calculated Y
 *  @type array
 */
function getTranslation_ty(event)
{
	var ev = getEvent(event);
	var target = getTarget(ev);
	var xy = getElementXY(ev, target);
	var t = target.translation;

	var sx = xy[0];
	var sy = xy[1];
	sy = target.height - sy;
	gx = sx * t[0] + t[1] * 1;
	gy = sy * t[2] + t[3] * 1;
	var result = new Array(3);
	result[0] = gx;
	result[1] = gy;
	var ry = parseInt(gy * 1000) / 1000;
	result[2] = timeToString(gx) + ", Y: " + ry;
	return result;
}

/**
 *  Get the event, get the mouse coordinates, get the translation coordinates for
 *  the target, latitude or time or whatever they may be.
 *
 *  @param {object} event event object such as mouseover
 */
function translate_xy(event)
{
	var ev = getEvent(event);
	var mxy = getMouseXY(ev);
	var gxy = getTranslation_xy(event);
	var target = getTarget(ev);
	
	currentMenu.acceptXYClick(target, mxy.x, mxy.y, gxy[0], gxy[1]);
}

/**
 *  Get the translation that's appropriate for a particular event, and pair it
 *  with the non-translated X Y with any decimal places dropped off.
 *
 *  @param {object} event event object such as mouseover
 */
function getTranslation_xy(event)
{
	var gxy = getTranslation_ty(event);
	var rx = parseInt(gxy[0] * 1000) / 1000;
	var ry = parseInt(gxy[1] * 1000) / 1000;
	gxy[2] = "X: " + rx + ", Y: " + ry;
	return gxy;
}

/**
 *  Get the heli translation for the XY values, pair these with mouse XY values.
 *  Accept TY means that clicks can change the start and end times.
 *
 *  @param {object} event event object such as mouseover
 */
function translate_heli(event)
{
	var gxy = getTranslation_heli(event);
	var ev = getEvent(event);
	var target = getTarget(ev);
	var mxy = getMouseXY(ev);
	
	currentMenu.acceptTYClick(target, mxy.x, mxy.y, gxy[0], gxy[1]);
}	

/**
 *  Finds out where you are on a helicorder to return accurate times, and accurate 
 *  vertical value for each row.
 *
 *  @param {object} event event object such as mouseover
 *  @returns an array with x, y and time
 *  @type array 
 */
function getTranslation_heli(event)
{
	var ev = getEvent(event);
	var target = getTarget(ev);
	var xy = getElementXY(ev, target);
	
	var t = target.translation;
	sx = xy[0];
	sy = xy[1];
	
	var gx, gy;
	
	if (sx < t[0] || sx > t[1])
	{
		gx = -1E300;
		gy = 0;
	}
	else
	{
		gx = t[4];
		gx += (sx - t[0]) * t[7];
		var row = Math.floor((sy - t[3]) / t[2]);
		gx += row * t[6];
		gy = row;
	}
	var tzselect  = document.getElementById('timeZoneAbbr');
	if(tzselect){
		// at least one time zone must be selected
		if (tzselect.selectedIndex == -1) {
			alert("You must select a time zone.");
			return;
		}
	} else {
		alert("Time zone not defined.");
		return;
	}
	if(tzselect[tzselect.selectedIndex].text != "UTC"){
		gx = gx + parseFloat(document.getElementById('timeZoneOffset').value);
	}
	
	var result = new Array(3);
	result[0] = gx;
	result[1] = gy;
	result[2] = timeToString(gx);
	return result;
}

/**
 *  Get event and target element x/y coordinates. For example mouse coordinates
 *  and png image coordinates which mouse is rolling over.
 *  
 *  Returns an array with two numeral based on the 'translation' property of the 
 *  target, as well as a time string. The numerals are latitude and longitude 
 *  translations for the map image.
 *  
 *  The numerals returned are then fed along with the mouse coordinates to 
 *  the function parameter "func" *  
 *
 *  @param {object} event event object such as mouseover
 *  @param {function} func function to apply
 */
function translate_map(event, func)
{
	var ev = getEvent(event);
	var target = getTarget(ev);
	var mxy = getMouseXY(ev);

	var ll = getTranslation_map(event);
	if (func)
		func(target, mxy.x, mxy.y, ll[0], ll[1]);
	else if (currentMenu)
		currentMenu.acceptMapClick(target, mxy.x, mxy.y, ll[0], ll[1]);
}

/**
 *  Get event and target element x/y coordinates. For example mouse coordinates
 *  and png image coordinates which mouse is rolling over.
 *  
 *  Returns an array with two numeral based on the 'translation' property of the 
 *  target, as well as a time string. The numerals can be latitude and longitude 
 *  translations for the map image.
 *  
 *
 *  @param {object} event event object such as mouseover
 */

function getTranslation_map(event)
{
	var ev = getEvent(event);
	var target = getTarget(ev);
	var xy = getElementXY(ev, target);
	var mxy = getMouseXY(ev);
	var t = target.translation;

	var sx = xy[0] + 1;
	var sy = xy[1] + 1;
	sy = target.height - sy;
	gx = sx * t[0] + t[1] * 1;
	gy = sy * t[2] + t[3] * 1;
	/**
	var ox = (t[4] * 1 + t[5] * 1) / 2;
	var oy = (t[6] * 1 + t[7] * 1) / 2;
	*/
	var ll = inverseTransverseMercator(t[6] * 1, t[7] * 1, gx, gy)
	ll[0] = Math.round(ll[0] * 1000) / 1000;
	ll[1] = Math.round(ll[1] * 1000) / 1000;
	while (ll[0] > 180)
		ll[0] -= 360;
	while (ll[0] <= -180)
		ll[0] += 360;
	
	ll[2] = ll[0] + ", " + ll[1];
	return ll;	
}
/** 
 *  Function to efficiently do nothing.
 *
 *  @param {object} event event object such as mouseover
 */
function translate_none(event)
{}

/** 
 *  Function to efficiently do nothing.
 *
 *  @param {object} event event object such as mouseover
 */
function getTranslation_none(event)
{}

/**
 *  This function takes latitude and longitude and returns the calculated Mercator x y.
 *  
 *  The Universal Transverse Mercator (UTM) coordinate system is a grid-based method of specifying locations on the surface of the Earth. It is used to identify locations on the earth, but differs from the traditional method of latitude and longitude.
 *
 *  @param {float} ox Mercator x
 *  @param {float} oy Mercator y
 *  @param {float} lon longitude
 *  @param {float} lat latitude
 *  @returns an array with x, y
 *  @type array 	
 */
function forwardTransverseMercator(ox, oy, lon, lat)
{
	var a=6378137;
	var esq=0.00669438;
	var epsq=esq/(1-esq);
	var scale=0.9996;
	var phiO=oy * 0.017453292519943295769236907684886;
	var lambdaO=ox * 0.017453292519943295769236907684886;
	var MO = a * (
	  (1 - esq/4 - 3*esq*esq/64 - 5*esq*esq*esq/256)*phiO -
	  (3*esq/8 + 3*esq*esq/32 + 45*esq*esq*esq/1024)*Math.sin(2*phiO) +
	  (15*esq*esq/256 + 45*esq*esq*esq/1024)*Math.sin(4*phiO) -
	  (35*esq*esq*esq/3072)*Math.sin(6*phiO)
	  );
    var phi = lat * 0.017453292519943295769236907684886;
    var lambda = lon * 0.017453292519943295769236907684886;
    var N = a / Math.pow(1 - esq * Math.sin(phi) * Math.sin(phi), 0.5);
    var T = Math.tan(phi) * Math.tan(phi);
    var C = epsq * Math.cos(phi) * Math.cos(phi);
    var A = (lambda - lambdaO) * Math.cos(phi);
    var M = a * (
	    (1 - esq/4 - 3*esq*esq/64 - 5*esq*esq*esq/256)*phi -
	    (3*esq/8 + 3*esq*esq/32 + 45*esq*esq*esq/1024)*Math.sin(2*phi) +
	    (15*esq*esq/256 + 45*esq*esq*esq/1024)*Math.sin(4*phi) -
	    (35*esq*esq*esq/3072)*Math.sin(6*phi)
	    );
    var x = scale*N*(A+(1-T+C)*A*A*A/6+(5-18*T+T*T+72*C-58*epsq)*A*A*A*A*A/120);
    var y = scale*(M - MO + N*Math.tan(phi)*(A*A/2+(5-T+9*C+4*C*C)*A*A*A*A/24+(61-58*T+T*T+600*C-330*epsq)*A*A*A*A*A*A/720));
	return new Array(x, y);
}
/**
 *  Take Mercator coordinates, and return latitude (lambda) and longitude (phi)
 *  
 *  The Universal Transverse Mercator (UTM) coordinate system is a grid-based method of specifying locations on the surface of the Earth. It is used to identify locations on the earth, but differs from the traditional method of latitude and longitude.
 *  
 *  @param {float} ox Mercator x
 *  @param {float} oy Mercator y
 *  @param {float} lon longitude
 *  @param {float} lat latitude
 *  @returns an array with latitude and longitude
 *  @type array 	
*/
function inverseTransverseMercator(ox, oy, x, y)
{
	var N1,T1,C1,R1,D,M,MO,phi1,mu,phi,lambda;
	var a=6378137;
	var esq=0.00669438;
	var epsq=esq/(1-esq);
	var scale=0.9996;
	var phiO=oy * 0.017453292519943295769236907684886;
	var lambdaO=ox * 0.017453292519943295769236907684886;
	var e1=(1-Math.sqrt(1-esq))/(1+Math.sqrt(1-esq));
	MO=a*(
		(1-esq/4-3*esq*esq/64-5*esq*esq*esq/256)*phiO-
		(3*esq/8+3*esq*esq/32+45*esq*esq*esq/1024)*Math.sin(2*phiO)+
		(15*esq*esq/256+45*esq*esq*esq/1024)*Math.sin(4*phiO)-
		(35*esq*esq*esq/3072)*Math.sin(6*phiO));
	M=MO+y/scale;
	mu=M/(a*(1-esq/4-3*esq*esq/64-5*esq*esq*esq/256));
	phi1=mu+(3*e1/2-27*e1*e1*e1/32)*Math.sin(2*mu)+(21*e1*e1/16-55*e1*e1*e1*e1/32)*Math.sin(4*mu)+(151*e1*e1*e1/96)*Math.sin(6*mu);
	N1=a/Math.sqrt(1-esq*Math.sin(phi1)*Math.sin(phi1));
	T1=Math.tan(phi1)*Math.tan(phi1);
	C1=epsq*Math.cos(phi1)*Math.cos(phi1);
	R1=a*(1-esq)/Math.pow(1-esq*Math.sin(phi1)*Math.sin(phi1),1.5);
	D=x/(N1*scale);
	phi=(phi1-(N1*Math.tan(phi1)/R1)*(D*D/2-(5+3*T1+10*C1-4*C1*C1-9*epsq)*D*D*D*D/24+(61+90*T1+298*C1+45*T1*T1-252*epsq-3*C1*C1)*D*D*D*D*D*D/720))*57.295779513082320876798154814105;
	lambda=(lambdaO+((D-(1+2*T1+C1)*D*D*D/6+(5-2*C1+28*T1-3*C1*C1+8*epsq+24*T1*T1)*D*D*D*D*D/120)/Math.cos(phi1)))*57.295779513082320876798154814105;
	return new Array(lambda, phi);
}