// $Id: event.js,v 1.2 2005-09-04 21:21:37 dcervelli Exp $

var isIE = !window.opera && navigator.userAgent.indexOf('MSIE') != -1;

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

function removeListener(elt, eventType, func, useCapture)
{
	if (elt.removeEventListener)
	{
		alert("remove");
		elt.removeEventListener(eventType, func, useCapture);
	}	
}

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

function getEvent(e)
{
	if (window.event)
		return window.event;
	else
		return e;
}

function getTarget(e)
{
	if (window.event)
		return window.event.srcElement;
	else if (e.target)
		return e.target;
	return null;
}

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
	while ((el = el.offsetParent));
	if (isIE)
	{
		sX = e.x - sX + document.documentElement.scrollLeft;
		sY = e.y - sY + document.documentElement.scrollTop;
	}
	else
	{
		sX = e.clientX - sX + window.pageXOffset;
		sY = e.clientY - sY + window.pageYOffset;
	}
	return new Array(sX, sY);
	
}

function getScroll()
{
	if (document.documentElement)
		return new Array(document.documentElement.scrollLeft, document.documentElement.scrollTop);
	else
		return new Array(window.pageXOffset, window.pageYOffset);
}
