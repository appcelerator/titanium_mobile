/*
 * Appcelerator Titanium Mobile
 * Copyright (c) 2011-Present by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
/* eslint-env mocha */
/* eslint no-unused-expressions: "off" */
'use strict';
const should = require('./utilities/assertions');

describe.android('Titanium.Geolocation', function () {
	// Methods

	it('async #forwardGeocoder()', () => {
		this.timeout(6e4); // 60 sec

		should(Ti.Geolocation.forwardGeocoder).be.a.Function;
		return Ti.Geolocation.forwardGeocoder('440 N Bernardo Ave, Mountain View')
			.then((data) => {
				should(data).have.property('success').which.is.a.Boolean;
				should(data.success).be.eql(true);
				should(data).have.property('code').which.is.a.Number;
				should(data.code).be.eql(0);
				should(data.latitude).be.approximately(37.387, 0.002); // iOS gives: 37.38605, Windows does 37.3883645
				should(data.longitude).be.approximately(-122.065, 0.02); // WIndows gives: -122.0512682, iOS gives -122.08385
				return;
			});
	});

	// FIXME The address object is different from platform to platform! https://jira.appcelerator.org/browse/TIMOB-23496
	it('async #reverseGeocoder()', () => {
		this.timeout(6e4); // 60 sec

		should(Ti.Geolocation.reverseGeocoder).be.a.Function;
		return Ti.Geolocation.reverseGeocoder(37.3883645, -122.0512682)
			.then((data) => {
				should(data).have.property('success').which.is.a.Boolean;
				should(data.success).be.eql(true);
				should(data).have.property('code').which.is.a.Number;
				should(data.code).be.eql(0);
				// FIXME error property is missing altogether on success for iOS...
				// should(data).have.property('error'); // undefined on success, holds error message as String otherwise.
				should(data).have.property('places').which.is.an.Array;

				should(data.places[0].postalCode).be.eql('94043');
				should(data.places[0].zipcode).be.eql('94043');
				should(data.places[0]).have.property('latitude').which.is.a.Number; // docs say String!
				should(data.places[0]).have.property('longitude').which.is.a.Number; // docs say String!
				should(data.places[0].country).be.eql('USA');
				should(data.places[0].state).be.eql('California');
				should(data.places[0].country_code).be.eql('US');
				should(data.places[0]).have.property('city').which.is.a.String;
				should(data.places[0]).have.property('address').which.is.a.String;
				return;
			});
	});
});
