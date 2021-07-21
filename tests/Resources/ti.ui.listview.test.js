/*
 * Appcelerator Titanium Mobile
 * Copyright (c) 2015-Present by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
/* global OS_ANDROID, OS_IOS, OS_VERSION_MAJOR */
/* eslint-env mocha */
/* eslint no-unused-expressions: "off" */
'use strict';
const should = require('./utilities/assertions');
const utilities = require('./utilities/utilities');

const isCI = Ti.App.Properties.getBool('isCI', false);

describe('Titanium.UI', () => {
	it('#createListView()', () => {
		// Validate createListView() factory.
		should(Ti.UI.createListView).not.be.undefined();
		should(Ti.UI.createListView).be.a.Function();

		// Create ListView.
		const listView = Ti.UI.createListView();
		should(listView).be.a.Object();

		// Create list section.
		const section_0 = Ti.UI.createListSection({ headerTitle: 'Zero' });
		should(section_0).be.a.Object();

		// Set section items.
		section_0.items = [
			{ properties: { title: 'Red' } },
			{ properties: { title: 'White' } }
		];
		should(section_0.items.length).be.eql(2);

		// Append item to section.
		section_0.appendItems([ { properties: { title: 'Purple' } } ]);

		// Validate section items length.
		should(section_0.items.length).be.eql(3);

		// Create list section.
		const section_1 = Ti.UI.createListSection({ headerTitle: 'One' });
		should(section_1).be.a.Object();

		// Set section items.
		section_1.items = [
			{ properties: { title: 'Green' } },
			{ properties: { title: 'Yellow' } },
			{ properties: { title: 'Blue' } }
		];
		should(section_1.items.length).be.eql(3);

		// Set listView sections.
		listView.sections = [ section_0 ];

		// Validate listView section count.
		should(listView.sectionCount).be.eql(1);

		// Apend section to listView.
		listView.appendSection([ section_1 ]);

		// Validate listView section count.
		should(listView.sectionCount).be.eql(2);
	});
});

