// $Id: rsammenu.js,v 1.1 2005-09-03 19:18:35 dcervelli Exp $

create_rsammenu = function(menu)
{
	menu.allowChannelMap = true;
	menu.formName = "rsamForm";
	menu.boxName = "rsamBox";
	menu.selector = "selector:ch";
	menu.timeShortcuts = new Array("-1h", "-2h", "-6h", "-12h", "-24h", "-2d", "-3d", "-1w", "-2w", "-1m");
}
