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

describe('Titanium.UI.TableView', function () {
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

	it.iosBroken('Ti.UI.TableView', () => { // should this be defined?
		should(Ti.UI.TableView).not.be.undefined();
	});

	it('.apiName', () => {
		const tableView = Ti.UI.createTableView();

		should(tableView).have.readOnlyProperty('apiName').which.is.a.String();
		should(tableView.apiName).be.eql('Ti.UI.TableView');
	});

	it('createTableView', () => {

		// Validate createTableView()
		should(Ti.UI.createTableView).not.be.undefined();
		should(Ti.UI.createTableView).be.a.Function();

		// Validate createTableViewSection()
		should(Ti.UI.createTableViewSection).not.be.undefined();
		should(Ti.UI.createTableViewSection).be.a.Function();

		// Validate createTableViewRow()
		should(Ti.UI.createTableViewRow).not.be.undefined();
		should(Ti.UI.createTableViewRow).be.a.Function();

		// Create TableView section
		const section_0 = Ti.UI.createTableViewSection({ headerTitle: 'Zero' });
		should(section_0).be.a.Object();
		should(section_0.apiName).be.a.String();

		// FIXME: iOS gives wrong apiName for section object.
		// should(section_0.apiName).be.eql('Ti.UI.TableViewSection');

		// Create and add two rows to the section
		section_0.add(Ti.UI.createTableViewRow({ title: 'Red' }));
		section_0.add(Ti.UI.createTableViewRow({ title: 'White' }));
		section_0.add(Ti.UI.createTableViewRow({ title: 'Purple' }));

		// Validate section rowCount
		should(section_0.rowCount).be.eql(3);

		// Validate a section row title
		should(section_0.rows[0].title).be.eql('Red');

		// Create another TableView section
		const section_1 = Ti.UI.createTableViewSection({ headerTitle: 'One' });
		should(section_1).be.a.Object();

		// Create and add three rows to the section
		section_1.add(Ti.UI.createTableViewRow({ title: 'Green' }));
		section_1.add(Ti.UI.createTableViewRow({ title: 'Yellow' }));
		section_1.add(Ti.UI.createTableViewRow({ title: 'Blue' }));

		// Validate section row count
		should(section_1.rowCount).be.eql(3);

		// Validate a section row title
		should(section_1.rows[2].title).be.eql('Blue');
		should(section_1.rows[2].apiName).be.a.String();

		// FIXME: iOS gives wrong apiName for row object.
		// should(section_1.rows[2].apiName).be.eql('Ti.UI.TableViewRow');

		// Create TableView, set data property
		const tableView = Ti.UI.createTableView({
			data: [ section_0 ]
		});
		should(tableView).be.a.Object();
		should(tableView.apiName).be.a.String();
		should(tableView.apiName).be.eql('Ti.UI.TableView');

		// Validate tableView section count
		should(tableView.sectionCount).be.eql(1);

		// Append another section
		tableView.appendSection(section_1);

		// Validate tableView section count
		should(tableView.sectionCount).be.eql(2);
	});

	it('insertRowAfter', finish => {
		const tableView = Ti.UI.createTableView({
			data: [ { title: 'Red' } ]
		});

		win = Ti.UI.createWindow({
			backgroundColor: 'blue'
		});
		win.addEventListener('focus', () => {
			try {

				// Validate section count.
				should(tableView.sectionCount).be.eql(1);

				// Validate section exists.
				should(tableView.sections[0]).be.an.Object();

				// Validate section row count.
				should(tableView.sections[0].rowCount).be.eql(1);

				// Validate row in section.
				should(tableView.sections[0].rows[0].title).be.eql('Red');

				// Insert row using TableViewRow dictionary.
				tableView.insertRowAfter(0, { title: 'White' });

				// Validate section row count.
				should(tableView.sections[0].rowCount).be.eql(2);

				// Validate rows in section.
				should(tableView.sections[0].rows[0].title).be.eql('Red');
				should(tableView.sections[0].rows[1].title).be.eql('White');

				// Insert row using TableViewRow dictionary.
				tableView.insertRowAfter(0, { title: 'Purple' });

				// Validate section row count.
				should(tableView.sections[0].rowCount).be.eql(3);

				// Validate rows in section.
				should(tableView.sections[0].rows[0].title).be.eql('Red');
				should(tableView.sections[0].rows[1].title).be.eql('Purple');
				should(tableView.sections[0].rows[2].title).be.eql('White');

				// Insert row using TableViewRow instance.
				tableView.insertRowAfter(0, Ti.UI.createTableViewRow({ title: 'Blue' }));

				// Validate section row count.
				should(tableView.sections[0].rowCount).be.eql(4);

				// Validate rows in section.
				should(tableView.sections[0].rows[0].title).be.eql('Red');
				should(tableView.sections[0].rows[1].title).be.eql('Blue');
				should(tableView.sections[0].rows[2].title).be.eql('Purple');
				should(tableView.sections[0].rows[3].title).be.eql('White');

				finish();
			} catch (err) {
				return finish(err);
			}
		});

		win.add(tableView);
		win.open();
	});

	it('insertRowBefore', finish => {
		const tableView = Ti.UI.createTableView({
			data: [ { title: 'Red' } ]
		});

		win = Ti.UI.createWindow({
			backgroundColor: 'blue'
		});
		win.addEventListener('focus', () => {
			try {

				// Validate section count.
				should(tableView.sectionCount).be.eql(1);

				// Validate section exists.
				should(tableView.sections[0]).be.an.Object();

				// Validate section row count.
				should(tableView.sections[0].rowCount).be.eql(1);

				// Validate row in section.
				should(tableView.sections[0].rows[0].title).be.eql('Red');

				// Insert row using TableViewRow dictionary.
				tableView.insertRowBefore(0, { title: 'White' });

				// Validate section row count.
				should(tableView.sections[0].rowCount).be.eql(2);

				// Validate rows in section.
				should(tableView.sections[0].rows[0].title).be.eql('White');
				should(tableView.sections[0].rows[1].title).be.eql('Red');

				// Insert row using TableViewRow dictionary.
				tableView.insertRowBefore(1, { title: 'Purple' });

				// Validate section row count.
				should(tableView.sections[0].rowCount).be.eql(3);

				// Validate rows in section.
				should(tableView.sections[0].rows[0].title).be.eql('White');
				should(tableView.sections[0].rows[1].title).be.eql('Purple');
				should(tableView.sections[0].rows[2].title).be.eql('Red');

				// Insert row using TableViewRow instance.
				tableView.insertRowBefore(1, Ti.UI.createTableViewRow({ title: 'Blue' }));

				// Validate section row count.
				should(tableView.sections[0].rowCount).be.eql(4);

				// Validate rows in section.
				should(tableView.sections[0].rows[0].title).be.eql('White');
				should(tableView.sections[0].rows[1].title).be.eql('Blue');
				should(tableView.sections[0].rows[2].title).be.eql('Purple');
				should(tableView.sections[0].rows[3].title).be.eql('Red');

				finish();
			} catch (err) {
				return finish(err);
			}
		});

		win.add(tableView);
		win.open();
	});

	it('appendRow', finish => {
		const tableView = Ti.UI.createTableView({
			data: [ { title: 'Red' } ]
		});

		win = Ti.UI.createWindow({
			backgroundColor: 'blue'
		});
		win.addEventListener('focus', () => {
			try {

				// Validate section count.
				should(tableView.sectionCount).be.eql(1);

				// Validate section exists.
				should(tableView.sections[0]).be.an.Object();

				// Validate section row count.
				should(tableView.sections[0].rowCount).be.eql(1);

				// Validate row in section.
				should(tableView.sections[0].rows[0].title).be.eql('Red');

				// Append row using TableViewRow dictionary.
				tableView.appendRow({ title: 'White' });

				// Validate section row count.
				should(tableView.sections[0].rowCount).be.eql(2);

				// Validate rows in section.
				should(tableView.sections[0].rows[0].title).be.eql('Red');
				should(tableView.sections[0].rows[1].title).be.eql('White');

				// Append row using TableViewRow dictionary.
				tableView.appendRow({ title: 'Purple' });

				// Validate section row count.
				should(tableView.sections[0].rowCount).be.eql(3);

				// Validate rows in section.
				should(tableView.sections[0].rows[0].title).be.eql('Red');
				should(tableView.sections[0].rows[1].title).be.eql('White');
				should(tableView.sections[0].rows[2].title).be.eql('Purple');

				// Append row using TableViewRow instance.
				tableView.appendRow(Ti.UI.createTableViewRow({ title: 'Blue' }));

				// Validate section row count.
				should(tableView.sections[0].rowCount).be.eql(4);

				// Validate rows in section.
				should(tableView.sections[0].rows[0].title).be.eql('Red');
				should(tableView.sections[0].rows[1].title).be.eql('White');
				should(tableView.sections[0].rows[2].title).be.eql('Purple');
				should(tableView.sections[0].rows[3].title).be.eql('Blue');

				finish();
			} catch (err) {
				return finish(err);
			}
		});

		win.add(tableView);
		win.open();
	});

	it('appendRow (Array)', finish => {
		const tableView = Ti.UI.createTableView({
			data: [ { title: 'Red' } ]
		});

		win = Ti.UI.createWindow({
			backgroundColor: 'blue'
		});
		win.addEventListener('focus', () => {
			try {

				// Validate section count.
				should(tableView.sectionCount).be.eql(1);

				// Validate section exists.
				should(tableView.sections[0]).be.an.Object();

				// Validate section row count.
				should(tableView.sections[0].rowCount).be.eql(1);

				// Validate row in section.
				should(tableView.sections[0].rows[0].title).be.eql('Red');

				// Append rows using TableViewRow dictionary.
				tableView.appendRow([
					{ title: 'White' },
					{ title: 'Purple' }
				]);

				// Validate section row count.
				should(tableView.sections[0].rowCount).be.eql(3);

				// Validate rows in section.
				should(tableView.sections[0].rows[0].title).be.eql('Red');
				should(tableView.sections[0].rows[1].title).be.eql('White');
				should(tableView.sections[0].rows[2].title).be.eql('Purple');

				// Append rows using TableViewRow instance.
				tableView.appendRow([
					Ti.UI.createTableViewRow({ title: 'Blue' }),
					Ti.UI.createTableViewRow({ title: 'Green' })
				]);

				// Validate section row count.
				should(tableView.sections[0].rowCount).be.eql(5);

				// Validate rows in section.
				should(tableView.sections[0].rows[0].title).be.eql('Red');
				should(tableView.sections[0].rows[1].title).be.eql('White');
				should(tableView.sections[0].rows[2].title).be.eql('Purple');
				should(tableView.sections[0].rows[3].title).be.eql('Blue');
				should(tableView.sections[0].rows[4].title).be.eql('Green');

				finish();
			} catch (err) {
				return finish(err);
			}
		});

		win.add(tableView);
		win.open();
	});

	it('TableViewSection.add', finish => {
		const section = Ti.UI.createTableViewSection();
		const tableView = Ti.UI.createTableView({
			sections: [ section ]
		});

		win = Ti.UI.createWindow({
			backgroundColor: 'blue'
		});
		win.addEventListener('focus', () => {
			try {

				// Validate section count.
				should(tableView.sectionCount).be.eql(1);

				// Validate section exists.
				should(section).be.an.Object();

				// Add row to section using dictionary.
				// FIXME: iOS does not allow arrays or dictionary objects to be passed.
				section.add(Ti.UI.createTableViewRow({ title: 'Red' }));

				// Validate section row count.
				should(section.rowCount).be.eql(1);

				// Validate row in section.
				should(section.rows[0].title).be.eql('Red');

				// Add row to section using dictionary.
				section.add(Ti.UI.createTableViewRow({ title: 'White' }));

				// Validate section row count.
				should(section.rowCount).be.eql(2);

				// Validate rows in section.
				should(section.rows[0].title).be.eql('Red');
				should(section.rows[1].title).be.eql('White');

				// Add row to section using TableViewRow instance.
				section.add(Ti.UI.createTableViewRow({ title: 'Blue' }));

				// Validate section row count.
				should(section.rowCount).be.eql(3);

				// Validate rows in section.
				should(section.rows[0].title).be.eql('Red');
				should(section.rows[1].title).be.eql('White');
				should(section.rows[2].title).be.eql('Blue');

				finish();
			} catch (err) {
				return finish(err);
			}
		});

		win.add(tableView);
		win.open();
	});

	it('deleteRow', finish => {
		const tableView = Ti.UI.createTableView({
			data: [
				{ title: 'Red' },
				{ title: 'Green' },
				{ title: 'Blue' },
				{ title: 'Purple' }
			]
		});

		win = Ti.UI.createWindow({
			backgroundColor: 'blue'
		});
		win.addEventListener('focus', () => {
			try {

				// Validate section count.
				should(tableView.sectionCount).be.eql(1);

				// Validate section exists.
				should(tableView.sections[0]).be.an.Object();

				// Validate section row count.
				should(tableView.sections[0].rowCount).be.eql(4);

				// Validate rows in section.
				should(tableView.sections[0].rows[0].title).be.eql('Red');
				should(tableView.sections[0].rows[1].title).be.eql('Green');
				should(tableView.sections[0].rows[2].title).be.eql('Blue');
				should(tableView.sections[0].rows[3].title).be.eql('Purple');

				// Delete row using index.
				tableView.deleteRow(1);

				// Validate section row count.
				should(tableView.sections[0].rowCount).be.eql(3);

				// Validate rows in section.
				should(tableView.sections[0].rows[0].title).be.eql('Red');
				should(tableView.sections[0].rows[1].title).be.eql('Blue');
				should(tableView.sections[0].rows[2].title).be.eql('Purple');

				// Delete row using TableViewRow instance.
				tableView.deleteRow(tableView.sections[0].rows[2]);

				// Validate section row count.
				should(tableView.sections[0].rowCount).be.eql(2);

				// Validate rows in section.
				should(tableView.sections[0].rows[0].title).be.eql('Red');
				should(tableView.sections[0].rows[1].title).be.eql('Blue');

				finish();
			} catch (err) {
				return finish(err);
			}
		});

		win.add(tableView);
		win.open();
	});

	it('TableViewSection.remove', finish => {
		const section = Ti.UI.createTableViewSection();
		const tableView = Ti.UI.createTableView({
			sections: [ section ]
		});

		section.add(Ti.UI.createTableViewRow({ title: 'Red' }));
		section.add(Ti.UI.createTableViewRow({ title: 'Green' }));
		section.add(Ti.UI.createTableViewRow({ title: 'Blue' }));
		section.add(Ti.UI.createTableViewRow({ title: 'Purple' }));

		win = Ti.UI.createWindow({
			backgroundColor: 'blue'
		});
		win.addEventListener('focus', () => {
			try {

				// Validate section count.
				should(tableView.sectionCount).be.eql(1);

				// Validate section exists.
				should(section).be.an.Object();

				// Validate section row count.
				should(section.rowCount).be.eql(4);

				// Validate rows in section.
				should(section.rows[0].title).be.eql('Red');
				should(section.rows[1].title).be.eql('Green');
				should(section.rows[2].title).be.eql('Blue');
				should(section.rows[3].title).be.eql('Purple');

				// Remove row from section.
				section.remove(section.rows[1]);

				// Validate section row count.
				should(section.rowCount).be.eql(3);

				// Validate rows in section.
				should(section.rows[0].title).be.eql('Red');
				should(section.rows[1].title).be.eql('Blue');
				should(section.rows[2].title).be.eql('Purple');

				finish();
			} catch (err) {
				return finish(err);
			}
		});

		win.add(tableView);
		win.open();
	});

	it('updateRow', finish => {
		const section = Ti.UI.createTableViewSection();
		const tableView = Ti.UI.createTableView({
			sections: [ section ]
		});

		section.add(Ti.UI.createTableViewRow({ title: 'Red' }));
		section.add(Ti.UI.createTableViewRow({ title: 'Green' }));
		section.add(Ti.UI.createTableViewRow({ title: 'Blue' }));

		win = Ti.UI.createWindow({
			backgroundColor: 'blue'
		});
		win.addEventListener('focus', () => {
			try {

				// Validate section count.
				should(tableView.sectionCount).be.eql(1);

				// Validate section exists.
				should(tableView.sections[0]).be.eql(section);

				// Validate section row count.
				should(section.rowCount).be.eql(3);

				// Validate rows in section.
				should(section.rows[0].title).be.eql('Red');
				should(section.rows[1].title).be.eql('Green');
				should(section.rows[2].title).be.eql('Blue');

				// Update rows in section.
				tableView.updateRow(0, Ti.UI.createTableViewRow({ title: 'Green' }));
				tableView.updateRow(1, Ti.UI.createTableViewRow({ title: 'Blue' }));
				tableView.updateRow(2, Ti.UI.createTableViewRow({ title: 'Red' }));

				// Validate section row count.
				should(section.rowCount).be.eql(3);

				// Validate rows in section.
				should(section.rows[0].title).be.eql('Green');
				should(section.rows[1].title).be.eql('Blue');
				should(section.rows[2].title).be.eql('Red');

				finish();
			} catch (err) {
				return finish(err);
			}
		});

		win.add(tableView);
		win.open();
	});

	it('appendSection', finish => {
		const section_a = Ti.UI.createTableViewSection();
		const section_b = Ti.UI.createTableViewSection();
		const tableView = Ti.UI.createTableView({
			sections: [ section_a ]
		});

		section_a.add(Ti.UI.createTableViewRow({ title: 'Red' }));
		section_a.add(Ti.UI.createTableViewRow({ title: 'Green' }));
		section_a.add(Ti.UI.createTableViewRow({ title: 'Blue' }));

		section_b.add(Ti.UI.createTableViewRow({ title: 'Purple' }));
		section_b.add(Ti.UI.createTableViewRow({ title: 'Yellow' }));
		section_b.add(Ti.UI.createTableViewRow({ title: 'Orange' }));

		win = Ti.UI.createWindow({
			backgroundColor: 'blue'
		});
		win.addEventListener('focus', () => {
			try {

				// Validate section count.
				should(tableView.sectionCount).be.eql(1);

				// Validate section exists.
				should(tableView.sections[0]).be.eql(section_a);

				// Validate section row count.
				should(section_a.rowCount).be.eql(3);

				// Validate rows in section.
				should(section_a.rows[0].title).be.eql('Red');
				should(section_a.rows[1].title).be.eql('Green');
				should(section_a.rows[2].title).be.eql('Blue');

				// Append section to table.
				tableView.appendSection(section_b);

				// Validate section count.
				should(tableView.sectionCount).be.eql(2);

				// Validate section exists.
				should(tableView.sections[1]).be.eql(section_b);

				// Validate section row count.
				should(section_a.rowCount).be.eql(3);

				// Validate rows in section.
				should(section_b.rows[0].title).be.eql('Purple');
				should(section_b.rows[1].title).be.eql('Yellow');
				should(section_b.rows[2].title).be.eql('Orange');

				finish();
			} catch (err) {
				return finish(err);
			}
		});

		win.add(tableView);
		win.open();
	});

	it('deleteSection', finish => {
		const section_a = Ti.UI.createTableViewSection();
		const section_b = Ti.UI.createTableViewSection();
		const tableView = Ti.UI.createTableView({
			sections: [ section_a, section_b ]
		});

		section_a.add(Ti.UI.createTableViewRow({ title: 'Red' }));
		section_a.add(Ti.UI.createTableViewRow({ title: 'Green' }));
		section_a.add(Ti.UI.createTableViewRow({ title: 'Blue' }));

		section_b.add(Ti.UI.createTableViewRow({ title: 'Purple' }));
		section_b.add(Ti.UI.createTableViewRow({ title: 'Yellow' }));
		section_b.add(Ti.UI.createTableViewRow({ title: 'Orange' }));

		win = Ti.UI.createWindow({
			backgroundColor: 'blue'
		});
		win.addEventListener('focus', () => {
			try {
				// Validate section count.
				should(tableView.sectionCount).be.eql(2);

				// Validate sections exists.
				should(tableView.sections[0]).be.eql(section_a);
				should(tableView.sections[1]).be.eql(section_b);

				// Delete section from table.
				tableView.deleteSection(0);

				// Validate section count.
				should(tableView.sectionCount).be.eql(1);

				// Validate section exists.
				should(tableView.sections[0]).be.eql(section_b);

				// Delete section from table.
				tableView.deleteSection(0);

				// Validate section count.
				should(tableView.sectionCount).be.eql(0);

				finish();
			} catch (err) {
				return finish(err);
			}
		});

		win.add(tableView);
		win.open();
	});

	it('updateSection', finish => {
		const section_a = Ti.UI.createTableViewSection();
		const section_b = Ti.UI.createTableViewSection();
		const tableView = Ti.UI.createTableView({
			sections: [ section_a ]
		});

		section_a.add(Ti.UI.createTableViewRow({ title: 'Red' }));
		section_a.add(Ti.UI.createTableViewRow({ title: 'Green' }));
		section_a.add(Ti.UI.createTableViewRow({ title: 'Blue' }));

		section_b.add(Ti.UI.createTableViewRow({ title: 'Purple' }));
		section_b.add(Ti.UI.createTableViewRow({ title: 'Yellow' }));
		section_b.add(Ti.UI.createTableViewRow({ title: 'Orange' }));

		win = Ti.UI.createWindow({
			backgroundColor: 'blue'
		});
		win.addEventListener('focus', () => {
			try {
				// Validate section count.
				should(tableView.sectionCount).be.eql(1);

				// Validate section exists.
				should(tableView.sections[0]).be.a.Object();

				// Validate section row count.
				should(tableView.sections[0].rowCount).be.eql(3);

				// Validate rows in section.
				should(tableView.sections[0].rows[0].title).be.eql('Red');
				should(tableView.sections[0].rows[1].title).be.eql('Green');
				should(tableView.sections[0].rows[2].title).be.eql('Blue');

				// Update section in table.
				tableView.updateSection(0, section_b);

				// Validate section count.
				should(tableView.sectionCount).be.eql(1);

				// Validate section exists.
				should(tableView.sections[0]).be.a.Object();

				// Validate section row count.
				should(tableView.sections[0].rowCount).be.eql(3);

				// Validate rows in section.
				should(tableView.sections[0].rows[0].title).be.eql('Purple');
				should(tableView.sections[0].rows[1].title).be.eql('Yellow');
				should(tableView.sections[0].rows[2].title).be.eql('Orange');

				finish();
			} catch (err) {
				return finish(err);
			}
		});

		win.add(tableView);
		win.open();
	});

	it('insertSectionAfter', finish => {
		const section_a = Ti.UI.createTableViewSection();
		const section_b = Ti.UI.createTableViewSection();
		const section_c = Ti.UI.createTableViewSection();
		const tableView = Ti.UI.createTableView({
			sections: [ section_a, section_c ]
		});

		section_a.add(Ti.UI.createTableViewRow({ title: 'Red' }));
		section_a.add(Ti.UI.createTableViewRow({ title: 'Green' }));
		section_a.add(Ti.UI.createTableViewRow({ title: 'Blue' }));

		section_b.add(Ti.UI.createTableViewRow({ title: 'Purple' }));
		section_b.add(Ti.UI.createTableViewRow({ title: 'Yellow' }));
		section_b.add(Ti.UI.createTableViewRow({ title: 'Orange' }));

		section_c.add(Ti.UI.createTableViewRow({ title: 'Magenta' }));
		section_c.add(Ti.UI.createTableViewRow({ title: 'Cyan' }));
		section_c.add(Ti.UI.createTableViewRow({ title: 'Pink' }));

		win = Ti.UI.createWindow({
			backgroundColor: 'blue'
		});
		win.addEventListener('focus', () => {
			try {
				// Validate section count.
				should(tableView.sectionCount).be.eql(2);

				// Validate sections exists.
				should(tableView.sections[0]).be.a.Object();
				should(tableView.sections[1]).be.a.Object();

				// Validate section row count.
				should(tableView.sections[0].rowCount).be.eql(3);
				should(tableView.sections[1].rowCount).be.eql(3);

				// Validate rows in section.
				should(tableView.sections[0].rows[0].title).be.eql('Red');
				should(tableView.sections[0].rows[1].title).be.eql('Green');
				should(tableView.sections[0].rows[2].title).be.eql('Blue');
				should(tableView.sections[1].rows[0].title).be.eql('Magenta');
				should(tableView.sections[1].rows[1].title).be.eql('Cyan');
				should(tableView.sections[1].rows[2].title).be.eql('Pink');

				// Update section in table.
				tableView.insertSectionAfter(0, section_b);

				// Validate section count.
				should(tableView.sectionCount).be.eql(3);

				// Validate section exists.
				should(tableView.sections[0]).be.a.Object();
				should(tableView.sections[1]).be.a.Object();
				should(tableView.sections[2]).be.a.Object();

				// Validate section row count.
				should(tableView.sections[0].rowCount).be.eql(3);
				should(tableView.sections[1].rowCount).be.eql(3);
				should(tableView.sections[2].rowCount).be.eql(3);

				// Validate rows in section.
				should(tableView.sections[0].rows[0].title).be.eql('Red');
				should(tableView.sections[0].rows[1].title).be.eql('Green');
				should(tableView.sections[0].rows[2].title).be.eql('Blue');
				should(tableView.sections[1].rows[0].title).be.eql('Purple');
				should(tableView.sections[1].rows[1].title).be.eql('Yellow');
				should(tableView.sections[1].rows[2].title).be.eql('Orange');
				should(tableView.sections[2].rows[0].title).be.eql('Magenta');
				should(tableView.sections[2].rows[1].title).be.eql('Cyan');
				should(tableView.sections[2].rows[2].title).be.eql('Pink');

				finish();
			} catch (err) {
				return finish(err);
			}
		});

		win.add(tableView);
		win.open();
	});

	it('insertSectionBefore', finish => {
		const section_a = Ti.UI.createTableViewSection();
		const section_b = Ti.UI.createTableViewSection();
		const section_c = Ti.UI.createTableViewSection();
		const tableView = Ti.UI.createTableView({
			sections: [ section_a, section_c ]
		});

		// FIXME: iOS cannot handle array input OR dictionary input.
		/* section_a.add([
			{ title: 'Red' },
			{ title: 'Green' },
			{ title: 'Blue' }
		]);

		section_b.add([
			{ title: 'Purple' },
			{ title: 'Yellow' },
			{ title: 'Orange' }
		]);

		section_c.add([
			{ title: 'Magenta' },
			{ title: 'Cyan' },
			{ title: 'Pink' }
		]); */

		section_a.add(Ti.UI.createTableViewRow({ title: 'Red' }));
		section_a.add(Ti.UI.createTableViewRow({ title: 'Green' }));
		section_a.add(Ti.UI.createTableViewRow({ title: 'Blue' }));

		section_b.add(Ti.UI.createTableViewRow({ title: 'Purple' }));
		section_b.add(Ti.UI.createTableViewRow({ title: 'Yellow' }));
		section_b.add(Ti.UI.createTableViewRow({ title: 'Orange' }));

		section_c.add(Ti.UI.createTableViewRow({ title: 'Magenta' }));
		section_c.add(Ti.UI.createTableViewRow({ title: 'Cyan' }));
		section_c.add(Ti.UI.createTableViewRow({ title: 'Pink' }));

		win = Ti.UI.createWindow({
			backgroundColor: 'blue'
		});
		win.addEventListener('focus', () => {
			try {

				// Validate section count.
				should(tableView.sectionCount).be.eql(2);

				// Validate sections exists.
				should(tableView.sections[0]).be.a.Object();
				should(tableView.sections[1]).be.a.Object();

				// Validate section row count.
				should(tableView.sections[0].rowCount).be.eql(3);
				should(tableView.sections[1].rowCount).be.eql(3);

				// Validate rows in section.
				should(tableView.sections[0].rows[0].title).be.eql('Red');
				should(tableView.sections[0].rows[1].title).be.eql('Green');
				should(tableView.sections[0].rows[2].title).be.eql('Blue');

				should(tableView.sections[1].rows[0].title).be.eql('Magenta');
				should(tableView.sections[1].rows[1].title).be.eql('Cyan');
				should(tableView.sections[1].rows[2].title).be.eql('Pink');

				// Update section in table.
				tableView.insertSectionBefore(1, section_b);

				// Validate section count.
				should(tableView.sectionCount).be.eql(3);

				// Validate section exists.
				should(tableView.sections[0]).be.a.Object();
				should(tableView.sections[1]).be.a.Object();
				should(tableView.sections[2]).be.a.Object();

				// Validate section row count.
				should(tableView.sections[0].rowCount).be.eql(3);
				should(tableView.sections[1].rowCount).be.eql(3);
				should(tableView.sections[2].rowCount).be.eql(3);

				// Validate rows in section.
				should(tableView.sections[0].rows[0].title).be.eql('Red');
				should(tableView.sections[0].rows[1].title).be.eql('Green');
				should(tableView.sections[0].rows[2].title).be.eql('Blue');

				should(tableView.sections[1].rows[0].title).be.eql('Purple');
				should(tableView.sections[1].rows[1].title).be.eql('Yellow');
				should(tableView.sections[1].rows[2].title).be.eql('Orange');

				should(tableView.sections[2].rows[0].title).be.eql('Magenta');
				should(tableView.sections[2].rows[1].title).be.eql('Cyan');
				should(tableView.sections[2].rows[2].title).be.eql('Pink');

				finish();
			} catch (err) {
				return finish(err);
			}
		});

		win.add(tableView);
		win.open();
	});

	// Verifies that we don't run into the JNI ref overflow issue on Android
	// NOTE: skipping due to memory constrains on our Android 4.4 test device
	it.skip('TIMOB-15765 rev.1', finish => { // eslint-disable-line mocha/no-skipped-tests
		var views = [],
			references = 51200, // JNI max is 51200
			error,
			i;

		// create references
		for (i = 0; i < references; i++) {
			views.push(Ti.UI.createView());

			if (!(i % Math.floor(references / 10))) {
				Ti.API.info('creating references... ' + i + '/' + references);
			}
		}

		// validate references
		try {
			should(views.length).be.eql(references);

			for (i = 0; i < references; i++) {
				should(views[i]).not.be.undefined();
				should(views[i]).be.an.Object();
			}

			Ti.API.info('success, created ' + references + ' references!');
		} catch (e) {
			error = e;
		}
		finish(error);
	});

	// NOTE: skipping due to memory constrains on our Android 4.4 test device
	it.skip('TIMOB-15765 rev.2', finish => { // eslint-disable-line mocha/no-skipped-tests
		var references = 51200, // JNI max is 51200
			error,
			blob,
			i;

		// create references
		try {
			for (i = 0; i < references; i++) {
				blob = Ti.createBuffer({ length: 1 }).toBlob();

				should(blob).not.be.undefined();
				should(blob).be.an.Object();

				if (!(i % Math.floor(references / 10))) {
					Ti.API.info('creating temporary references... ' + i + '/' + references);
				}
			}
			Ti.API.info('success!');
		} catch (e) {
			error = e;
		}
		finish(error);
	});

	it.ios('Delete row (Search Active)', finish => {
		var section_0,
			searchBar,
			tableView,
			isFocused;
		win = Ti.UI.createWindow({ backgroundColor: 'blue' });
		section_0 = Ti.UI.createTableViewSection({ headerTitle: 'Zero' });
		section_0.add(Ti.UI.createTableViewRow({ title: 'Red' }));
		section_0.add(Ti.UI.createTableViewRow({ title: 'White' }));
		section_0.add(Ti.UI.createTableViewRow({ title: 'Purple' }));

		searchBar = Ti.UI.createSearchBar({ showCancel: true });
		tableView = Ti.UI.createTableView({
			data: [ section_0 ],
			search: searchBar
		});

		isFocused = false;

		win.addEventListener('focus', () => {
			var error;

			if (isFocused) {
				return;
			}
			isFocused = true;

			try {
				searchBar.value = 'e';
				searchBar.focus();
				should(tableView.sections[0].rowCount).be.eql(3);
				tableView.deleteRow(0);
				should(tableView.sections[0].rowCount).be.eql(2);
			} catch (err) {
				error = err;
			}
			setTimeout(() => {
				finish(error);
			}, 1000);
		});

		win.add(tableView);
		win.open();
	});

	it('set and clear data', finish => {
		const data_a = [
			{ title: 'Square', backgroundSelectedColor: 'red' },
			{ title: 'Circle', backgroundSelectedColor: 'blue' },
			{ title: 'Triangle', backgroundSelectedColor: 'purple' }
		];
		const data_b = [
			{ title: 'Red', backgroundSelectedColor: 'red' },
			{ title: 'Green', backgroundSelectedColor: 'green' },
			{ title: 'Blue', backgroundSelectedColor: 'blue' }
		];
		const tableView = Ti.UI.createTableView();

		try {
			tableView.data = [];
			tableView.data = data_a;
			tableView.data = [];
			tableView.data = data_b;
			tableView.data = [];
			tableView.data = data_a;
		} catch (e) {
			finish(e);
		}
		finish();
	});

	it('scrollable', () => {
		const tableView = Ti.UI.createTableView({ scrollable: false });

		should(tableView.scrollable).be.be.false();
		tableView.scrollable = !tableView.scrollable;
		should(tableView.scrollable).be.be.true();
	});

	it('separatorStyle', () => {
		const section = Ti.UI.createTableViewSection();

		section.add(Ti.UI.createTableViewRow({ title: 'Red' }));
		section.add(Ti.UI.createTableViewRow({ title: 'White' }));

		const tableView = Ti.UI.createTableView({
			data: [ section ],
			separatorStyle: Ti.UI.TABLE_VIEW_SEPARATOR_STYLE_SINGLE_LINE
		});
		should(tableView.separatorStyle).eql(Ti.UI.TABLE_VIEW_SEPARATOR_STYLE_SINGLE_LINE);
		tableView.separatorStyle = Ti.UI.TABLE_VIEW_SEPARATOR_STYLE_NONE;
		should(tableView.separatorStyle).eql(Ti.UI.TABLE_VIEW_SEPARATOR_STYLE_NONE);
	});

	it('separatorColor', () => {
		const section = Ti.UI.createTableViewSection();

		section.add(Ti.UI.createTableViewRow({ title: 'Red' }));
		section.add(Ti.UI.createTableViewRow({ title: 'White' }));

		const tableView = Ti.UI.createTableView({
			data: [ section ],
			separatorColor: 'red'
		});
		should(tableView.separatorColor).eql('red');
		tableView.separatorColor = 'blue';
		should(tableView.separatorColor).eql('blue');
	});

	it.ios('resultsBackgroundColor', () => {
		const section = Ti.UI.createTableViewSection();

		section.add(Ti.UI.createTableViewRow({ title: 'Red' }));
		section.add(Ti.UI.createTableViewRow({ title: 'White' }));

		const tableView = Ti.UI.createTableView({
			data: [ section ],
			resultsBackgroundColor: 'red'
		});
		should(tableView.resultsBackgroundColor).eql('red');
		tableView.resultsBackgroundColor = 'blue';
		should(tableView.resultsBackgroundColor).eql('blue');
	});

	it.ios('resultsSeparatorColor', () => {
		const section = Ti.UI.createTableViewSection();

		section.add(Ti.UI.createTableViewRow({ title: 'Red' }));
		section.add(Ti.UI.createTableViewRow({ title: 'White' }));

		const tableView = Ti.UI.createTableView({
			data: [ section ],
			resultsSeparatorColor: 'red'
		});
		should(tableView.resultsSeparatorColor).eql('red');
		tableView.resultsSeparatorColor = 'blue';
		should(tableView.resultsSeparatorColor).eql('blue');
	});

	it.ios('resultsSeparatorStyle', () => {
		const section = Ti.UI.createTableViewSection();

		section.add(Ti.UI.createTableViewRow({ title: 'Red' }));
		section.add(Ti.UI.createTableViewRow({ title: 'White' }));

		const tableView = Ti.UI.createTableView({
			data: [ section ],
			resultsSeparatorStyle: Ti.UI.TABLE_VIEW_SEPARATOR_STYLE_SINGLE_LINE
		});
		should(tableView.resultsSeparatorStyle).eql(Ti.UI.TABLE_VIEW_SEPARATOR_STYLE_SINGLE_LINE);
		tableView.resultsSeparatorStyle = Ti.UI.TABLE_VIEW_SEPARATOR_STYLE_NONE;
		should(tableView.resultsSeparatorStyle).eql(Ti.UI.TABLE_VIEW_SEPARATOR_STYLE_NONE);
	});

	it('refreshControl', finish => {
		win = Ti.UI.createWindow();

		const refreshControl = Ti.UI.createRefreshControl();

		refreshControl.addEventListener('refreshstart', () => {
			setTimeout(() => {
				refreshControl.endRefreshing();
			}, 1000);
		});
		refreshControl.addEventListener('refreshend', () => {
			finish();
		});

		win.addEventListener('open', function () {
			refreshControl.beginRefreshing();
		});

		win.add(Ti.UI.createListView({ refreshControl: refreshControl }));
		win.open();
	});

	it('Add and remove headerView/footerView ', finish => {
		win = Ti.UI.createWindow({ backgroundColor: 'gray' });

		const headerView = Ti.UI.createView({
			backgroundColor: 'red',
			height: 100,
			width: Ti.UI.FILL
		});
		const footerView = Ti.UI.createView({
			backgroundColor: 'blue',
			height: 100,
			width: Ti.UI.FILL
		});
		const tableView = Ti.UI.createTableView({
			headerView,
			footerView,
			data: [ { title: 'ROW' } ]
		});

		win.addEventListener('focus', () => {
			try {
				should(tableView.headerView).not.be.null();
				should(tableView.footerView).not.be.null();

				tableView.headerView = null;
				tableView.footerView = null;

				should(tableView.headerView).be.null();
				should(tableView.footerView).be.null();

				finish();
			} catch (err) {
				return finish(err);
			}
		});

		win.add(tableView);
		win.open();
	});

	it('TIMOB-26164: updateRow + insertRowAfter causing crash on main thread', finish => {
		win = Ti.UI.createWindow({ backgroundColor: 'blue' });

		const tableView = Ti.UI.createTableView({
			data: [ { title: 'Red' } ]
		});

		win.addEventListener('focus', () => {
			try {
				tableView.updateRow(0, { title: 'Green' });
				tableView.insertRowAfter(0, { title: 'White' });

				// Validate section row count.
				should(tableView.sections[0].rowCount).be.eql(2);

				// Validate section rows.
				should(tableView.sections[0].rows[0].title).be.eql('Green');
				should(tableView.sections[0].rows[1].title).be.eql('White');

				finish();
			} catch (err) {
				return finish(err);
			}
		});

		win.add(tableView);
		win.open();
	});

	it.android('SearchView persistence', finish => {
		win = Ti.UI.createWindow();

		const	tableData = [ { title: 'Apples' }, { title: 'Bananas' }, { title: 'Carrots' }, { title: 'Potatoes' } ];
		const searchView = Ti.UI.Android.createSearchView();
		const table = Ti.UI.createTableView({
			height: '80%',
			search: searchView,
			data: tableData
		});

		function removeAndAddTable() {
			try {
				table.removeEventListener('postlayout', removeAndAddTable);
				win.remove(table);
				win.add(table);
				finish();
			} catch (err) {
				finish(err);
			}
		}

		table.addEventListener('postlayout', removeAndAddTable);
		win.add(table);
		win.open();
	});

	it('row.color row.backgroundColor', finish => {
		win = Ti.UI.createWindow();

		const section = Ti.UI.createTableViewSection();
		const tableView = Ti.UI.createTableView({ data: [ section ] });
		const row_a = Ti.UI.createTableViewRow({
			title: 'Row A',
			color: 'white',
			backgroundColor: 'blue'
		});
		const row_b = Ti.UI.createTableViewRow({
			title: 'Row B',
			color: 'black',
			backgroundColor: 'yellow'
		});

		section.add(row_a);
		section.add(row_b);

		win.addEventListener('open', () => {

			// Change row color values.
			row_a.color = 'red';
			row_a.backgroundColor = 'white';
			row_b.color = 'white';
			row_b.backgroundColor = 'purple';

			// Validate new row color values.
			should(row_a.color).be.eql('red');
			should(row_a.backgroundColor).be.eql('white');
			should(row_b.color).be.eql('white');
			should(row_b.backgroundColor).be.eql('purple');

			finish();
		});

		win.add(tableView);
		win.open();
	});

	it('row custom properties', () => {
		const tableView = Ti.UI.createTableView({
			data: [
				Ti.UI.createTableViewRow({ title: 'Row 1', myNumber: 1 }),
				{ title: 'Row 2', myNumber: 2 }
			]
		});
		tableView.appendRow(Ti.UI.createTableViewRow({ title: 'Row 3', myNumber: 3 }));
		const sectionRows = tableView.sections[0].rows;
		should(sectionRows[0].myNumber).be.eql(1);
		should(sectionRows[1].myNumber).be.eql(2);
		should(sectionRows[2].myNumber).be.eql(3);

		const row = sectionRows[0];
		row.myNumber = 10;
		tableView.updateRow(0, row);
		should(tableView.sections[0].rows[0].myNumber).be.eql(10);
	});

	it('row.getViewById()', finish => {
		const section1 = Ti.UI.createTableViewSection({ headerTitle: 'My Section' });
		for (let index = 1; index <= 3; index++) {
			const row = Ti.UI.createTableViewRow();
			row.add(Ti.UI.createLabel({ text: `Row ${index}`, id: 'myLabelId' }));
			section1.add(row);
		}
		const tableView = Ti.UI.createTableView({
			data: [ section1 ]
		});
		win = Ti.UI.createWindow();
		win.add(tableView);
		win.addEventListener('open', () => {
			try {
				for (let index = 1; index <= 3; index++) {
					const row = tableView.sections[0].rows[index - 1];
					const view = row.getViewById('myLabelId');
					should(view).be.a.Object();
					should(view.apiName).be.eql('Ti.UI.Label');
					should(view.text).be.eql(`Row ${index}`);
				}
				finish();
			} catch (err) {
				finish(err);
			}
		});
		win.open();
	});

	it('row.className', finish => {
		const section1 = Ti.UI.createTableViewSection({ headerTitle: 'Section 1' });
		for (let index = 1; index <= 3; index++) {
			const row = Ti.UI.createTableViewRow({ className: 'rowType1' });
			row.add(Ti.UI.createLabel({ text: `Row ${index}`, left: 20 }));
			section1.add(row);
		}
		const section2 = Ti.UI.createTableViewSection({ headerTitle: 'Section 2' });
		for (let index = 1; index <= 3; index++) {
			const row = Ti.UI.createTableViewRow({ className: 'rowType2' });
			row.add(Ti.UI.createLabel({ text: `Row ${index}`, right: 20 }));
			section1.add(row);
		}

		win = Ti.UI.createWindow();
		win.add(Ti.UI.createTableView({
			data: [ section1, section2 ]
		}));
		win.addEventListener('open', () => {
			finish();
		});
		win.open();
	});

	it.iosBroken('resize row with Ti.UI.SIZE on content height change', finish => {
		win = Ti.UI.createWindow({ backgroundColor: 'blue' });

		const heights = [ 100, 200, 50 ];
		const tableView = Ti.UI.createTableView({});
		const row = Ti.UI.createTableViewRow({
			height: Ti.UI.SIZE,
			width: Ti.UI.FILL
		});
		const view = Ti.UI.createView({
			height: heights.pop(),
			backgroundColor: 'red'
		});

		row.add(view);

		tableView.data = [ row ];

		tableView.addEventListener('postlayout', function onPostLayout() {
			console.log('postlayout', row.rect.height, view.rect.height);
			should(row.rect.height).be.eql(view.rect.height);

			if (!heights.length) {
				tableView.removeEventListener('postlayout', onPostLayout);
				finish();
			}
			view.height = heights.pop();
		});

		win.add(tableView);
		win.open();
	});

	it('row#rect', function (finish) {
		if (isCI && utilities.isMacOS()) { // FIXME: On macOS CI (maybe < 10.15.6?), times out! Does app need explicit focus added?
			return finish(); // FIXME: skip when we move to official mocha package
		}

		win = Ti.UI.createWindow();

		const tableView = Ti.UI.createTableView();
		const row = Ti.UI.createTableViewRow({
			height: Ti.UI.SIZE,
			width: Ti.UI.FILL
		});
		const view = Ti.UI.createView({
			height: 150,
			backgroundColor: 'yellow'
		});

		row.add(view);

		tableView.data = [ row ];

		// FIXME: Times out on ios?
		row.addEventListener('postlayout', () => {
			try {
				should(row.rect.height).be.eql(150);
			} catch (e) {
				return finish(e);
			}
			finish();
		});

		win.add(tableView);
		win.open();
	});

	it('rows with vertical or horizontal layout', finish => {
		win = Ti.UI.createWindow();

		const data = [];

		for (var index = 1; index <= 20; index++) {
			let layout = 'vertical';
			if (index > 10) {
				layout = 'horizontal';
			}
			const row = Ti.UI.createTableViewRow({ layout });
			row.add(Ti.UI.createLabel({ text: `Row ${index}` }));
			data.push(row);
		}

		const table = Ti.UI.createTableView({ data });

		win.addEventListener('postlayout', function addTableView() {
			win.removeEventListener('postlayout', addTableView);
			try {
				// After adding table, app should not crash
				win.add(table);
			} catch (err) {
				return finish(err);
			}
			finish();
		});
		win.open();
	});

	it('TIMOB-28148: adding view on row causing crash', finish => {
		const row = Ti.UI.createTableViewRow({ title: 'click me' });
		const tableView = Ti.UI.createTableView({
			data: [ row ]
		});

		win = Ti.UI.createWindow({
			backgroundColor: 'blue'
		});
		win.addEventListener('open', function () {
			setTimeout(function () {
				try {
					const label = Ti.UI.createLabel({ text: 'REQUIRED' });
					row.add(label);
					finish();
				} catch (err) {
					return finish(err);
				}
			}, 2000);
		});

		win.add(tableView);
		win.open();
	});

	it('TableViewRow scaling (percent)', function () {
		// FIXME: Does not honour scale correctly on macOS: https://jira.appcelerator.org/browse/TIMOB-28261
		if (isCI && utilities.isMacOS() && OS_VERSION_MAJOR < 11) {
			this.skip();
			return;
		}

		const view = Ti.UI.createView({
			width: '540px',
			height: '960px'
		});
		const tableView = Ti.UI.createTableView({
			height: '50%',
			backgroundColor: 'white'
		});
		const row = Ti.UI.createTableViewRow({
			height: '50%',
			backgroundColor: 'blue'
		});

		tableView.data = [ row ];

		view.add(tableView);

		// TableViewRow should fill 50% of its parent TableView.
		should(view).matchImage('snapshots/tableViewRowScaling_percent.png', { maxPixelMismatch: OS_IOS ? 2 : 0 }); // 2 pixels differ on actual iPhone
	});

	it('TableViewRow scaling (FILL)', function () {
		// FIXME: Does not honour scale correctly on macOS: https://jira.appcelerator.org/browse/TIMOB-28261
		if (isCI && utilities.isMacOS() && OS_VERSION_MAJOR < 11) {
			this.skip();
			return;
		}

		const view = Ti.UI.createView({
			width: '540px',
			height: '960px'
		});
		const tableView = Ti.UI.createTableView({
			height: '50%',
			backgroundColor: 'white'
		});
		const row = Ti.UI.createTableViewRow({
			height: Ti.UI.FILL,
			backgroundColor: 'blue'
		});

		tableView.data = [ row ];

		view.add(tableView);

		// TableViewRow should fill 50% of its parent TableView.
		should(view).matchImage('snapshots/tableViewRowScaling_fill.png', { maxPixelMismatch: OS_IOS ? 8 : 0 }); // 8 pixels differ on actual iPhone
	});

	it('TableViewRow internal icons', function () {
		// FIXME: Does not honour scale correctly on macOS: https://jira.appcelerator.org/browse/TIMOB-28261
		if (isCI && utilities.isMacOS() && OS_VERSION_MAJOR < 11) {
			this.skip();
			return;
		}

		const view = Ti.UI.createView({
			width: '540px',
			height: '960px'
		});
		const tableView = Ti.UI.createTableView({
			backgroundColor: 'white'
		});

		tableView.data = [
			{ hasCheck: true },
			{ hasChild: true },
			{ hasDetail: true }
		];

		view.add(tableView);

		// TableView should display rows of internal icons.
		should(view).matchImage('snapshots/tableViewRow_icons.png', {
			maxPixelMismatch: OS_IOS ? 378 : 0 // iPhoen XR differs by 378 pixels
		});
	});

	// FIXME: Unsupported on iOS.
	it.iosBroken('TableViewRow borderRadius', function () {
		// FIXME: Does not honour scale correctly on macOS: https://jira.appcelerator.org/browse/TIMOB-28261
		if (isCI && utilities.isMacOS() && OS_VERSION_MAJOR < 11) {
			this.skip();
			return;
		}

		const view = Ti.UI.createView({
			width: '540px',
			height: '960px'
		});
		const tableView = Ti.UI.createTableView({
			backgroundColor: 'white'
		});

		tableView.data = [
			{
				backgroundColor: 'blue',
				height: '64px',
				borderRadius: '16px'
			}
		];

		view.add(tableView);

		// TableView should display rows of internal icons.
		should(view).matchImage('snapshots/tableViewRow_borderRadius.png');
	});

	it('TableViewRow default title & image', function () {
		// FIXME: Does not honour scale correctly on macOS: https://jira.appcelerator.org/browse/TIMOB-28261
		if (isCI && utilities.isMacOS() && OS_VERSION_MAJOR < 11) {
			this.skip();
			return;
		}

		const view = Ti.UI.createView({
			width: '540px',
			height: '960px'
		});
		const tableView = Ti.UI.createTableView({
			backgroundColor: 'blue'
		});

		tableView.data = [
			{
				title: 'Default Title',
				image: '/Logo.png'
			}
		];

		view.add(tableView);

		// TableView should display rows of internal icons.
		should(view).matchImage('snapshots/tableViewRow_default.png', {
			maxPixelMismatch: OS_IOS ? 380 : 0 // iphone XR differs by 380 pixels
		});
	});

	it('TableView headerTitle & footerTitle', function () {
		// FIXME: Does not honour scale correctly on macOS: https://jira.appcelerator.org/browse/TIMOB-28261
		if (isCI && utilities.isMacOS() && OS_VERSION_MAJOR < 11) {
			this.skip();
			return;
		}

		const view = Ti.UI.createView({
			width: '540px',
			height: '960px'
		});
		const tableView = Ti.UI.createTableView({
			headerTitle: 'Header',
			footerTitle: 'Footer',
			backgroundColor: 'white'
		});

		tableView.data = [ { title: 'Row', color: 'black' } ];

		view.add(tableView);

		// TableView should display rows of internal icons.
		should(view).matchImage('snapshots/tableView_headerTitle_footerTitle.png', {
			maxPixelMismatch: OS_IOS ? 290 : 0 // iphone differs by 290
		});
	});

	// FIXME: For an unknown reason, this test causes an 'signal error code: 11' exception on iOS
	// shortly after running successfully.
	it.iosBroken('TableView headerView & footerView', function () {
		// FIXME: Does not honour scale correctly on macOS: https://jira.appcelerator.org/browse/TIMOB-28261
		if (isCI && utilities.isMacOS() && OS_VERSION_MAJOR < 11) {
			this.skip();
			return;
		}

		const view = Ti.UI.createView({
			width: '540px',
			height: '960px'
		});
		const headerView = Ti.UI.createView({
			backgroundColor: 'red',
			height: '80px'
		});
		const footerView = Ti.UI.createView({
			backgroundColor: 'blue',
			height: '80px'
		});
		const tableView = Ti.UI.createTableView({
			headerView,
			footerView,
			backgroundColor: 'white'
		});

		tableView.data = [ { title: 'Row', color: 'black' } ];

		view.add(tableView);

		// TableView should display rows of internal icons.
		const options = {
			threshold: 0.1,
			// only for certain versions of Android? This happens on 5.x, what about 6-10?
			maxPixelMismatch: (OS_ANDROID && OS_VERSION_MAJOR < 6) ? 2224 : 0
		};
		should(view).matchImage('snapshots/tableView_header_footer.png', options);
	});

	// FIXME: For an unknown reason, this test causes an 'signal error code: 11' exception on iOS
	// shortly after running successfully.
	it.iosBroken('TableView + TableViewSection headerView & footerView', function () {
		// FIXME: Does not honour scale correctly on macOS: https://jira.appcelerator.org/browse/TIMOB-28261
		if (isCI && utilities.isMacOS() && OS_VERSION_MAJOR < 11) {
			this.skip();
			return;
		}

		const view = Ti.UI.createView({
			width: '540px',
			height: '960px'
		});
		const headerView = Ti.UI.createView({
			backgroundColor: 'red',
			height: '80px'
		});
		const footerView = Ti.UI.createView({
			backgroundColor: 'blue',
			height: '80px'
		});
		const tableView = Ti.UI.createTableView({
			headerView,
			footerView,
			backgroundColor: 'white'
		});

		tableView.data = [
			Ti.UI.createTableViewSection({
				headerTitle: 'TableViewSection.headerTitle',
				footerTitle: 'TableViewSection.footerTitle'
			})
		];

		view.add(tableView);

		// TableView should display rows of internal icons.
		const options = {
			threshold: 0.1,
			// only for certain versions of Android? This happens on 5.x, what about 6-10?
			maxPixelMismatch: (OS_ANDROID && OS_VERSION_MAJOR < 6) ? 3757 : 0
		};
		should(view).matchImage('snapshots/tableView_tableViewSection_header_footer.png', options);
	});

	it.ios('All text should show if TableView.style is .INSET_GROUPED ', () => {
		// FIXME: Does not honour scale correctly on macOS: https://jira.appcelerator.org/browse/TIMOB-28261
		if (utilities.isMacOS() && OS_VERSION_MAJOR < 11) {
			return;
		}

		const view = Ti.UI.createView({
			width: '540px',
			height: '960px'
		});
		const tableView = Ti.UI.createTableView({
			backgroundColor: 'white',
			style: Titanium.UI.iOS.TableViewStyle.INSET_GROUPED
		});
		const row = Ti.UI.createTableViewRow({
			backgroundColor: 'blue'
		});

		const label = Ti.UI.createLabel({ text: 'Lorem ipsum dolor sit amet, consectetur adipiscing elit. Aenean interdum laoreet augue scelerisque convallis.' });
		row.add(label);

		tableView.data = [ row ];

		view.add(tableView);

		should(view).matchImage('snapshots/tableview_style_inset_grouped.png', {
			threshold: 0.1
		});
	});
});
