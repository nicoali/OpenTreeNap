"use strict";

var geocoder = require('treemap/lib/geocoder.js'),
    _ = require('lodash'),
    $ = require('jquery');

module.exports = function(triggerStream, formSelector) {
    var gcoder = geocoder();
    var reverseGeocodeStream = gcoder.reverseGeocodeStream(triggerStream);
    reverseGeocodeStream.onValue(function(geocode) {
        // Grab the applicable values
        var clean = function(value) {
                if (value === undefined || value === null) {
                    return '';
                }
                value = String(value).trim();
                return /^(undefined|null|none)$/i.test(value) ? '' : value;
            },
            updates = {'address_street': clean(geocode.address.Address),
                       'address_city': clean(geocode.address.City),
                       'address_zip': clean(geocode.address.Postal)};

        // Apply the updates to the form. If key == "address_zip",
        // this will get the value for "plot.address_zip" or "garden.address_zip"
        var $form = $(formSelector);
        _.each(updates, function(value, key) {
            $form.find("input[name$='" + key + "']").val(value);
        });

        if (geocode._attribution) {
            var $attribution = $form.find('.otn-geocode-attribution');
            if ($attribution.length === 0) {
                $attribution = $('<div class="otn-geocode-attribution small text-muted"></div>');
                $form.append($attribution);
            }
            $attribution.text('Indirizzo: ' + geocode._attribution);
        }
    });

    return reverseGeocodeStream;
};
