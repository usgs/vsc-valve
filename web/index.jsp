<%@ page language="java" %>
<%@ page import="gov.usgs.valve3.Valve3, java.util.Date" %>
<%@ page session="false" %>
<%@ page isThreadSafe="true" %>

<!DOCTYPE HTML PUBLIC "=-//W3C//DTD HTML 4.01//EN"
	"http://www.w3.org/TR/html4/strict.dtd">

<html>
	<head>
		<title>Valve Menu</title>
		<link rel="stylesheet" href="valve3.css">
		
        <script type="text/javascript" src="yui/3.1.1/yui/yui-min.js"></script>
		<script type="text/javascript" src="js/event.js"></script>		
		<script type="text/javascript" src="js/time.js"></script>		
		<script type="text/javascript" src="js/xml.js"></script>
		<script type="text/javascript" src="js/menu.js"></script>
		<script type="text/javascript" src="js/ui.js"></script>
		<script type="text/javascript" src="js/plot.js"></script>
		<script type="text/javascript" src="js/popup.js"></script>
		<script type="text/javascript" src="js/box.js"></script>
		<script type="text/javascript" src="js/xlat.js"></script>
        <script type="text/javascript" src="js/help.js"></script>
		
		<script type="text/javascript">
		
		function onLoad()
		{
			var p = document.getElementById('timeShortcutPanel');
			addListener(p, 'click', timeShortcutClick, false);
			populateTimeShortcuts(new Array("-1i", "-1h", "-1d", "-1w", "-1m", "-1y"));
			addListener(document.getElementById('timeShortcutButton'), 'click', toggleTimeShortcutPanel, false);
			addListener(document.getElementById('minMaxUI'), 'click', toggleUI, false);
			addListener(document.getElementById('submit'), 'click', doSubmit, false);
			addListener(document.getElementById('deleteAll'), 'click', deleteAll, false);
			addListener(document.getElementById('nowLink'), 'click', function() { document.getElementById('endTime').value = 'Now'; fixZoomMarksInside(null,0);}, false);
            help_init();

			var b = document.getElementById("channelMapButton");
			b.disabled = true;
			
			addListener(document.getElementById('geoFilter'), 'change', 
				function()
				{
					var wesn = getGeoFilter();
					b.disabled = wesn == null;
				});
			
			addListener(b, 'click',
				function()
				{
					var menu = "";
					if (currentMenu && currentMenu.allowChannelMap)
						menu = currentMenu.id;
					var pr = new PlotRequest();
					var pc = pr.createComponent(menu, 0, 1);
					pc.src = "channel_map";
					pc.subsrc = menu;
					var wesn = getGeoFilter();
					if (wesn == null)
					{
						alert("You must select a geographic filter to generate a channel map.");
						return;
					}
					pc.west = wesn[0];
					pc.east = wesn[1];
					pc.south = wesn[2];
					pc.north = wesn[3];
					if (currentMenu)
						pc.getChannelsForMap(currentMenu.getForm());
					loadXML("channel map plot", pr.getURL());
				});
			
			loadDataSources();
		}
	
		function ieWindowLoaded(event)  
		{
			document.body.ondrag = function () { return false; };
			document.body.onselectstart = function () { return false; };
		}
		
		addListener(window, 'load', onLoad, false);
		</script>
	</head>
	
	<% if (config.getServletContext().getResource("/header.html") != null) { %>
        <!-- <jsp:include page="header.html" /> -->
    <% } %>
	
	<body onload="ieWindowLoaded(event);">
		<div id="container">
		
		<SCRIPT TYPE="text/javascript" LANGUAGE="JavaScript">
		if (!navigator.javaEnabled())
		document.write("<H1><center><big>VALVE requires Javascript to be enabled.</big></center></H1>");
		</SCRIPT>
		<NOSCRIPT><H1><center><big>VALVE requires JavaScript to be enabled.</big></center></H1></NOSCRIPT>
		
		
		<div class="topBar">
			<img id="deleteAll" class="fr" src="images/x.gif">
			<img id="minMaxUI" class="fr" src="images/min.gif">
            <span id="appTitle" class="help"></span>
		</div>

		<div id="uiPanel">
			<!-- TODO: rework the data panel to use boxes -->
			<div id="dataPanel" class="dataPanel">
				<h5 id="version"></h5>
				<div class="clear"><h5 class="help">Administrator: <a id='admin'></a></h5></div>
				<hr>
				<img class="fl" src="images/folders.gif"><h2>Data Sources</h2>
				<ul class="menu">
				</ul>
			</div>
			
			<div id="openPanel" class="openPanel">
			</div>
			
			<div id="timePanel">
				<div class="box">
					<img src="images/clock_down.gif" class="fl" id="timeShortcutButton"><h1 class="help">Start Time</h1>
					<p><input class="mono" type="text" value="" id="startTime" size="17"
						onchange=fixZoomMarksInside(null,0)></p>
				</div>
				<div class="box">
					<h1>End Time</h1>
					<p><input class="mono" type="text" value="Now" id="endTime" size="17"
						onchange=fixZoomMarksInside(null,0)></p>
				</div>
				<h5>'yyyy[MMdd[hhmm]]' or '<a id="nowLink">Now</a>'.</h5>
				<div class="box">
					<h1>Time Zone</h1>
					<p>
					<select id="timeZoneAbbr" name="selector:tz" class="w100p">
						<option selected="selected"><%=Valve3.getInstance().getTimeZoneAbbr()%></option>
						<option>UTC </option>
					</select>
					</p>
					<input type="hidden" value="<%=Valve3.getInstance().getTimeZoneOffset(new Date())%>" id="timeZoneOffset">
				</div>
				<div class="box">
					<h1>Output Size</h1>
					<p>
					<select id="outputSize" class="w100p">
					<option>Tiny</option>
					<option>Small</option>
					<option selected="selected">Medium</option>
					<option>Large</option>
					</select>
					</p>
				</div>
				<div class="box">
					<h1>Geographic Filter</h1>
					<p>
					<select id="geoFilter" class="w100p">
					<option value="[none]">[none]</option>
					</select>
				</div>
				<p>
				<input type="button" value="Draw Map" id="channelMapButton">
				<input id="submit" type="button" value="Submit">
				<img id="throbber" class="fr" src="images/throbber_still.png">
				</p>
			</div>
			
			<div class="box listbox" id="timeShortcutPanel">
				<h1>Time Shortcuts</h1>
			</div>
		</div>
		
		<div style="clear:left;">		
			<div id="contentInsertionPoint"></div>
		</div>

		<div id='popupInsertionPoint'></div>
		<!-- Invisible elements -->		
		<div id="contentTemplate" class="content">
			<img name="close_btn" class="button" src="images/x.gif">
			<img name="minimize_btn" class="button" src="images/min.gif">
			<img name="clock_btn" class="button" src="images/clock.gif">
			<img name="combine_btn" class="button" src="images/add.gif">
			<img name="procdata_btn" class="button" src="images/procdata.gif">
			<img name="xml_btn" class="button" src="images/xml.gif">
			<img name="export_data_btn" class="button" src="images/data.gif">
			<img name="export_json_btn" class="button" src="images/json.gif">
			<img name="export_xml_btn" class="button" src="images/xml_data.gif">
			<img name="export_csvnots_btn" class="button" src="images/csvnots.gif">
			<img name="export_csv_btn" class="button" src="images/csv.gif">
			<img name="export_ps_btn" class="button" src="images/ps.gif">
			<a target="plot" href=""><img class="button" src="images/bkmrk.gif"></a>
			<div class="titleBar">
				<h1 class="help">Content Template</h1>
				<h1>Status Text</h1>
			</div>
			<div class="suppviewer">
				<table bgcolor="#CCCCCC">
					<tr>
						<td colspan="2"><b><i>Supplemental Data</i></b></td>
						<td align="right"><img src="images/x.gif"/></td>
					</tr>
					<tr>
						<td colspan="3" name="sd_span"><b>Timespan</b>:</td>
					</tr>
					<tr>
						<td colspan="3"><b>Type:</b></td>
					</tr>
					<tr>
						<td colspan="3"><b>Data:</b></td>
					</tr>
				</table>
			</div>
			<div class="suppnodl">
				<div class="tableContainer">
				<table class="suppnodltable" width="300px">
				<thead class="fixedHeader"><tr><th>Processing Data</th></tr></thead>
				<tbody class="scrollContent"></tbody>
				</table>
				</div>
			</div>
			<img class="pointer">
			<input name="componentCount" type="hidden" value="">	
			<div id="combineMenuTemplate" class="combine">
			</div>
		</div>
		
		<ul>
			<li id='listItemTemplate'>
				<h1><img src="images/plus.png"> Category</h1>
				<ul class="hiddenSubMenu">
				</ul>
			</li>
		</ul>
		
		<div id='popupTemplate' class="box abs">
			<img class="button" src="images/x.gif"><h1>Popup Title</h1>
			<div>
				<img>
			</div>
		</div>
		
		<div id='dataTemplate'>
			<iframe id="dataFrame"></iframe>
		</div>
		
		<img src="images/redMark.png" id="redMark">
		<img src="images/greenMark.png" id="greenMark">
		</div>
		
		<div id="redLine1"></div>
		<div id="redLine2"></div>
		
		<!--% if (config.getServletContext().getResource("/footer.html") != null) { %-->
			<!--  jsp:include page="footer.html" /-->
		<!--  % } %-->		
		
	</body>
</html>
