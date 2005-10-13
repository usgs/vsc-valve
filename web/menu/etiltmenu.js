// $Id: etiltmenu.js,v 1.1 2005-10-13 22:23:08 dcervelli Exp $

create_etiltmenu = function(menu)
{
	menu.allowChannelMap = true;
	menu.formName = "eTiltForm";
	menu.boxName = "eTiltBox";
	menu.selector = "selector:ch";
	menu.timeShortcuts = new Array("-1h", "-2h", "-6h", "-12h", "-24h", "-2d", "-3d", "-1w", "-2w", "-1m");
}
