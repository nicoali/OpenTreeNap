"use strict";

var $ = require('jquery'),
    _ = require('lodash'),
    Bacon = require('baconjs'),
    BU = require('treemap/lib/baconUtils.js'),
    config = require('treemap/lib/config.js'),
    geocoder = require('treemap/lib/geocoder.js');

module.exports = function (options) {
    var locationTypeahead = options.locationTypeahead,
        otherTypeaheads = options.otherTypeaheads || [],
        typeaheads = _.flatten([locationTypeahead, otherTypeaheads]),
        inputs = _.map(typeaheads, 'input').join(','),

        // Trigger a search when you:
        //   - Click the "Search" button
        //   - Hit "Enter" from typeahead input box
        //   - Choose a typeahead item
        enterOrClickStream = BU.enterOrClickEventStream({
            inputs: inputs,
            button: options.searchButton
        }),

        manualUpdates = new Bacon.Bus(),

        triggerSearchStream = Bacon.mergeAll(
            enterOrClickStream,
            Bacon.mergeAll(_.map(typeaheads, 'selectStream'))
        ),

        geocodeCandidateStream = Bacon.mergeAll(
            triggerSearchStream
                .flatMap(getDatum)
                .filter(_.identity),  // ignore false values
            manualUpdates
        ),
        gcoder = geocoder(),
        geocodedLocationStream = gcoder.geocodeStream(geocodeCandidateStream, options.forStorage);

    enterOrClickStream.onValue(function () {
        _.each(typeaheads, function (ta) {
            ta.autocomplete();
        });
    });

    return {
        geocodedLocationStream: geocodedLocationStream,
        triggerSearchStream: triggerSearchStream,
        triggerGeocode: function(datum) {
            manualUpdates.push(datum);
        }
    };

    function getDatum() {
        var datum = locationTypeahead.getDatum();
        if (datum) {
            if (datum.magicKey) {
                // Geocode this suggestion using its magic key
                return datum;
            } else {
                // Datum is a boundary, which we don't geocode
                return false;
            }
        } else if ($(locationTypeahead.input).val()) {
            // Accept free-form addresses too. The server will try an exact
            // lookup first and then a street-level fallback when a civic
            // number is not present in the geocoder database.
            return {
                text: $(locationTypeahead.input).val(),
                freeText: true
            };
        } else {
            // Blank input
            return false;
        }
    }
};
