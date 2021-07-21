/*
 * Appcelerator Titanium Mobile
 * Copyright (c) 2011-Present by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
/* eslint-env mocha */
/* eslint no-unused-expressions: "off" */
/* eslint promise/always-return: "off" */
/* eslint promise/no-return-wrap: "off" */

var should = require('./utilities/assertions');

describe('ES6 Promise', () => {
	it('follows execution order #1', finished => {
		let result = '';

		new Promise((resolve, reject) => {
			setTimeout(() => {
				result += '1';
				reject();
				result += '2';
			}, 10);
		})
			.then(() => result += '3')
			.catch(() => result += '4')
			.catch(err => finished(err));

		setTimeout(() => {
			try {
				should(result).equal('124');
				finished();
			} catch (e) {
				finished(e);
			}
		}, 50);
	});

	it('follows execution order #2', finished => {
		let result = '';

		Promise.resolve()
			.then(() => result += '1')
			.catch(() => result += '2')
			.catch(err => finished(err));

		result += '3';

		setTimeout(() => {
			try {
				should(result).equal('31');
				finished();
			} catch (e) {
				finished(e);
			}
		}, 50);
	});

	it('follows execution order #3', finished => {
		let result = '';

		Promise.reject()
			.then(() => result += '1')
			.catch(() => result += '2')
			.catch(err => finished(err));

		result += '3';

		setTimeout(() => {
			try {
				should(result).equal('32');
				finished();
			} catch (e) {
				finished(e);
			}
		}, 50);
	});

	it('follows execution order #4', finished => {
		let result = '';

		const P1 = Promise.resolve();
		P1
			.then(() => {
				result += '1';
				return Promise.resolve();
			})
			.then(() => result += '2')
			.catch(err => finished(err));

		result += '3';

		setTimeout(() => {
			try {
				should(result).equal('312');
				finished();
			} catch (e) {
				finished(e);
			}
		}, 50);
	});

	it('follows execution order #5', finished => {
		let result = '';

		Promise.resolve()
			.then(() => {
				result += '1';
				return Promise.resolve();
			})
			.then(() => result += '2')
			.catch(err => finished(err));

		result += '3';

		setTimeout(() => {
			try {
				should(result).equal('312');
				finished();
			} catch (e) {
				finished(e);
			}
		}, 50);
	});

	it('follows execution order #6', finished => {
		let result = '';
		new Promise(function (resolve, reject) {
			setTimeout(function () {
				result += '1';
				reject();
				result += '2';
			}, 10);
		})
			.then(function () {
				result += '3';
			})
			.catch(function () {
				result += '4';
			});

		setTimeout(() => {
			try {
				should(result).equal('124');
				finished();
			} catch (e) {
				finished(e);
			}
		}, 50);
	});

	it('does not break execution order because of "console.log" #1.1', finished => {
		let result = '';

		new Promise((resolve, reject) => {
			setTimeout(() => {
				console.log(1);
				result += '1';
				reject();
				console.log(2);
				result += '2';
			}, 10);
		})
			.then(() => {
				console.log(3);
				result += '3';
			})
			.catch(() => {
				console.log(4);
				result += '4';
			})
			.catch(err => finished(err));

		setTimeout(() => {
			try {
				should(result).equal('124');
				finished();
			} catch (e) {
				finished(e);
			}
		}, 50);
	});

	it('does not break execution order because of "console.log" #2.1', finished => {
		let result = '';

		Promise.resolve()
			.then(() => {
				console.log(1);
				result += '1';
			})
			.catch(() => {
				console.log(2);
				result += '2';
			})
			.catch(err => finished(err));

		console.log(3);
		result += '3';

		setTimeout(() => {
			try {
				should(result).equal('31');
				finished();
			} catch (e) {
				finished(e);
			}
		}, 50);
	});

	it('does not break execution order because of "console.log" #3.1', finished => {
		let result = '';

		Promise.reject()
			.then(() => {
				console.log(1);
				result += '1';
			})
			.catch(() => {
				console.log(2);
				result += '2';
			})
			.catch(err => finished(err));

		console.log(3);
		result += '3';

		setTimeout(() => {
			try {
				should(result).equal('32');
				finished();
			} catch (e) {
				finished(e);
			}
		}, 50);
	});

	it('does not break execution order because of "console.log" #4.1', finished => {
		let result = '';

		const P1 = Promise.resolve();
		P1
			.then(() => {
				console.log(1);
				result += '1';
				return Promise.resolve();
			})
			.then(() => {
				console.log(2);
				result += '2';
			})
			.catch(err => finished(err));

		console.log(3);
		result += '3';

		setTimeout(() => {
			try {
				should(result).equal('312');
				finished();
			} catch (e) {
				finished(e);
			}
		}, 50);
	});

	it('does not break execution order because of "console.log" #5.1', finished => {
		let result = '';

		Promise.resolve()
			.then(() => {
				console.log(1);
				result += '1';
				return Promise.resolve();
			})
			.then(() => {
				console.log(2);
				result += '2';
			})
			.catch(err => finished(err));

		console.log(3);
		result += '3';

		setTimeout(() => {
			try {
				should(result).equal('312');
				finished();
			} catch (e) {
				finished(e);
			}
		}, 50);
	});

	it('does not break execution order because of "console.log" #6.1', finished => {
		let result = '';
		new Promise(function (resolve, reject) {
			setTimeout(function () {
				console.log(1);
				result += '1';
				reject();
				console.log(2);
				result += '2';
			}, 10);
		})
			.then(function () {
				console.log(3);
				result += '3';
			})
			.catch(function () {
				console.log(4);
				result += '4';
			});

		setTimeout(() => {
			try {
				should(result).equal('124');
				finished();
			} catch (e) {
				finished(e);
			}
		}, 50);
	});
});
