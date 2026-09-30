"use strict";

// The catalog loads before the application bundles; fallback supports older
// pages that deliberately disable JavaScript translations.
exports.gettext = function (message) {
    return window.gettext ? window.gettext(message) : message;
};
