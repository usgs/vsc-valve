// $Id: ewrsammenu.js,v 1.1 2007-04-22 06:20:34 tparker Exp $

create_ewrsammenu = function(menu)
{
	menu.allowChannelMap = true;
	menu.formName = "ewrsamForm";
	menu.boxName = "ewrsamBox";
	menu.selector = "selector:ch";
	menu.timeShortcuts = new Array("-1w", "-1m", "-6m", "-1y", "-2y");
}
