// $Id: ewrsammenu.js,v 1.2 2007-06-06 22:49:14 tparker Exp $

create_ewrsammenu = function(menu)
{
	menu.allowChannelMap = true;
	menu.formName = "ewrsamForm";
	menu.boxName = "ewrsamBox";
	menu.selector = "selector:ch";
	menu.timeShortcuts = new Array("-1m", "-6m", "-1y", "-2y", "-4y");
}