describe('Titanium.UI.ListView', function () {
	this.timeout(5000);

	let win;
	afterEach(done => { // fires after every test in sub-suites too...
		if (win && !win.closed) {
			win.addEventListener('close', function listener () {
				win.removeEventListener('close', listener);
				win = null;
				done();
			});
			win.close();
		} else {
			win = null;
			done();
		}
	});

	it.iosBroken('nsamespace exists', () => { // Should this be defined?
		should(Ti.UI.ListView).not.be.undefined();
	});

	describe('properties', () => {

		describe.ios('.allowsMultipleSelectionDuringEditing', () => {
			let list;

			beforeEach(() => {
				list = Ti.UI.createListView({
					allowsMultipleSelectionDuringEditing: true,
					sections: [ Ti.UI.createListSection({
						items: [ {
							properties: {
								title: 'My Title 1',
							}
						}, {
							properties: {
								title: 'My Title 2',
							}
						} ]
					}) ]
				});
			});

			it('is a Boolean', () => {
				should(list).have.a.property('allowsMultipleSelectionDuringEditing').which.is.a.Boolean();
			});

			it('equals value passed to factory method', () => {
				should(list.allowsMultipleSelectionDuringEditing).be.true();
			});

			it('can be assigned a Boolean value', () => {
				list.allowsMultipleSelectionDuringEditing = false;
				should(list.allowsMultipleSelectionDuringEditing).be.false();
			});

			it('has no accessors', () => {
				should(list).not.have.accessors('allowsMultipleSelectionDuringEditing');
			});

			it('lifecycle', function (finish) {
				win = Ti.UI.createWindow();
				win.addEventListener('open', function () {
					try {
						should(list.allowsMultipleSelectionDuringEditing).be.true();

						list.allowsMultipleSelectionDuringEditing = false;
						should(list.allowsMultipleSelectionDuringEditing).be.false();
					} catch (err) {
						return finish(err);
					}
					finish();
				});
				win.add(list);
				win.open();
			});
		});

		describe('.apiName', () => {
			it('is a read-only String', () => {
				const listView = Ti.UI.createListView();
				should(listView).have.readOnlyProperty('apiName').which.is.a.String();
			});

			it('equals \'Ti.UI.ListView\'', () => {
				const listView = Ti.UI.createListView();
				should(listView.apiName).be.eql('Ti.UI.ListView');
			});
		});

		it('.canScroll', () => {
			const listView = Ti.UI.createListView({ canScroll: false });
			should(listView.canScroll).be.be.false();
			listView.canScroll = !listView.canScroll;
			should(listView.canScroll).be.be.true();
		});

		it.android('.fastScroll', () => {
			const listView = Ti.UI.createListView();
			should(listView.fastScroll).be.false();
		});

		/**
		 * Validate ListSection header and footer properties.
		 */
		it('.headerView', finish => {
			const listView = Ti.UI.createListView();
			const sections = [];
			const fruitSection = Ti.UI.createListSection({ headerTitle: 'Fruits' });
			const fruitDataSet = [
				{ properties: { title: 'Apple' } },
				{ properties: { title: 'Banana' } },
			];
			const vegSection = Ti.UI.createListSection({ headerTitle: 'Vegetables' });
			const vegDataSet = [
				{ properties: { title: 'Carrots' } },
				{ properties: { title: 'Potatoes' } },
			];

			win = Ti.UI.createWindow({ backgroundColor: 'green' });

			// Set section items.
			fruitSection.items = fruitDataSet;
			vegSection.items = vegDataSet;

			// Set header and footer views for first section.
			fruitSection.headerView = Ti.UI.createView({ backgroundColor: 'black', height: 42 });
			fruitSection.footerView = Ti.UI.createView({ backgroundColor: 'black', height: 42 });
			sections.push(fruitSection);

			// Set header and footer views for second section.
			vegSection.headerView = Ti.UI.createView({ backgroundColor: 'black', height: 42 });
			vegSection.footerView = Ti.UI.createView({ backgroundColor: 'black', height: 42 });
			sections.push(vegSection);

			// NOTE: Since a headerTitle has already been defined, headerTitle will have priority.

			// Set ListView sections.
			listView.sections = sections;

			win.addEventListener('open', () => {
				try {

					// Validate section count.
					should(listView.sectionCount).be.eql(2);

					// Validate first section count.
					should(listView.sections[0].items.length).be.eql(2);

					// Validate first section items.
					should(listView.sections[0].items[0].properties.title).be.eql('Apple');
					should(listView.sections[0].items[1].properties.title).be.eql('Banana');

					// Validate second section count.
					should(listView.sections[1].items.length).be.eql(2);

					// Validate second section items.
					should(listView.sections[1].items[0].properties.title).be.eql('Carrots');
					should(listView.sections[1].items[1].properties.title).be.eql('Potatoes');
				} catch (err) {
					return finish(err);
				}
				finish();
			});

			win.add(listView);
			win.open();
		});

		it.ios('.selectedItems', function (finish) {
			const list = Ti.UI.createListView({
				sections: [ Ti.UI.createListSection({
					items: [ {
						properties: {
							title: 'My Title 1',
						}
					}, {
						properties: {
							title: 'My Title 2',
						}
					} ]
				}) ]
			});

			win = Ti.UI.createWindow();
			win.addEventListener('open', function () {
				try {
					list.selectItem(0, 1);
					should(list.selectedItems[0].section).be.eql(list.sections[0]);
					should(list.selectedItems[0].sectionIndex).be.eql(0);
					should(list.selectedItems[0].itemIndex).be.eql(1);
					list.selectItem(0, 0);
					should(list.selectedItems[0].section).be.eql(list.sections[0]);
					should(list.selectedItems[0].sectionIndex).be.eql(0);
					should(list.selectedItems[0].itemIndex).be.eql(0);
				} catch (err) {
					return finish(err);
				}
				finish();
			});
			win.add(list);
			win.open();
		});
	});

	describe('methods', () => {
		it('#appendSection()', finish => {
			const listView = Ti.UI.createListView();

			win = Ti.UI.createWindow({ backgroundColor: 'green' });

			const fruitSection = Ti.UI.createListSection({ headerTitle: 'Fruits' });
			fruitSection.items = [
				{ properties: { title: 'Apple' } },
				{ properties: { title: 'Banana' } },
			];

			const vegSection = Ti.UI.createListSection({ headerTitle: 'Vegetables' });
			vegSection.items = [
				{ properties: { title: 'Carrots' } },
				{ properties: { title: 'Potatoes' } },
			];

			const fishSection = Ti.UI.createListSection({ headerTitle: 'Fish' });
			fishSection.items = [
				{ properties: { title: 'Cod' } },
				{ properties: { title: 'Haddock' } },
			];

			listView.sections = [ fruitSection ];

			win.addEventListener('open', () => {
				try {

					// Validate section count.
					should(listView.sectionCount).be.eql(1);

					// Validate first section item count.
					should(listView.sections[0].items.length).be.eql(2);

					// Validate first section items.
					should(listView.sections[0].items[0].properties.title).be.eql('Apple');
					should(listView.sections[0].items[1].properties.title).be.eql('Banana');

					// Append second section.
					listView.appendSection(vegSection);

					// Validate new section count.
					should(listView.sectionCount).be.eql(2);

					// Validate second section item count.
					should(listView.sections[1].items.length).be.eql(2);

					// Validate second section items.
					should(listView.sections[1].items[0].properties.title).be.eql('Carrots');
					should(listView.sections[1].items[1].properties.title).be.eql('Potatoes');

					// Append last section using an array.
					listView.appendSection([ fishSection ]);

					// Validate new section count.
					should(listView.sectionCount).be.eql(3);

					// Validate last section item count.
					should(listView.sections[2].items.length).be.eql(2);

					// Validate last section items.
					should(listView.sections[2].items[0].properties.title).be.eql('Cod');
					should(listView.sections[2].items[1].properties.title).be.eql('Haddock');
				} catch (err) {
					return finish(err);
				}
				finish();
			});

			win.add(listView);
			win.open();
		});

		it('#insertSectionAt()', finish => {
			const listView = Ti.UI.createListView();

			win = Ti.UI.createWindow({ backgroundColor: 'green' });

			const fruitSection = Ti.UI.createListSection({ headerTitle: 'Fruits' });
			fruitSection.items = [
				{ properties: { title: 'Apple' } },
				{ properties: { title: 'Banana' } },
			];

			const vegSection = Ti.UI.createListSection({ headerTitle: 'Vegetables' });
			vegSection.items = [
				{ properties: { title: 'Carrots' } },
				{ properties: { title: 'Potatoes' } },
			];

			const fishSection = Ti.UI.createListSection({ headerTitle: 'Fish' });
			fishSection.items = [
				{ properties: { title: 'Cod' } },
				{ properties: { title: 'Haddock' } },
			];

			listView.sections = [ fruitSection, fishSection ];

			win.addEventListener('open', () => {
				try {

					// Validate section count.
					should(listView.sectionCount).be.eql(2);

					// Validate first section item count.
					should(listView.sections[0].items.length).be.eql(2);

					// Validate first section items.
					should(listView.sections[0].items[0].properties.title).be.eql('Apple');
					should(listView.sections[0].items[1].properties.title).be.eql('Banana');

					// Validate second section item count.
					should(listView.sections[1].items.length).be.eql(2);

					// Validate second section items.
					should(listView.sections[1].items[0].properties.title).be.eql('Cod');
					should(listView.sections[1].items[1].properties.title).be.eql('Haddock');

					// Append new section.
					listView.insertSectionAt(1, vegSection);

					// Validate new section count.
					should(listView.sectionCount).be.eql(3);

					// Validate second section item count.
					should(listView.sections[1].items.length).be.eql(2);

					// Validate second section items.
					should(listView.sections[1].items[0].properties.title).be.eql('Carrots');
					should(listView.sections[1].items[1].properties.title).be.eql('Potatoes');

					// Validate last section item count.
					should(listView.sections[2].items.length).be.eql(2);

					// Validate last section items.
					should(listView.sections[2].items[0].properties.title).be.eql('Cod');
					should(listView.sections[2].items[1].properties.title).be.eql('Haddock');
				} catch (err) {
					return finish(err);
				}
				finish();
			});

			win.add(listView);
			win.open();
		});

		it('#replaceSectionAt()', finish => {
			const listView = Ti.UI.createListView();

			win = Ti.UI.createWindow({ backgroundColor: 'green' });

			const fruitSection = Ti.UI.createListSection({ headerTitle: 'Fruits' });
			fruitSection.items = [
				{ properties: { title: 'Apple' } },
				{ properties: { title: 'Banana' } },
			];

			const vegSection = Ti.UI.createListSection({ headerTitle: 'Vegetables' });
			vegSection.items = [
				{ properties: { title: 'Carrots' } },
				{ properties: { title: 'Potatoes' } },
			];

			const fishSection = Ti.UI.createListSection({ headerTitle: 'Fish' });
			fishSection.items = [
				{ properties: { title: 'Cod' } },
				{ properties: { title: 'Haddock' } },
			];

			listView.sections = [ fruitSection, fishSection ];

			win.addEventListener('open', () => {
				try {

					// Validate section count.
					should(listView.sectionCount).be.eql(2);

					// Validate first section item count.
					should(listView.sections[0].items.length).be.eql(2);

					// Validate first section items.
					should(listView.sections[0].items[0].properties.title).be.eql('Apple');
					should(listView.sections[0].items[1].properties.title).be.eql('Banana');

					// Validate second section item count.
					should(listView.sections[1].items.length).be.eql(2);

					// Validate second section items.
					should(listView.sections[1].items[0].properties.title).be.eql('Cod');
					should(listView.sections[1].items[1].properties.title).be.eql('Haddock');

					// Append new section.
					listView.replaceSectionAt(1, vegSection);

					// Validate section count.
					should(listView.sectionCount).be.eql(2);

					// Validate second section item count.
					should(listView.sections[1].items.length).be.eql(2);

					// Validate second section items.
					should(listView.sections[1].items[0].properties.title).be.eql('Carrots');
					should(listView.sections[1].items[1].properties.title).be.eql('Potatoes');
				} catch (err) {
					return finish(err);
				}
				finish();
			});

			win.add(listView);
			win.open();
		});

		it('#deleteSectionAt()', function (finish) {
			const listView = Ti.UI.createListView();

			win = Ti.UI.createWindow({ backgroundColor: 'green' });

			const fruitSection = Ti.UI.createListSection({ headerTitle: 'Fruits' });
			fruitSection.items = [
				{ properties: { title: 'Apple' } },
				{ properties: { title: 'Banana' } },
			];

			const vegSection = Ti.UI.createListSection({ headerTitle: 'Vegetables' });
			vegSection.items = [
				{ properties: { title: 'Carrots' } },
				{ properties: { title: 'Potatoes' } },
			];

			const fishSection = Ti.UI.createListSection({ headerTitle: 'Fish' });
			fishSection.items = [
				{ properties: { title: 'Cod' } },
				{ properties: { title: 'Haddock' } },
			];

			listView.sections = [ fruitSection, vegSection, fishSection ];

			win.addEventListener('open', () => {
				try {

					// Validate section count.
					should(listView.sectionCount).be.eql(3);

					// Validate first section item count.
					should(listView.sections[0].items.length).be.eql(2);

					// Validate first section items.
					should(listView.sections[0].items[0].properties.title).be.eql('Apple');
					should(listView.sections[0].items[1].properties.title).be.eql('Banana');

					// Validate second section item count.
					should(listView.sections[1].items.length).be.eql(2);

					// Validate second section items.
					should(listView.sections[1].items[0].properties.title).be.eql('Carrots');
					should(listView.sections[1].items[1].properties.title).be.eql('Potatoes');

					// Validate last section item count.
					should(listView.sections[2].items.length).be.eql(2);

					// Validate last section items.
					should(listView.sections[2].items[0].properties.title).be.eql('Cod');
					should(listView.sections[2].items[1].properties.title).be.eql('Haddock');

					// Delete second section.
					listView.deleteSectionAt(1);

					// Validate new second section item count.
					should(listView.sections[1].items.length).be.eql(2);

					// Validate new second section items.
					should(listView.sections[1].items[0].properties.title).be.eql('Cod');
					should(listView.sections[1].items[1].properties.title).be.eql('Haddock');
				} catch (err) {
					return finish(err);
				}
				finish();
			});

			win.add(listView);
			win.open();
		});
	});

	//
	// Making sure setting header & footer doesn't throw exception
	//
	it('Basic ListSection header and footer', finish => {
		const listView = Ti.UI.createListView();
		const ukHeaderView = Ti.UI.createView({
			backgroundColor: 'black',
			height: 42
		});
		const ukFooterView = Ti.UI.createView({
			backgroundColor: 'black',
			height: 42
		});
		const ukSection = Ti.UI.createListSection({
			headerView: ukHeaderView,
			footerView: ukFooterView
		});
		const usSection = Ti.UI.createListSection({
			headerTitle: 'English US Header',
			footerTitle: 'English US Footer'
		});

		win = Ti.UI.createWindow({ backgroundColor: 'green' });

		ukHeaderView.add(Ti.UI.createLabel({ text: 'English UK Header', color: 'white' }));
		ukFooterView.add(Ti.UI.createLabel({ text: 'English UK Footer', color: 'white' }));

		ukSection.items = [
			{ properties: { title: 'Lift', color: 'black' } },
			{ properties: { title: 'Lorry', color: 'black' } },
			{ properties: { title: 'Motorway', color: 'black' } }
		];
		listView.appendSection(ukSection);

		usSection.items = [
			{ properties: { title: 'Elevator', color: 'black' } },
			{ properties: { title: 'Truck', color: 'black' } },
			{ properties: { title: 'Freeway', color: 'black' } }
		];
		listView.appendSection(usSection);

		win.addEventListener('open', () => {
			try {

				// Validate section count.
				should(listView.sectionCount).be.eql(2);

				// Validate first section item count.
				should(listView.sections[0].items.length).be.eql(3);

				// Validate first section items.
				should(listView.sections[0].items[0].properties.title).be.eql('Lift');
				should(listView.sections[0].items[1].properties.title).be.eql('Lorry');
				should(listView.sections[0].items[2].properties.title).be.eql('Motorway');

				// Validate second sectiion count.
				should(listView.sections[1].items.length).be.eql(3);

				// Validate second section items.
				should(listView.sections[1].items[0].properties.title).be.eql('Elevator');
				should(listView.sections[1].items[1].properties.title).be.eql('Truck');
				should(listView.sections[1].items[2].properties.title).be.eql('Freeway');
			} catch (err) {
				return finish(err);
			}
			finish();
		});

		win.add(listView);
		win.open();
	});

	/**
	 * Basic custom template test.
	 */
	it('template', finish => {
		const listView = Ti.UI.createListView({
			templates: {
				template: {
					childTemplates: [
						{
							type: 'Ti.UI.ImageView',
							bindId: 'pic',
							properties: {
								width: 50,
								height: 50,
								left: 0
							}
						},
						{
							type: 'Ti.UI.Label',
							bindId: 'info',
							properties: {
								color: 'black',
								font: {
									fontSize: 20,
									fontWeight: 'bold'
								},
								left: 60,
								top: 0,
							}
						},
						{
							type: 'Ti.UI.Label',
							bindId: 'es_info',
							properties: {
								color: 'gray',
								font: { fontSize: 14 },
								left: 60,
								top: 25,
							}
						}
					]
				}
			},
			defaultItemTemplate: 'template'
		});
		const sections = [];

		win = Ti.UI.createWindow({ backgroundColor: 'green' });

		const fruitSection = Ti.UI.createListSection({ headerTitle: 'Fruits / Frutas' });
		fruitSection.items = [
			{ info: { text: 'Apple' }, es_info: { text: 'Manzana' }, pic: { image: 'Logo.png' } },
			{ info: { text: 'Banana' }, es_info: { text: 'Banana' }, pic: { image: 'Logo.png' } }
		];
		sections.push(fruitSection);

		const vegSection = Ti.UI.createListSection({ headerTitle: 'Vegetables / Verduras' });
		vegSection.items = [
			{ info: { text: 'Carrot' }, es_info: { text: 'Zanahoria' }, pic: { image: 'Logo.png' } },
			{ info: { text: 'Potato' }, es_info: { text: 'Patata' }, pic: { image: 'Logo.png' } }
		];
		sections.push(vegSection);

		const grainSection = Ti.UI.createListSection({ headerTitle: 'Grains / Granos' });
		grainSection.items = [
			{ info: { text: 'Corn' }, es_info: { text: 'Maiz' }, pic: { image: 'Logo.png' } },
			{ info: { text: 'Rice' }, es_info: { text: 'Arroz' }, pic: { image: 'Logo.png' } }
		];
		sections.push(grainSection);

		listView.sections = sections;

		win.addEventListener('open', () => {
			try {

				// Validate section count.
				should(listView.sectionCount).be.eql(3);

				// Validate first section item count.
				should(listView.sections[0].items.length).be.eql(2);

				// Validate first section items.
				should(listView.sections[0].items[0].info.text).be.eql('Apple');
				should(listView.sections[0].items[1].info.text).be.eql('Banana');

				// Validate second section item count.
				should(listView.sections[1].items.length).be.eql(2);

				// Validate second section items.
				should(listView.sections[1].items[0].info.text).be.eql('Carrot');
				should(listView.sections[1].items[1].info.text).be.eql('Potato');

				// Validate last section item count.
				should(listView.sections[2].items.length).be.eql(2);

				// Validate last section items.
				should(listView.sections[2].items[0].info.text).be.eql('Corn');
				should(listView.sections[2].items[1].info.text).be.eql('Rice');
			} catch (err) {
				return finish(err);
			}
			finish();
		});

		win.add(listView);
		win.open();
	});

	describe('ListItem', function () {
		// Since the tested API is iOS only, we will skip all other platforms
		it.ios('properties', () => {
			const listView = Ti.UI.createListView({
				sections: [ Ti.UI.createListSection({
					items: [ {
						template: Ti.UI.LIST_ITEM_TEMPLATE_CONTACTS,
						properties: {
							title: 'My Title',
							subtitle: 'My Subtitle',
							subtitleColor: 'red',
							selectedSubtitleColor: 'green'
						}
					} ]
				}) ]
			});

			// Validate list and section
			should(listView.apiName).be.eql('Ti.UI.ListView');
			const section = listView.sections[0];
			should(section.apiName).be.eql('Ti.UI.ListSection');

			// Validate items
			const items = section.items;
			should(items).be.an.Array();
			should(items.length).be.a.Number();
			should(items.length).be.eql(1);

			// Validate single item
			const item = items[0];
			const template = item.template;
			const properties = item.properties;

			// Validate item template
			should(item).have.ownProperty('template');
			should(template).not.be.undefined();
			should(template).be.a.Number();
			should(template).eql(Ti.UI.LIST_ITEM_TEMPLATE_CONTACTS);

			// Validate item properties
			should(item).have.ownProperty('properties');
			should(properties).not.be.undefined();
			should(properties).be.an.Object();

			// Validate properties subtitleColor and selectedSubtitleColor
			should(properties).have.ownProperty('subtitleColor');
			should(properties.subtitleColor).be.a.String();
			should(properties.subtitleColor).be.eql('red');
			should(properties).have.ownProperty('selectedSubtitleColor');
			should(properties.selectedSubtitleColor).be.a.String();
			should(properties.selectedSubtitleColor).be.eql('green');

			// Validate properties title & subtitle
			should(properties).have.ownProperty('title');
			should(properties.title).be.a.String();
			should(properties.title).be.eql('My Title');
			should(properties).have.ownProperty('subtitle');
			should(properties.subtitle).be.a.String();
			should(properties.subtitle).be.eql('My Subtitle');
		});

		it('itemId', () => {
			const listView = Ti.UI.createListView({
				sections: [
					Ti.UI.createListSection({
						items: [
							{ properties: { title: 'Row 1', itemId: 'Foo' } },
						]
					})
				]
			});
			const section = listView.sections[0];
			const item = section.getItemAt(0);
			should(item.properties.itemId).be.eql('Foo');
			item.properties.itemId = 'Bar';
			section.updateItemAt(0, item);
			should(section.getItemAt(0).properties.itemId).be.eql('Bar');
		});

		it('custom properties', () => {
			const listView = Ti.UI.createListView({
				sections: [
					Ti.UI.createListSection({
						items: [
							{ properties: { title: 'Row 1', myNumber: 1 } },
						]
					})
				]
			});
			const section = listView.sections[0];
			section.appendItems([
				{ properties: { title: 'Row 2', myNumber: 2 } },
			]);
			should(section.getItemAt(0).properties.myNumber).be.eql(1);
			should(section.getItemAt(1).properties.myNumber).be.eql(2);

			const item = section.getItemAt(0);
			item.myString = 'my_string';
			section.updateItemAt(0, item);
			should(section.getItemAt(0).myString).be.eql('my_string');
		});
	});

	it('ListSection.items manipulation', () => {
		const section = Ti.UI.createListSection({
			items: [
				{ properties: { title: 'B' } },
				{ properties: { title: 'A' } },
				{ properties: { title: 'E' } },
				{ properties: { title: 'G' } }
			]
		});
		const items_a = [
			{ properties: { title: 'A' } },
		];
		const items_b = [
			{ properties: { title: 'C' } },
			{ properties: { title: 'D' } }
		];
		const items_c = [
			{ properties: { title: 'E' } },
			{ properties: { title: 'F' } }
		];
		const validation = [ 'A', 'B', 'C', 'D', 'E', 'F' ];

		section.updateItemAt(0, { properties: { title: 'A' } });
		section.updateItemAt(1, { properties: { title: 'B' } });
		section.updateItemAt(3, { properties: { title: 'F' } });

		section.insertItemsAt(2, items_b);

		section.deleteItemsAt(0, 1);
		section.deleteItemsAt(3, 2);

		section.appendItems(items_c);
		section.insertItemsAt(0, items_a);

		const items = section.items;
		should(items.length).be.eql(6);
		for (let i = 0; i < items.length; i++) {
			const item = items[i].properties.title;
			should(item).be.eql(validation[i]);
		}
	});

	it('ListSection.items manipulation (header & footer)', () => {
		const section = Ti.UI.createListSection({
			headerTitle: 'HEADER',
			footerTitle: 'FOOTER',
			items: [
				{ properties: { title: 'B' } },
				{ properties: { title: 'A' } },
				{ properties: { title: 'E' } },
				{ properties: { title: 'G' } }
			]
		});
		const items_a = [
			{ properties: { title: 'A' } },
		];
		const items_b = [
			{ properties: { title: 'C' } },
			{ properties: { title: 'D' } }
		];
		const items_c = [
			{ properties: { title: 'E' } },
			{ properties: { title: 'F' } }
		];
		const validation = [ 'A', 'B', 'C', 'D', 'E', 'F' ];

		section.updateItemAt(0, { properties: { title: 'A' } });
		section.updateItemAt(1, { properties: { title: 'B' } });
		section.updateItemAt(3, { properties: { title: 'F' } });

		section.insertItemsAt(2, items_b);

		section.deleteItemsAt(0, 1);
		section.deleteItemsAt(3, 2);

		section.appendItems(items_c);
		section.insertItemsAt(0, items_a);

		const items = section.items;
		should(items.length).be.eql(6);
		for (let i = 0; i < items.length; i++) {
			const item = items[i].properties.title;
			should(item).be.eql(validation[i]);
		}
	});

	// Making sure sections data is saved even when it's filtered (TIMOB-24019)
	it('TIMOB-24019', finish => {
		const listView = Ti.UI.createListView({
			width: Ti.UI.FILL,
			height: Ti.UI.FILL,
			caseInsensitiveSearch: true
		});

		win = Ti.UI.createWindow({ backgroundColor: 'green' });

		const fruitSection = Ti.UI.createListSection({ headerTitle: 'Fruits' });
		fruitSection.items = [
			{ properties: { title: 'Apple', searchableText: 'Apple' } },
			{ properties: { title: 'Banana', searchableText: 'Banana' } },
		];

		const vegSection = Ti.UI.createListSection({ headerTitle: 'Vegetables' });
		vegSection.items = [
			{ properties: { title: 'Carrots', searchableText: 'Carrots' } },
			{ properties: { title: 'Potatoes', searchableText: 'Potatoes' } },
		];

		listView.sections = [ fruitSection, vegSection ];

		win.addEventListener('open', () => {

			try {
				// Validate section count.
				should(listView.sectionCount).be.eql(2);

				// Validate first section item count.
				should(listView.sections[0].items.length).be.eql(2);

				// Validate first section items.
				should(listView.sections[0].items[0].properties.title).be.eql('Apple');
				should(listView.sections[0].items[1].properties.title).be.eql('Banana');

				// Validate second section item count.
				should(listView.sections[1].items.length).be.eql(2);

				// Validate section items.
				should(listView.sections[1].items[0].properties.title).be.eql('Carrots');
				should(listView.sections[1].items[1].properties.title).be.eql('Potatoes');

				// Filter to show 'Apple' and 'Potatoes'.
				listView.searchText = 'p';

				// Validate original section items still exist.
				setTimeout(() => {
					try {

						// Validate section count.
						should(listView.sectionCount).be.eql(2);

						// Validate first section item count.
						should(listView.sections[0].items.length).be.eql(2);

						// Validate first section items.
						should(listView.sections[0].items[0].properties.title).be.eql('Apple');
						should(listView.sections[0].items[1].properties.title).be.eql('Banana');

						// Validate second section item count.
						should(listView.sections[1].items.length).be.eql(2);

						// Validate section items.
						should(listView.sections[1].items[0].properties.title).be.eql('Carrots');
						should(listView.sections[1].items[1].properties.title).be.eql('Potatoes');
					} catch (err) {
						return finish(err);
					}
					finish();
				}, 2000);
			} catch (err) {
				return finish(err);
			}
		});

		win.add(listView);
		win.open();
	});

	it('.refreshControl (in NavigationWindow)', function (finish) {
		const window = Ti.UI.createWindow({
			title: 'Hello World',
			largeTitleEnabled: true,
			extendEdges: [ Ti.UI.EXTEND_EDGE_ALL ]
		});

		const control = Ti.UI.createRefreshControl();

		window.addEventListener('open', function () {
			control.beginRefreshing();
		});

		const nav = Ti.UI.createNavigationWindow({
			window: window
		});

		const listView = Ti.UI.createListView({
			refreshControl: control
		});

		control.addEventListener('refreshstart', function () {
			setTimeout(function () {
				control.endRefreshing();
			}, 2000);
		});

		control.addEventListener('refreshend', function () {
			nav.close();
			finish();
		});

		window.add(listView);
		nav.open();
	});

	it('.refreshControl (Basic)', finish => {
		const control = Ti.UI.createRefreshControl();
		const listView = Ti.UI.createListView({
			refreshControl: control
		});

		win = Ti.UI.createWindow();

		win.addEventListener('open', () => {
			control.beginRefreshing();
		});

		control.addEventListener('refreshstart', () => {
			setTimeout(() => {
				control.endRefreshing();
			}, 2000);
		});

		control.addEventListener('refreshend', () => {
			finish();
		});

		win.add(listView);
		win.open();
	});

	it.android('listView with Ti.UI.Android.CardView', finish => {
		const listView = Ti.UI.createListView({
			templates: {
				test: {
					childTemplates: [ {
						type: 'Ti.UI.Android.CardView',
						childTemplates: [ {
							type: 'Ti.UI.Label',
							bindId: 'label',
							properties: {
								color: 'black',
								bindId: 'label'
							}
						} ],
						properties: {
							width: Ti.UI.FILL,
							height: Ti.UI.SIZE,
							cardUseCompatPadding: true,
							backgroundColor: 'white',
							layout: 'vertical'
						}
					} ]
				}
			},
			defaultItemTemplate: 'test'
		});
		const section = Ti.UI.createListSection();
		const items = [];

		[ 'A', 'B', 'C' ].forEach(
			item => items.push({
				label: { text: item },
				template: 'test'
			})
		);

		win = Ti.UI.createWindow({
			backgroundColor: 'gray'
		});

		section.items = items;
		listView.sections = [ section ];

		win.addEventListener('open', () => {

			// Validated CardView renders without error.
			finish();
		});

		win.add(listView);
		win.open();
	});

	it('ListViewItem scaling (percent)', function () {
		// FIXME: Does not honour scale correctly on macOS: https://jira.appcelerator.org/browse/TIMOB-28261
		if (isCI && utilities.isMacOS() && OS_VERSION_MAJOR < 11) {
			this.skip();
			return;
		}

		const view = Ti.UI.createView({
			width: '540px',
			height: '960px'
		});
		const listView = Ti.UI.createListView({
			backgroundColor: 'white',
			height: '50%',
			templates: {
				template: {
					properties: {
						backgroundColor: 'blue',
						height: '50%'
					}
				}
			},
			defaultItemTemplate: 'template'
		});

		listView.sections = [
			Ti.UI.createListSection({
				items: [ {} ]
			})
		];

		view.add(listView);

		// ListViewItem should fill 50% of its parent ListView.
		should(view).matchImage('snapshots/listViewItemScaling_percent.png', { maxPixelMismatch: OS_IOS ? 2 : 0 }); // 2 pixels differ on actual iPhone
	});

	it('ListViewItem scaling (FILL)', function () {
		// FIXME: Does not honour scale correctly on macOS: https://jira.appcelerator.org/browse/TIMOB-28261
		if (isCI && utilities.isMacOS() && OS_VERSION_MAJOR < 11) {
			this.skip();
			return;
		}

		const view = Ti.UI.createView({
			width: '540px',
			height: '960px'
		});
		const listView = Ti.UI.createListView({
			backgroundColor: 'white',
			height: '50%',
			templates: {
				template: {
					properties: {
						backgroundColor: 'blue',
						height: Ti.UI.FILL
					}
				}
			},
			defaultItemTemplate: 'template'
		});

		listView.sections = [
			Ti.UI.createListSection({
				items: [ {} ]
			})
		];

		view.add(listView);

		// ListViewItem should fill the height of its parent ListView.
		should(view).matchImage('snapshots/listViewItemScaling_fill.png', { maxPixelMismatch: OS_IOS ? 10 : 0 }); // 10 pixels differ on actual iPhone
	});

	it('ListViewItem accessoryType', function () {
		// FIXME: Does not honour scale correctly on macOS: https://jira.appcelerator.org/browse/TIMOB-28261
		if (isCI && utilities.isMacOS() && OS_VERSION_MAJOR < 11) {
			this.skip();
			return;
		}

		const view = Ti.UI.createView({
			width: '540px',
			height: '960px'
		});
		const listView = Ti.UI.createListView();

		listView.sections = [
			Ti.UI.createListSection({
				items: [
					{ properties: { accessoryType: Ti.UI.LIST_ACCESSORY_TYPE_NONE } },
					{ properties: { accessoryType: Ti.UI.LIST_ACCESSORY_TYPE_CHECKMARK } },
					{ properties: { accessoryType: Ti.UI.LIST_ACCESSORY_TYPE_DETAIL } },
					{ properties: { accessoryType: Ti.UI.LIST_ACCESSORY_TYPE_DISCLOSURE } },
				]
			})
		];

		view.add(listView);

		// Validate items accessoryType.
		should(listView.sections[0].items[0].properties.accessoryType).be.eql(Ti.UI.LIST_ACCESSORY_TYPE_NONE);
		should(listView.sections[0].items[1].properties.accessoryType).be.eql(Ti.UI.LIST_ACCESSORY_TYPE_CHECKMARK);
		should(listView.sections[0].items[2].properties.accessoryType).be.eql(Ti.UI.LIST_ACCESSORY_TYPE_DETAIL);
		should(listView.sections[0].items[3].properties.accessoryType).be.eql(Ti.UI.LIST_ACCESSORY_TYPE_DISCLOSURE);

		// Validate items accessoryType icons.
		should(view).matchImage('snapshots/listViewItem_accessoryTypes.png', { maxPixelMismatch: OS_IOS ? 378 : 0 }); // iphone device can differ by 378
	});

	it('ListViewItem borderRadius', function () {
		// FIXME: Does not honour scale correctly on macOS: https://jira.appcelerator.org/browse/TIMOB-28261
		if (isCI && utilities.isMacOS() && OS_VERSION_MAJOR < 11) {
			this.skip();
			return;
		}

		const view = Ti.UI.createView({
			width: '540px',
			height: '960px'
		});
		const listView = Ti.UI.createListView({
			backgroundColor: 'white',
			templates: {
				template: {
					properties: {
						backgroundColor: 'blue',
						height: '64px',
						borderRadius: '16px'
					}
				}
			},
			defaultItemTemplate: 'template'
		});

		listView.sections = [
			Ti.UI.createListSection({
				items: [ {} ]
			})
		];

		view.add(listView);

		// ListViewItem should fill the height of its parent ListView.
		should(view).matchImage('snapshots/listViewItem_borderRadius.png', { maxPixelMismatch: OS_IOS ? 28 : 0 }); // 28 pixels differ on actual iPhone
	});

	it('ListItem default template layout', function () {
		// FIXME: Does not honour scale correctly on macOS: https://jira.appcelerator.org/browse/TIMOB-28261
		if (isCI && utilities.isMacOS() && OS_VERSION_MAJOR < 11) {
			this.skip();
			return;
		}

		const view = Ti.UI.createView({
			width: '540px',
			height: '960px'
		});
		const listView = Ti.UI.createListView({
			backgroundColor: 'blue'
		});

		listView.sections = [
			Ti.UI.createListSection({
				items: [
					{
						properties: {
							title: 'ListItem.title',
							image: 'Logo.png'
						}
					}
				]
			})
		];

		view.add(listView);

		// Validate default template displays `title` and `image` correctly.
		should(view).matchImage('snapshots/listItem_defaultTemplate.png', { maxPixelMismatch: OS_IOS ? 21 : 0 }); // 21 pixels differ on actual iPhone
	});

	it('ListItem template property', function () {
		// FIXME: Does not honour scale correctly on macOS: https://jira.appcelerator.org/browse/TIMOB-28261
		if (isCI && utilities.isMacOS() && OS_VERSION_MAJOR < 11) {
			this.skip();
			return;
		}

		const view = Ti.UI.createView({
			width: '540px',
			height: '960px'
		});
		const listView = Ti.UI.createListView({
			backgroundColor: 'white',
			templates: {
				template: {
					childTemplates: [
						{
							type: 'Ti.UI.Label',
							bindId: 'label',
							properties: {
								right: '5px',
								color: 'black'
							}
						}
					]
				}
			}
		});

		listView.sections = [
			Ti.UI.createListSection({
				items: [
					{
						template: 'template',
						label: { text: 'Red', color: 'red' }
					},
					{
						template: 'template',
						label: { text: 'Green', color: 'green' }
					},
					{
						// Validate no `template` property renders incorrectly.
						label: { text: 'Blue', color: 'blue' }
					}
				]
			})
		];

		view.add(listView);

		// Validate ListView only renders two items with specified `template`.
		should(view).matchImage('snapshots/listViewItem_template.png', { maxPixelMismatch: OS_IOS ? 23 : 0 }); // 23 pixels differ on actual iPhone
	});

	it('ListView header & footer', function () {
		// FIXME: Does not honour scale correctly on macOS: https://jira.appcelerator.org/browse/TIMOB-28261
		if (isCI && utilities.isMacOS() && OS_VERSION_MAJOR < 11) {
			this.skip();
			return;
		}

		const view = Ti.UI.createView({
			width: '540px',
			height: '960px'
		});
		const listView = Ti.UI.createListView({
			headerTitle: 'ListView.headerTitle',
			footerTitle: 'ListView.footerTitle',
			backgroundColor: 'white'
		});

		view.add(listView);

		// Both ListView header and footer should be visible.
		// Even without defining a ListSection.
		const options = {
			threshold: OS_IOS ? 0.2 : 0.1,
			// only for certain versions of Android? This happens on 5.x, what about 6-10?
			maxPixelMismatch: (OS_ANDROID && OS_VERSION_MAJOR < 6) ? 1236 : 0
		};
		should(view).matchImage('snapshots/listView_header_footer.png', options);
	});

	it('ListView + ListSection header & footer', function () {
		// FIXME: Does not honour scale correctly on macOS: https://jira.appcelerator.org/browse/TIMOB-28261
		if (isCI && utilities.isMacOS() && OS_VERSION_MAJOR < 11) {
			this.skip();
			return;
		}

		const view = Ti.UI.createView({
			width: '540px',
			height: '960px'
		});
		const listView = Ti.UI.createListView({
			headerTitle: 'ListView.headerTitle',
			footerTitle: 'ListView.footerTitle',
			backgroundColor: 'white'
		});

		listView.sections = [
			Ti.UI.createListSection({
				headerTitle: 'ListSection.headerTitle',
				footerTitle: 'ListSection.footerTitle',
				items: []
			})
		];

		view.add(listView);

		// Both ListView and ListSection header and footer should be visible.
		// Even without defining ListSection items.
		should(view).matchImage('snapshots/listView_listSection_header_footer.png', {
			maxPixelMismatch: OS_IOS ? 478 : 0 // iPad differs by ~4 pixels, iphone by 478
		});
	});

	it.ios('All text should show if ListView.style is .INSET_GROUPED ', () => {
		// FIXME: Does not honour scale correctly on macOS: https://jira.appcelerator.org/browse/TIMOB-28261
		if (utilities.isMacOS() && OS_VERSION_MAJOR < 11) {
			return;
		}

		const view = Ti.UI.createView({
			width: '540px',
			height: '960px'
		});
		const template = {
			childTemplates: [
				{
					type: 'Ti.UI.View',
					properties: { layout: 'horizontal', height: Ti.UI.SIZE, backgroundColor: 'blue' },
					childTemplates: [
						{
							type: 'Ti.UI.View',
							properties: { layout: 'horizontal', height: Ti.UI.SIZE, backgroundColor: 'green', width: Ti.UI.FILL },
							childTemplates: [
								{
									type: 'Ti.UI.Label',
									bindId: 'title',
								},
								{
									type: 'Ti.UI.Label',
									bindId: 'detail',
									properties: { font: { fontWeight: 'bold' } }
								}
							]
						},
					]
				}
			]
		};
		const section = Ti.UI.createListSection({
			headerTitle: 'Example',
			items: [ {
				title: { text: 'Lorem ipsum dolor sit amet, consectetur adipiscing elit. Aenean interdum laoreet augue.' },
				detail: { text: 'This text should show at the bottom' }
			} ]
		});

		const listView = Ti.UI.createListView({
			templates: { mytemplate: template },
			defaultItemTemplate: 'mytemplate',
			sections: [ section ],
			style: Titanium.UI.iOS.ListViewStyle.INSET_GROUPED
		});

		view.add(listView);

		should(view).matchImage('snapshots/listview_style_inset_grouped.png', {
			threshold: 0.1,
			maxPixelMismatch: 18380
		});
	});
});
