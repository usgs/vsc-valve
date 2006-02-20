// $Id: xlat.js,v 1.3 2006-02-20 03:47:09 dcervelli Exp $

var lastTimeClick = 0;

function translate_ty(event)
{
	var ev = getEvent(event);
	var target = getTarget(ev);
	var xy = getElementXY(ev, target);
	var mxy = getMouseXY(ev);
	var t = target.translation;

	var sx = xy[0];
	var sy = xy[1];
	sy = target.height - sy;
	gx = sx * t[0] + t[1] * 1;
	gy = sy * t[2] + t[3] * 1;
	
	currentMenu.acceptTYClick(target, mxy.x, mxy.y, gx, gy);
}

function translate_xy(event)
{
	var ev = getEvent(event);
	var target = getTarget(ev);
	var xy = getElementXY(ev, target);
	var mxy = getMouseXY(ev);
	var t = target.translation;

	var sx = xy[0];
	var sy = xy[1];
	sy = target.height - sy;
	gx = sx * t[0] + t[1] * 1;
	gy = sy * t[2] + t[3] * 1;
	
	currentMenu.acceptXYClick(target, mxy.x, mxy.y, gx, gy);
}

function translate_heli(event)
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
	var mxy = getMouseXY(ev);
	
	currentMenu.acceptTYClick(target, mxy.x, mxy.y, gx, gy);
}	

function translate_map(event, func)
{
	var ev = getEvent(event);
	var target = getTarget(ev);
	var xy = getElementXY(ev, target);
	var mxy = getMouseXY(ev);
	var t = target.translation;

	var sx = xy[0];
	var sy = xy[1];
	sy = target.height - sy;
	gx = sx * t[0] + t[1] * 1;
	gy = sy * t[2] + t[3] * 1;
	/*var ox = (t[4] * 1 + t[5] * 1) / 2;
	var oy = (t[6] * 1 + t[7] * 1) / 2;
	*/
	var ll = inverseTransverseMercator(t[6] * 1, t[7] * 1, gx, gy)
	ll[0] = Math.round(ll[0] * 1000) / 1000;
	ll[1] = Math.round(ll[1] * 1000) / 1000;
	while (ll[0] > 180)
		ll[0] -= 360;
	while (ll[0] <= -180)
		ll[0] += 360;
		
	if (func)
		func(target, mxy.x, mxy.y, ll[0], ll[1]);
	else if (currentMenu)
		currentMenu.acceptMapClick(target, mxy.x, mxy.y, ll[0], ll[1]);
}

function translate_none(event)
{}

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