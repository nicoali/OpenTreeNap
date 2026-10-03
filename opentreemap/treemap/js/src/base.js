"use strict";

// This entry module is loaded in 'base.html' and is used to load JS that
// should run on every page on the site

require("../../../../assets/css/sass/main.scss");
require("autotrack");
require("treemap/lib/buttonEnabler.js").run();
require("treemap/lib/export.js").run();

// Filters change the URL after rendering; preserve their current state.
var $ = require('jquery');
$('.otn-language').on('submit', function () {
    $(this).find('input[name="next"]').val(
        window.location.pathname + window.location.search + window.location.hash
    );
});

// Polyfill for String.startsWith(), not supported in IE 11
if (!String.prototype.startsWith) {
    String.prototype.startsWith = function (searchString, position) {
        position = position || 0;
        return this.indexOf(searchString, position) === position;
    };
}
