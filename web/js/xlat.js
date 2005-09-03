// $Id: xlat.js,v 1.1 2005-09-03 19:18:35 dcervelli Exp $

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

function translate_map(event)
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
		
	currentMenu.acceptMapClick(target, mxy.x, mxy.y, ll[0], ll[1]);
}

function translate_none(event)
{}

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