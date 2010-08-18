/** 
 * @fileoverview Functions in help.js add help pop-ups to tagged elements.
 * 
 * To use these, add the class "help" to a heading element (h1 or h2)
 * and then in BY_TEXT below, give the mapping from the heading text
 * to the file URL that should be displayed.  Alternatively, add "help"
 * and then define a selector (which should include "help").  In both
 * cases the "help" class is removed once the element is processed
 * (this makes the processing faster when repeated). 
 * 
 * The design might seem a bit odd, but the following alternatives were 
 * discarded:
 * - A custom attribute that includes the URL (not sure custom attributes
 *   are a good idea for compatibility; having the URLs spread over many
 *   files seems like a bad idea)
 * - No class, just a CSS selector in the table below (unfortunately
 *   CSS selectors can't select on enclosed text - it's not xpath -
 *   and so the targets would be difficult to define).
 *   
 * Note that the firebux plugin in Firefox (particularly with the 
 * Firefinder extension) is very useful in inspecting a page to find
 * an appropriate CSS selector.
 *  
 * @author Andrew Cooke
 */

function help_init() {YUI({base: 'yui/3.1.1/', combo: false}).use(
		'event', 'node', 'dom', function(Y) {

	var DEBUG = true;
	var BY_TEXT = {
		'ISTI Valve': 'doc/user_docs/index.html',
		'Start Time': 'doc/user_docs/index.html#commonPanel',
	};
	var BY_SELECTOR = {
		'#contentInsertionPoint div.titleBar h1.help': 'doc/user_docs/index.html#graphs',
		'#isti_deformation_gps_pane_options_0- h1.help': 'doc/user_docs/gps/gpsdoc.html',
		'#isti_deformation_tilt_pane_options_0- h1.help': 'doc/user_docs/tilt/tiltdoc.html',
		'#isti_deformation_strain_genericFixedForm h1.help': 'doc/user_docs/strain/straindoc.html',
	}
	
	function debug(something) {
		if (DEBUG && window.console) {
			console.log(something);
		}
	}
	
	/**
	 * Scan the entire document, checking for headings with the appropriate
	 * class. 
	 */
	function update_all() {
		Y.all('.help').each(update_by_text);
		for (selector in BY_SELECTOR) {
			Y.all(selector).each(function(element) {update(element, BY_SELECTOR[selector]);});
		}
	}
	
	/**
	 * Process a single element, using the text from the map.
	 * 
	 * @param element The element that needs a help image + popup.
	 */
	function update_by_text(element) {
		var text = element.get('text');
		if (! (text in BY_TEXT)) {
			debug('no url for ' + text);
		} else {
			var url = BY_TEXT[text];
			update(element, url);
		}
	}
	
	/**
	 * Process a single element.  Add an image, float right, just before in the DOM,
	 * with an onclick for the popup URL.
	 * 
	 * @param element The element that needs a help image + popup.
	 * @url the URL for the popup
	 */
	function update(element, url) {
		debug(element);
		var parent = element.get('parentNode');
		var html = '<img class="fr helpimg" src="images/help.gif" onclick="help_popup(\'' + url + '\')"/>';
		parent.insert(html, element);
		element.removeClass('help');
	}
	
	/**
	 * This is annoying, but no cross-browser event exists for DOM change (IE doesn't
	 * support anything and YUI doesn't add anything).  Luckily the action when there
	 * has been no change is very brief.
	 */
	function loop() {
		debug('help tick');
		update_all();
		setTimeout(loop, 1000);
	}
	
	setTimeout(loop, 100); // allow page to be built
	
});}


/**
 * Display the URL in a new window.
 * 
 * @param url The url to display.
 */
function help_popup(url) {
	var new_window=window.open(url, 'name', 'height=600,width=800,top=100,left=100,scrollbars=yes');
	new_window.focus();
	return false;
}
