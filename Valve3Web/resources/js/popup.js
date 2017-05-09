// $Id: popup.js,v 1.1 2005/09/03 19:18:35 dcervelli Exp $
/**  @fileoverview
 * contains a function for dealing with popup windows.
 * closeups of waveform plots are popup windows. This js also adds
 * listeners for mouse move or mouse up.
 *
 * @author Dan Cervelli
 */
var dragging = false;
var dragged = null;
var popupCount = 0;
/**
 *  Create a popup window for a waveform, for example. You can move this popup around on the
 *  screen.
 *
 *  @param {integer} px X coordinate for popup location
 *  @param {integer} py Y coordinate for popup location
 *  @return Returns the popup element
 *  @type element
 */
function createPopup(px, py)
{
  var p = document.getElementById('popupTemplate').cloneNode(true);
  p.style.display = 'none';
  p.id = "popup" + popupCount;
  popupCount++;
  p.style.top = py + 'px';
  p.style.left = px + 'px';

  var b = document.getElementById('popupInsertionPoint');
  addListener(p.getElementsByTagName('img')[0], 'click',
    function()
    {
      b.removeChild(p);
    }, false);

  var d = p.getElementsByTagName('h1')[0];
  addListener(d, 'mousedown',
    function(ev)
    {
      ev = getEvent(ev);
      dragging = true;
      dragged = new Object();
      dragged.target = p;
      d.style.cursor = "move";
      var xy = getElementXY(ev, getTarget(ev));
      dragged.xOffset = xy[0];
      dragged.yOffset = xy[1];
    }, false);

  b.appendChild(p);

  return p;
}
/**
  Monitors for dragging an object, and then move the target object
  along with the mouse coordinates
 */
addListener(document, 'mousemove',
    function(ev)
    {
      if (dragging)
      {
        var e = getEvent(ev);
        var mouse = getMouseXY(e);
        var dragTarget = dragged.target;
        dragTarget.style.top = (mouse.y - dragged.yOffset) + 'px';
        dragTarget.style.left = (mouse.x - dragged.xOffset) + 'px';
        consume(e);
      }
    }, false);

/**
  If mouse is let go on a drag, clear element cursor and let it go to default value.
 */

addListener(document, 'mouseup',
    function(ev)
    {
      if (dragging)
      {
        dragged.target.getElementsByTagName('h1')[0].style.cursor = "";
        dragged = null;
        dragging = false;
      }
    }, false);
/// end popup
