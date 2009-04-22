/** @fileoverview  
 * 
 * function menu for titlemenu.html 
 *
 * @author Dan Cervelli
 */

/**
  *  Called for tiltmenu.html, allows access to form elements via menu object
  *  Sets up time shortcut values for popup.  *  
  *
  *  @param {menu object} menu 
  */
create_tiltmenu = function(menu)
{
	menu.allowChannelMap = true;
	menu.formName = "tiltForm";
	menu.boxName = "tiltBox";
	menu.selector = "selector:ch";
	menu.timeShortcuts = new Array("-1h", "-2h", "-6h", "-12h", "-24h", "-2d", "-3d", "-1w", "-2w", "-1m");
}