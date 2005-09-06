// $Id: tiltmenu.js,v 1.1 2005-09-06 21:34:48 dcervelli Exp $

create_tiltmenu = function(menu)
{
	menu.allowChannelMap = true;
	menu.formName = "tiltForm";
	menu.boxName = "tiltBox";
	menu.selector = "selector:ch";
	menu.timeShortcuts = new Array("-1h", "-2h", "-6h", "-12h", "-24h", "-2d", "-3d", "-1w", "-2w", "-1m");
}
