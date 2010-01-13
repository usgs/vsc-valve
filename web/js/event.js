// $Id: event.js,v 1.3 2005/09/21 18:11:51 dcervelli Exp $

/** @fileoverview  
 *  functions to deal with events and event monitoring 
 *
 * @author Dan Cervelli 
 */

var isIE = !window.opera && navigator.userAgent.indexOf('MSIE') != -1;

/**	
 *  Attach the function func to an element elt, either by adding an event listener
 *  or attaching an event by onEventType preform func. addEventListener is a standard
 *  Javascript function, the Internet Explorer equivalent is attachEvent. 
 *  Valve never utilizes useCapture.
 *
 *  @param {object} elt The element to add the listener to
 *  @param {string} eventType A string representing the event type to listen for
 *  @param {function} func The object that receives a notification when an event of the specified type occurs. This must be an object implementing the EventListener interface, or simply a JavaScript function. In Valve's case, it's always a function.
 *  @param {boolean} useCapture from docs on developer.mozilla.org: If true, useCapture indicates that the user wishes to initiate capture. After initiating capture, all events of the specified type will be dispatched to the registered listener before being dispatched to any EventTargets beneath it in the DOM tree. Events which are bubbling upward through the tree will not trigger a listener designated to use capture. See DOM Level 3 Events for a detailed explanation.
 *  @return success or failure returned as true or false
 *  @type boolean
 */
function addListener(elt, eventType, func, useCapture)
{
	if (elt.addEventListener)
	{
		elt.addEventListener(eventType, func, useCapture);
		return true;
	}
	else if (elt.attachEvent)
	{
		//if (elt == window)
		//	elt = document;
		var r = elt.attachEvent('on' + eventType, func);
		return r;
	}
	else
		elt['on' + eventType] = func;
}
/** 
 *  Remove an event listener
 *
 *  @param {object} elt The element to remove the listener from
 *  @param {string} eventType A string representing the event type listened for
 *  @param {function} func The object that receives a notification when an event of the specified type occurs. This must be an object implementing the EventListener interface, or simply a JavaScript function. In Valve's case, it's always a function.
 *  @param {boolean} useCaptureadd from docs on developer.mozilla.org: If true, useCapture indicates that the user wishes to initiate capture. After initiating capture, all events of the specified type will be dispatched to the registered listener before being dispatched to any EventTargets beneath it in the DOM tree. Events which are bubbling upward through the tree will not trigger a listener designated to use capture. See DOM Level 3 Events for a detailed explanation.
 *  @return success or failure returned as true or false
 *  @type boolean
 */
function removeListener(elt, eventType, func, useCapture)
{
	if (elt.removeEventListener)
	{
		alert("remove");
		elt.removeEventListener(eventType, func, useCapture);
	}	
}
/**
 *  Prevents further propagation of the current event in the event flow. 
 *  cancelBubble is the Microsoft syntax
 *  
 *  @param {event} e Event
 */
function consume(e)
{
	if (e.stopPropagation) 
	{
		e.stopPropagation();
		e.preventDefault();
	} 
	else if (e.cancelBubble) 
	{
		e.cancelBubble = true;
		e.returnValue  = false;
	}	
}
/**
 *  Retrieve an event from the document object model, in this case the window. 
 *  For example, mousing over a menu is an event.
 *  
 *  @param {event} e Event
 *  @return event object
 *  @type event object
*/

function getEvent(e)
{
	if (window.event)
		return window.event;
	else
		return e;
}

/** 
 *  Returns the target of an event(e) as an element.
 *
 *  @param {event} e Event
 *  @return event target
 *  @type object
 */
function getTarget(e)
{
	if (window.event)
		return window.event.srcElement;
	else if (e.target)
		return e.target;
	return null;
}

/**
 *  Return mouse x and y coordinates
 *  Non microsoft:
 *      pageX Returns the horizontal coordinate of the event relative to whole document. 
 *  Microsoft:
 *      clientX Sets or retrieves the x-coordinate of the mouse pointer's position
 *      relative to the client area of the window, excluding window decorations and
 *      scroll bars.
 *  @param {event} e Event
 *  @return object containing x and y properties.
 *  @type object
 */
function getMouseXY(e)
{
	var mouse = new Object();
	if (e.pageX && e.pageY)
	{
		mouse.x = e.pageX;
		mouse.y = e.pageY;
	}
	else if (e.clientX && e.clientY)
	{
		mouse.x = e.clientX;
		mouse.y = e.clientY;
		if (isIE)
		{
			mouse.x += document.documentElement.scrollLeft;
			mouse.y += document.documentElement.scrollTop;
		}
	}
	return mouse;
}

/**
 *  getElement's X and Y where e is an event and targ is target, like a png image for example
 *  
 *  We'll return a useful x/y coordinate of the target's upper left offset.
 *  
 *  @param {event} e Event
 *  @param {object} targ Target Element
 *  @return array with two numeric values: x and y.
 *  @type array
 */
function getElementXY(e, targ)
{
	var sX = 0;
	var sY = 0;
	var el = targ;
	do 
	{
		sX += el.offsetLeft;
		sY += el.offsetTop;
	}
	while ((el = el.offsetParent)); // Returns a reference to the object that is the current element's offset positioning context.
	if (isIE)
	{
		sX = e.x - sX + document.documentElement.scrollLeft;
		sY = e.y - sY + document.documentElement.scrollTop;
	}
	else
	{
		sX = e.clientX - sX + window.pageXOffset; // clientX Indicate the horizontal (x) and vertical (y) coordinate of the mouse at the moment the current event fired. These coordinates are relative to the viewable document area of the browser window or frame.
		sY = e.clientY - sY + window.pageYOffset;
	}
	return new Array(sX, sY);
}

/** 
 *  Checks the status of document scrolling; is the page offset?.
 *
 *  @return array with two numeric values: x and y offsets
 *  @type array
 */

function getScroll()
{
	if (document.documentElement)
		return new Array(document.documentElement.scrollLeft, document.documentElement.scrollTop);
	else
		return new Array(window.pageXOffset, window.pageYOffset);
}
