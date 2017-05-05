/** @fileoverview
 *
 * function menu for genericfixedmenu.html
 *
 * @author Dan Cervelli, Loren Antolik
 */

/**
 *  Called for genericfixedmenu.html, allows access to form elements via menu object
 *  Sets up time shortcut values for popup.
 *
 *  Also, this tries to create and display a menu defined by an XML file for this
 *  generic data source.
 *
 *  @param {menu object} menu
 */
create_genericfixedmenu = function(menu) {
  menu.formName = "genericFixedForm";
  menu.boxName  = "genericFixedBox";

  // override the default initialize function because the columns box needs to be populated here
  menu.initialize = function() {

    // default initialization
    Menu.prototype.initialize.call(this);

    // populate columns
    populateGenericColumns(this);

    // populate suppdata types
    populateSuppDataTypes(this);

    // initialize the time shortcuts
    if (menu.timeShortcuts[0] == "") {
      menu.timeShortcuts  = new Array("-10i","-30i","-1h","-6h","-12h","-1d","-3d","-1w","-1m","-1y");
    }

    var sel = document.getElementById(this.id + '_selector:ds');
    addListener(sel, 'change',
      function() {
        var interval = document.getElementById(this.id + '_interval');
        switch(sel.selectedIndex)
        {
          case 0:
            interval.firstChild.textContent = "Interval:";
            break;
          case 1:
            interval.firstChild.textContent = "Interval, pts: ";
            break;
          case 2:
            interval.firstChild.textContent = "Interval, sec: ";
            break;
        }
      }, false);

    sel = document.getElementById(this.id + '_showall');
    addListener(sel, 'change', function() {
      if (currentMenu)
        currentMenu.filterChanged();
    }, false);
  }

  // override the default presubmit function
  menu.presubmit = function(pr, pc) {

    // call the main presubmit function
    if (!Menu.prototype.presubmit.call(this)) {
      return false;
    } else {
      return true;
    }
  }
}
